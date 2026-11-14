package com.downloadsource.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Segmenter(private val client: OkHttpClient) {
    // 修复：支持同一个任务多线程的多个 Call 取消
    private val activeCalls = ConcurrentHashMap<String, MutableList<Call>>()
    
    // 简单的令牌桶限速器
    private class RateLimiter(private val permitsPerSecond: Long) {
        private var nextFreeTicketMicros = 0L
        private val stableIntervalMicros = if (permitsPerSecond > 0) 1_000_000L / permitsPerSecond else 0L

        @Synchronized
        fun acquire(requiredPermits: Long) {
            if (stableIntervalMicros == 0L) return
            val nowMicros = System.nanoTime() / 1000
            val waitMicros = maxOf(0L, nextFreeTicketMicros - nowMicros)
            if (waitMicros > 0) {
                try { Thread.sleep(waitMicros / 1000) } catch (e: InterruptedException) {}
            }
            nextFreeTicketMicros = maxOf(nextFreeTicketMicros, nowMicros) + requiredPermits * stableIntervalMicros
        }
    }

    fun cancelTask(taskId: String) {
        activeCalls.remove(taskId)?.forEach { it.cancel() }
    }

    suspend fun download(
        taskId: String, url: String, targetDir: File, fileName: String,
        parts: Int, speedLimitKb: Long, customHeaders: Map<String, String>,
        onTick: (Long, Long, Long) -> Unit
    ): File = coroutineScope {
        val headers = Headers.Builder().apply { customHeaders.forEach { (k, v) -> add(k, v) } }.build()
        
        // 修复：仅通过 m3u8 后缀判定，TS直链不走 HLS 逻辑
        if (url.contains(".m3u8")) {
            return@coroutineScope downloadM3u8(taskId, url, targetDir, fileName, parts, headers, onTick)
        }

        // 修复：HEAD 请求获取大小，失败则回退 GET Range:0-0
        var total = -1L
        var supportsRange = false
        try {
            val headReq = Request.Builder().url(url).headers(headers).head().build()
            val call = client.newCall(headReq)
            addCall(taskId, call)
            call.execute().use { resp ->
                if (resp.isSuccessful) {
                    total = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    supportsRange = resp.header("Accept-Ranges")?.contains("bytes", true) == true
                }
            }
        } catch (e: Exception) {
            // HEAD 失败，尝试 GET Range:0-0
            try {
                val getReq = Request.Builder().url(url).headers(headers).header("Range", "bytes=0-0").build()
                val call = client.newCall(getReq)
                addCall(taskId, call)
                call.execute().use { resp ->
                    if (resp.code == 206) {
                        val range = resp.header("Content-Range") // bytes 0-0/123456
                        total = range?.substringAfter("/")?.toLongOrNull() ?: -1L
                        supportsRange = true
                    } else if (resp.isSuccessful) {
                        total = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                }
            } catch (_: Exception) {}
        } finally {
            removeCalls(taskId)
        }

        if (total <= 0) {
            // 回退：无大小流式下载，单线程
            return@coroutineScope downloadStream(taskId, url, targetDir, fileName, headers, onTick)
        }
        
        var n = if (supportsRange && total > 4L shl 20) parts.coerceAtMost(16) else 1
        if (total < 2L shl 20) n = 1

        val tmpDir = File(targetDir, ".${fileName}.parts").apply { mkdirs() }
        val downloaded = AtomicLong(0)
        val rateLimiter = RateLimiter(if (speedLimitKb > 0) speedLimitKb * 1024 else 0)

        var lastTick = System.currentTimeMillis(); var lastBytes = 0L
        val ticker = launch(Dispatchers.Default) {
            while (isActive) {
                delay(300)
                val now = System.currentTimeMillis(); val cur = downloaded.get()
                val speed = if (now > lastTick) (cur - lastBytes) * 1000 / (now - lastTick) else 0
                lastTick = now; lastBytes = cur
                onTick(cur, total, speed)
            }
        }

        try {
            (0 until n).map { i ->
                async(Dispatchers.IO) {
                    val start = i * total / n
                    val end = if (i == n - 1) total - 1 else (i + 1) * total / n - 1
                    retryDownloadRange(taskId, url, start, end, File(tmpDir, "$fileName.part$i"), downloaded, rateLimiter, headers)
                }
            }.awaitAll()
            ticker.cancel()
            
            var actualSize = 0L
            for (i in 0 until n) actualSize += File(tmpDir, "$fileName.part$i").length()
            if (actualSize != total) throw IOException("下载大小不匹配，可能源文件已改变")

            val partPaths = (0 until n).map { File(tmpDir, "$fileName.part$it").absolutePath }.toTypedArray()
            val out = File(targetDir, fileName)
            
            // 取消预分配，直接合并，防止双倍IO
            if (!ZeroCore.mergeFiles(partPaths, out.absolutePath)) throw IOException("合并文件失败")
            
            tmpDir.deleteRecursively()
            onTick(total, total, 0)
            out
        } catch (e: Exception) { 
            ticker.cancel(); 
            throw e 
        }
    }

    private fun addCall(taskId: String, call: Call) {
        activeCalls.getOrPut(taskId) { mutableListOf() }.add(call)
    }

    private fun removeCalls(taskId: String) {
        activeCalls.remove(taskId)
    }

    private suspend fun retryDownloadRange(
        taskId: String, url: String, start: Long, end: Long, 
        file: File, downloaded: AtomicLong, rateLimiter: RateLimiter, headers: Headers
    ) {
        var retries = 5
        var baseDelay = 1000L
        while (retries > 0) {
            if (!currentCoroutineContext().isActive) return
            try { 
                downloadRange(taskId, url, start, end, file, downloaded, rateLimiter, headers)
                return 
            } catch (e: Exception) { 
                if (e is CancellationException) throw e
                if (e.message?.contains("416") == true) throw IOException("文件发生变化(416)，终止下载")
                
                retries--; 
                if (retries == 0) throw e; 
                delay(baseDelay)
                baseDelay *= 2 // 指数退避
            }
        }
    }

    private suspend fun downloadRange(
        taskId: String, url: String, start: Long, end: Long, 
        file: File, downloaded: AtomicLong, rateLimiter: RateLimiter, headers: Headers
    ) {
        val existing = if (file.exists()) file.length() else 0L
        val realStart = start + existing
        if (realStart > end) { downloaded.addAndGet(existing); return }
        if (existing > 0 && downloaded.get() == 0L) downloaded.addAndGet(existing)
        
        val req = Request.Builder().url(url).headers(headers).header("Range", "bytes=$realStart-$end").build()
        val call = client.newCall(req)
        addCall(taskId, call)
        
        try {
            call.execute().use { resp ->
                // 修复：强制校验 206
                if (resp.code != 206) throw IOException("Range请求失败，服务器返回 ${resp.code} 而非 206")
                
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(existing)
                    resp.body?.byteStream()?.use { ins ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (!currentCoroutineContext().isActive) break
                            val r = ins.read(buf)
                            if (r <= 0) break
                            raf.write(buf, 0, r)
                            downloaded.addAndGet(r.toLong())
                            
                            // 使用令牌桶限速
                            rateLimiter.acquire(r.toLong())
                        }
                    }
                    raf.fd.sync()
                }
            }
        } finally {
            removeCalls(taskId)
        }
    }

    private suspend fun downloadStream(
        taskId: String, url: String, targetDir: File, fileName: String, headers: Headers, onTick: (Long, Long, Long) -> Unit
    ): File = coroutineScope {
        val out = File(targetDir, fileName)
        val req = Request.Builder().url(url).headers(headers).build()
        val call = client.newCall(req)
        addCall(taskId, call)
        var downloaded = 0L
        
        withContext(Dispatchers.IO) {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val total = resp.body?.contentLength() ?: -1L
                    resp.body?.byteStream()?.use { ins ->
                        FileOutputStream(out).use { fos ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val r = ins.read(buf)
                                if (r <= 0) break
                                fos.write(buf, 0, r)
                                downloaded += r
                                onTick(downloaded, total, 0)
                            }
                            fos.fd.sync()
                        }
                    }
                }
            } finally {
                removeCalls(taskId)
            }
        }
        out
    }

    private suspend fun downloadM3u8(
        taskId: String, url: String, targetDir: File, fileName: String, parts: Int, headers: Headers, onTick: (Long, Long, Long) -> Unit
    ): File = coroutineScope {
        val (tsUrls, aesKey, iv) = extractTsUrlsAndKeys(url, mutableSetOf(), null, null, 0)
        if (tsUrls.isEmpty()) throw IOException("未找到有效的 TS 分片")
        
        val tmpDir = File(targetDir, ".${fileName}.ts_parts").apply { mkdirs() }
        val downloaded = AtomicLong(0)
        val total = tsUrls.size.toLong()
        val ticker = launch(Dispatchers.Default) { while (isActive) { delay(300); onTick(downloaded.get(), total, 0) } }

        val semaphore = Semaphore(parts)
        tsUrls.mapIndexed { index, tsUrl ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val partFile = File(tmpDir, "$index.ts")
                    if (!partFile.exists()) {
                        var r = 3
                        while (r > 0) {
                            if (!isActive) return@withPermit
                            try {
                                val call = client.newCall(Request.Builder().url(tsUrl).headers(headers).build())
                                addCall(taskId, call)
                                call.execute().use { resp -> 
                                    var bytes = resp.body?.bytes() ?: ByteArray(0)
                                    if (aesKey != null) {
                                        // 修复：IV = mediaSequence + index
                                        val useIv = iv ?: ByteArray(16).apply {
                                            val seq = index
                                            this[12] = (seq shr 24).toByte(); this[13] = (seq shr 16).toByte()
                                            this[14] = (seq shr 8).toByte(); this[15] = seq.toByte()
                                        }
                                        bytes = decryptAES(bytes, aesKey, useIv)
                                    }
                                    partFile.writeBytes(bytes)
                                    // 修复：使用 append=true，防止截断
                                    FileOutputStream(partFile, true).use { it.fd.sync() }
                                }
                                break
                            } catch (e: Exception) { 
                                if (e is CancellationException) throw e
                                r--; if (r == 0) throw e; delay(1000) 
                            } finally {
                                removeCalls(taskId)
                            }
                        }
                    }
                    downloaded.incrementAndGet()
                }
            }
        }.awaitAll()
        ticker.cancel()

        val mergedTs = File(targetDir, "$fileName.tmp_merged.ts")
        if (!ZeroCore.mergeFiles(tsUrls.indices.map { File(tmpDir, "$it.ts").absolutePath }.toTypedArray(), mergedTs.absolutePath)) throw IOException("TS 合成失败")

        val outMp4 = File(targetDir, if (fileName.endsWith(".mp4")) fileName else "$fileName.mp4")
        val success = withContext(Dispatchers.IO) { 
            try { remuxTsToMp4(mergedTs, outMp4); true } 
            catch (e: Exception) { false } 
        }
        
        tmpDir.deleteRecursively()
        onTick(total, total, 0)
        
        if (!success) {
            // 修复：封装失败保留 TS，不要提前 delete
            val fallbackTs = File(targetDir, if (fileName.endsWith(".ts")) fileName else "$fileName.ts")
            if (!mergedTs.renameTo(fallbackTs)) {
                mergedTs.delete() // 只有重命名失败才删
                throw IOException("MP4 封装失败且回退保存失败")
            }
            throw IOException("MP4 封装失败，已为您保留 .ts 原文件")
        }
        mergedTs.delete()
        outMp4
    }

    private suspend fun extractTsUrlsAndKeys(url: String, visited: MutableSet<String>, currentKey: ByteArray?, currentIv: ByteArray?, seq: Int): Triple<List<String>, ByteArray?, ByteArray?> = withContext(Dispatchers.IO) {
        if (visited.contains(url)) return@withContext Triple(emptyList(), currentKey, currentIv)
        visited.add(url)
        val text = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        
        var aesKey = currentKey
        var iv = currentIv
        val lines = text.lines()
        val mediaSequence = lines.find { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }?.substringAfter(":")?.toIntOrNull() ?: 0
        
        lines.find { it.startsWith("#EXT-X-KEY:") }?.let { keyLine ->
            if (keyLine.contains("METHOD=AES-128")) {
                val keyUri = Regex("URI=\"(.*?)\"").find(keyLine)?.groupValues?.get(1)
                val ivStr = Regex("IV=0x(.*?)(,|$)").find(keyLine)?.groupValues?.get(1)
                if (keyUri != null) {
                    aesKey = client.newCall(Request.Builder().url(if (keyUri.startsWith("http")) keyUri else url.substringBeforeLast('/') + "/" + keyUri).build()).execute().use { it.body?.bytes() }
                    if (ivStr != null) iv = hexStringToByteArray(ivStr)
                }
            }
        }
        
        val urls = mutableListOf<String>()
        for (line in lines) {
            if (line.isNotBlank() && !line.startsWith("#")) {
                val absUrl = if (line.startsWith("http")) line else url.substringBeforeLast('/') + "/" + line
                if (absUrl.contains(".m3u8")) {
                    val (subUrls, subKey, subIv) = extractTsUrlsAndKeys(absUrl, visited, aesKey, iv, mediaSequence)
                    urls.addAll(subUrls)
                    aesKey = subKey; iv = subIv
                } else {
                    urls.add(absUrl)
                }
            }
        }
        return@withContext Triple(urls, aesKey, iv)
    }

    private fun decryptAES(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            cipher.doFinal(data)
        } catch (e: Exception) { 
            // 修复：解密失败不再静默返回密文
            throw IOException("AES 解密失败，密钥错误或数据损坏", e)
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        return data
    }

    private fun remuxTsToMp4(input: File, output: File) {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(input.absolutePath)
            val trackMap = HashMap<Int, Int>()
            // 动态 Buffer 大小
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024) 
            val info = MediaCodec.BufferInfo()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i); trackMap[i] = muxer.addTrack(format)
                }
            }
            muxer.start()
            while (true) {
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime; info.flags = extractor.sampleFlags; info.offset = 0
                val trackIdx = extractor.sampleTrackIndex
                if (trackMap.containsKey(trackIdx)) muxer.writeSampleData(trackMap[trackIdx]!!, buffer, info)
                extractor.advance()
            }
        } finally { 
            runCatching { muxer.stop() }; runCatching { muxer.release() }; runCatching { extractor.release() } 
        }
    }
}
