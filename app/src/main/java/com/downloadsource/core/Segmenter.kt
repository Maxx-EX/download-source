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
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Segmenter {
    // 引入 HTTP/2、连接复用池，并支持取消
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(15, 10, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .build()

    // 记录所有活跃的请求 Call，用于瞬间中断下载
    private val activeCalls = ConcurrentHashMap<String, Call>()

    fun cancelTask(taskId: String) {
        activeCalls[taskId]?.cancel()
        activeCalls.remove(taskId)
    }

    suspend fun download(
        taskId: String,
        url: String,
        targetDir: File,
        fileName: String,
        parts: Int,
        speedLimitKb: Long,
        customHeaders: Map<String, String>,
        onTick: (Long, Long, Long) -> Unit
    ): File = coroutineScope {
        val headers = Headers.Builder().apply { customHeaders.forEach { (k, v) -> add(k, v) } }.build()
        
        if (url.contains(".m3u8") || url.endsWith(".ts", true)) {
            return@coroutineScope downloadM3u8(taskId, url, targetDir, fileName, parts, headers, onTick)
        }

        // 获取文件信息
        val headReq = Request.Builder().url(url).headers(headers).head().build()
        val call = client.newCall(headReq)
        activeCalls[taskId] = call
        
        val meta = withContext(Dispatchers.IO) {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw when(resp.code) {
                        403 -> Exception("403 拒绝访问，请配置 Referer/UA")
                        404 -> Exception("404 文件不存在")
                        else -> Exception("HTTP ${resp.code}")
                    }
                    Pair(resp.header("Content-Length")?.toLongOrNull() ?: -1L, resp.header("Accept-Ranges")?.contains("bytes", true) == true)
                }
            } finally { activeCalls.remove(taskId) }
        }
        
        val total = meta.first
        if (total <= 0) error("获取文件大小失败")
        
        // 智能线程规划
        var n = if (meta.second && total > 4L shl 20) parts.coerceAtMost(16) else 1
        if (total < 2L shl 20) n = 1 // < 2MB 强制单线程

        val tmpDir = File(targetDir, ".${fileName}.parts").apply { mkdirs() }
        val downloaded = AtomicLong(0)

        // 进度轮询
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
                    retryDownloadRange(taskId, url, start, end, File(tmpDir, "$fileName.part$i"), downloaded, speedLimitKb, headers)
                }
            }.awaitAll()
            ticker.cancel()
            
            // 合并前校验分片大小
            var actualSize = 0L
            for (i in 0 until n) actualSize += File(tmpDir, "$fileName.part$i").length()
            if (actualSize != total) throw Exception("下载大小不匹配，校验失败")

            val partPaths = (0 until n).map { File(tmpDir, "$fileName.part$it").absolutePath }.toTypedArray()
            val out = File(targetDir, fileName)
            ZeroCore.fallocate(out.absolutePath, total)
            if (!ZeroCore.mergeFiles(partPaths, out.absolutePath)) error("合并失败")
            tmpDir.deleteRecursively()
            onTick(total, total, 0)
            out
        } catch (e: Exception) { 
            ticker.cancel(); 
            throw e 
        }
    }

    private suspend fun retryDownloadRange(taskId: String, url: String, start: Long, end: Long, file: File, downloaded: AtomicLong, speedLimitKb: Long, headers: Headers) {
        var retries = 5
        while (retries > 0) {
            if (!currentCoroutineContext().isActive) return // 协程已取消
            try { 
                downloadRange(taskId, url, start, end, file, downloaded, speedLimitKb, headers)
                return 
            } catch (e: Exception) { 
                if (e is CancellationException) throw e
                retries--; 
                if (retries == 0) throw e; 
                delay(1500) // 失败退避
            }
        }
    }

    private fun downloadRange(taskId: String, url: String, start: Long, end: Long, file: File, downloaded: AtomicLong, speedLimitKb: Long, headers: Headers) {
        val existing = if (file.exists()) file.length() else 0L
        val realStart = start + existing
        if (realStart > end) { downloaded.addAndGet(existing); return }
        if (existing > 0 && downloaded.get() == 0L) downloaded.addAndGet(existing)
        
        val req = Request.Builder().url(url).headers(headers).header("Range", "bytes=$realStart-$end").build()
        val call = client.newCall(req)
        activeCalls[taskId] = call
        
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) throw Exception("分片失败 HTTP ${resp.code}")
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(existing)
                    resp.body?.byteStream()?.use { ins ->
                        val buf = ByteArray(if (speedLimitKb > 0) 16 * 1024 else 64 * 1024)
                        var bytesSinceStart = 0L
                        val startTime = System.currentTimeMillis()

                        while (true) {
                            if (!currentCoroutineContext().isActive) break
                            val r = ins.read(buf)
                            if (r <= 0) break
                            raf.write(buf, 0, r)
                            downloaded.addAndGet(r.toLong())
                            bytesSinceStart += r

                            if (speedLimitKb > 0) {
                                val speedLimitBytes = speedLimitKb * 1024
                                val expectedElapsed = (bytesSinceStart * 1000) / speedLimitBytes
                                val actualElapsed = System.currentTimeMillis() - startTime
                                if (expectedElapsed > actualElapsed) Thread.sleep(expectedElapsed - actualElapsed)
                            }
                        }
                    }
                    // 强制刷盘
                    raf.fd.sync()
                }
            }
        } finally {
            if (activeCalls[taskId] == call) activeCalls.remove(taskId)
        }
    }

    private suspend fun downloadM3u8(taskId: String, url: String, targetDir: File, fileName: String, parts: Int, headers: Headers, onTick: (Long, Long, Long) -> Unit): File = coroutineScope {
        var finalUrl = url
        if (url.endsWith(".ts", true)) finalUrl = url.substringBeforeLast("/").plus("/index.m3u8")
        
        val (tsUrls, aesKey, iv) = extractTsUrlsAndKeys(finalUrl, mutableSetOf(), null, null)
        if (tsUrls.isEmpty()) error("未找到有效的 TS 分片")
        
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
                                activeCalls[taskId] = call
                                call.execute().use { resp -> 
                                    var bytes = resp.body?.bytes() ?: ByteArray(0)
                                    // AES 解密
                                    if (aesKey != null) {
                                        val useIv = iv ?: ByteArray(16).apply {
                                            val seq = index
                                            this[12] = (seq shr 24).toByte(); this[13] = (seq shr 16).toByte()
                                            this[14] = (seq shr 8).toByte(); this[15] = seq.toByte()
                                        }
                                        bytes = decryptAES(bytes, aesKey, useIv)
                                    }
                                    partFile.writeBytes(bytes)
                                    // 强制刷盘
                                    java.io.FileOutputStream(partFile).use { it.fd.sync() }
                                }
                                break
                            } catch (e: Exception) { 
                                if (e is CancellationException) throw e
                                r--; if (r == 0) throw e; delay(1000) 
                            } finally {
                                activeCalls.remove(taskId)
                            }
                        }
                    }
                    downloaded.incrementAndGet()
                }
            }
        }.awaitAll()
        ticker.cancel()

        val mergedTs = File(targetDir, "$fileName.tmp_merged.ts")
        if (!ZeroCore.mergeFiles(tsUrls.indices.map { File(tmpDir, "$it.ts").absolutePath }.toTypedArray(), mergedTs.absolutePath)) error("合成失败")

        val outMp4 = File(targetDir, if (fileName.endsWith(".mp4")) fileName else "$fileName.mp4")
        
        // MediaMuxer 兼容性兜底
        val success = withContext(Dispatchers.IO) { 
            try { remuxTsToMp4(mergedTs, outMp4); true } 
            catch (e: Exception) { false } 
        }
        
        tmpDir.deleteRecursively(); mergedTs.delete()
        onTick(total, total, 0)
        
        if (!success) {
            // 如果合成失败，将源文件直接保留为 .ts 给用户
            val fallbackTs = File(targetDir, if (fileName.endsWith(".ts")) fileName else "$fileName.ts")
            if (mergedTs.exists()) mergedTs.renameTo(fallbackTs)
            throw Exception("MP4 封装失败，已保留 .ts 原文件")
        }
        outMp4
    }

    // 支持解析 Master 索引和多码率、AES-128 加密 Key
    private suspend fun extractTsUrlsAndKeys(url: String, visited: MutableSet<String>, currentKey: ByteArray?, currentIv: ByteArray?): Triple<List<String>, ByteArray?, ByteArray?> = withContext(Dispatchers.IO) {
        if (visited.contains(url)) return@withContext Triple(emptyList(), currentKey, currentIv)
        visited.add(url)
        val text = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        
        var aesKey = currentKey
        var iv = currentIv
        val lines = text.lines()
        
        // 解析 Key
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
                    val (subUrls, subKey, subIv) = extractTsUrlsAndKeys(absUrl, visited, aesKey, iv)
                    urls.addAll(subUrls)
                    aesKey = subKey; iv = subIv
                } else {
                    urls.add(absUrl)
                }
            }
        }
        return@withContext Triple(urls, aesKey, iv)
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        return data
    }

    private fun decryptAES(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            cipher.doFinal(data)
        } catch (e: Exception) { data }
    }

    private fun remuxTsToMp4(input: File, output: File) {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(input.absolutePath)
            val trackMap = HashMap<Int, Int>()
            val buffer = ByteBuffer.allocate(1 * 1024 * 1024)
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
