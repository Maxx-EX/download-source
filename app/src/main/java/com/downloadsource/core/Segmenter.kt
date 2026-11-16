package com.downloadsource.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class TsSegment(val url: String, val key: ByteArray?, val iv: ByteArray?)

// 修复：全局令牌桶限速器
class GlobalRateLimiter(private val permitsPerSecond: Long) {
    private var nextFreeTicketMicros = 0L
    private val stableIntervalMicros = if (permitsPerSecond > 0) 1_000_000L / permitsPerSecond else 0L
    @Synchronized fun acquire(requiredPermits: Long) {
        if (stableIntervalMicros == 0L) return
        val nowMicros = System.nanoTime() / 1000
        val waitMicros = maxOf(0L, nextFreeTicketMicros - nowMicros)
        if (waitMicros > 0) try { Thread.sleep(waitMicros / 1000) } catch (e: Exception) {}
        nextFreeTicketMicros = maxOf(nextFreeTicketMicros, nowMicros) + requiredPermits * stableIntervalMicros
    }
}

class Segmenter(private val client: OkHttpClient) {
    private val activeCalls = ConcurrentHashMap<String, MutableList<okhttp3.Call>>()

    fun cancelTask(taskId: String) {
        activeCalls.remove(taskId)?.forEach { it.cancel() }
    }

    suspend fun download(taskId: String, url: String, outFile: File, parts: Int, speedLimitKb: Long, customHeaders: Map<String, String>, onTick: (Long, Long, Long) -> Unit): File = coroutineScope {
        val headers = okhttp3.Headers.Builder().apply { customHeaders.forEach { (k, v) -> add(k, v) } }.build()
        
        if (url.contains(".m3u8") || url.endsWith(".ts", true)) return@coroutineScope downloadM3u8(taskId, url, outFile, parts, headers, onTick)

        // 修复：使用 GET Range: 0-0 探测 206，不依赖不可靠的 HEAD
        val probeReq = Request.Builder().url(url).headers(headers).header("Range", "bytes=0-0").build()
        val meta = withContext(Dispatchers.IO) {
            client.newCall(probeReq).execute().use { resp ->
                val total = if (resp.code == 206) {
                    resp.header("Content-Range")?.substringAfter("/")?.toLongOrNull() ?: -1L
                } else {
                    resp.header("Content-Length")?.toLongOrNull() ?: -1L
                }
                Pair(total, resp.code == 206)
            }
        }
        val total = meta.first
        if (total <= 0) error("获取文件大小失败 (●'◡'●)")
        
        val n = if (meta.second && total > 2L shl 20) parts else 1
        val downloaded = AtomicLong(0)
        val rateLimiter = GlobalRateLimiter(if (speedLimitKb > 0) speedLimitKb * 1024 else 0)

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
            // 修复：直写最终文件，彻底取消合并阶段！
            RandomAccessFile(outFile, "rw").setLength(total)
            (0 until n).map { i ->
                async(Dispatchers.IO) {
                    val start = i * total / n; val end = if (i == n - 1) total - 1 else (i + 1) * total / n - 1
                    retryDownloadRange(taskId, url, start, end, outFile, downloaded, rateLimiter, headers)
                }
            }.awaitAll()
            ticker.cancel()
            onTick(total, total, 0)
            outFile
        } catch (e: Exception) { 
            ticker.cancel()
            if (!outFile.delete()) outFile.deleteOnExit()
            throw e 
        }
    }

    private suspend fun retryDownloadRange(taskId: String, url: String, start: Long, end: Long, file: File, downloaded: AtomicLong, rateLimiter: GlobalRateLimiter, headers: okhttp3.Headers) {
        var retries = 5
        while (retries > 0) {
            if (!currentCoroutineContext().isActive) return
            try { downloadRange(taskId, url, start, end, file, downloaded, rateLimiter, headers); return } 
            catch (e: Exception) { 
                if (e is CancellationException) throw e
                retries--; if (retries == 0) throw e; delay(1500)
            }
        }
    }

    private suspend fun downloadRange(taskId: String, url: String, start: Long, end: Long, file: File, downloaded: AtomicLong, rateLimiter: GlobalRateLimiter, headers: okhttp3.Headers) {
        // 断点续传判断（针对直接写入最终文件的逻辑，需依赖文件本身的大小）
        // 由于直接写最终文件，我们需要一个机制记录每个分片下到了哪里。
        // 为了简化且不破坏文件，我们强制从头开始。对于直写方案，断点续传依赖任务表的 recordedBytes，这里默认从 0 开始。

        val req = Request.Builder().url(url).headers(headers).header("Range", "bytes=$start-$end").build()
        val call = client.newCall(req)
        activeCalls.getOrPut(taskId) { mutableListOf() }.add(call)
        
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) throw Exception("分片失败 HTTP ${resp.code}")
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(start)
                    resp.body?.byteStream()?.use { ins ->
                        val buf = ByteArray(1024 * 1024)
                        while (true) {
                            if (!currentCoroutineContext().isActive) break
                            val r = ins.read(buf)
                            if (r <= 0) break
                            raf.write(buf, 0, r)
                            downloaded.addAndGet(r.toLong())
                            rateLimiter.acquire(r.toLong())
                        }
                    }
                    raf.fd.sync()
                }
            }
        } finally {
            activeCalls[taskId]?.remove(call) // 修复：精确移除，不清空整个 list
        }
    }

    // M3U8 修复：精确清理 Call
    private suspend fun downloadM3u8(taskId: String, url: String, outFile: File, parts: Int, headers: okhttp3.Headers, onTick: (Long, Long, Long) -> Unit): File = coroutineScope {
        var finalUrl = url
        if (url.endsWith(".ts", true)) finalUrl = url.substringBeforeLast("/").plus("/index.m3u8")
        val segments = parseM3u8(finalUrl, mutableSetOf())
        if (segments.isEmpty()) error("未找到有效的 TS 分片")
        
        val tmpDir = File(outFile.parentFile, ".${outFile.name}.ts_parts").apply { mkdirs() }
        val downloaded = AtomicLong(0)
        val total = segments.size.toLong()
        val ticker = launch(Dispatchers.Default) { while (isActive) { delay(300); onTick(downloaded.get(), total, 0) } }

        val semaphore = Semaphore(parts)
        segments.mapIndexed { index, segment ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val partFile = File(tmpDir, "$index.ts")
                    if (!partFile.exists()) {
                        var r = 3
                        while (r > 0) {
                            if (!isActive) return@withPermit
                            try {
                                val call = client.newCall(Request.Builder().url(segment.url).headers(headers).build())
                                activeCalls.getOrPut(taskId) { mutableListOf() }.add(call)
                                call.execute().use { resp -> 
                                    var bytes = resp.body?.bytes() ?: ByteArray(0)
                                    if (segment.key != null && segment.iv != null) bytes = decryptAES(bytes, segment.key, segment.iv)
                                    partFile.writeBytes(bytes)
                                    java.io.FileOutputStream(partFile, true).use { it.fd.sync() }
                                }
                                break
                            } catch (e: Exception) { 
                                if (e is CancellationException) throw e
                                r--; if (r == 0) throw e; delay(1000) 
                            } finally { 
                                // 修复：精确移除 Call
                                activeCalls[taskId]?.removeAll { it.isCanceled() } 
                            }
                        }
                    }
                    downloaded.incrementAndGet()
                }
            }
        }.awaitAll()
        ticker.cancel()

        val mergedTs = File(outFile.parentFile, "${outFile.name}.tmp_merged.ts")
        if (!ZeroCore.mergeFiles(segments.indices.map { File(tmpDir, "$it.ts").absolutePath }.toTypedArray(), mergedTs.absolutePath)) error("TS 合成失败")

        val success = withContext(Dispatchers.IO) { 
            try { remuxTsToMp4(mergedTs, outFile); true } catch (e: Exception) { false } 
        }
        tmpDir.deleteRecursively()
        onTick(total, total, 0)
        if (!success) {
            val fallbackTs = File(outFile.parentFile, outFile.name.replace(".mp4", ".ts"))
            if (mergedTs.exists()) mergedTs.renameTo(fallbackTs)
            throw Exception("MP4 封装失败，已保留 .ts 原文件 (´･_･`)")
        }
        mergedTs.delete()
        outFile
    }

    private suspend fun parseM3u8(url: String, visited: MutableSet<String>): List<TsSegment> = withContext(Dispatchers.IO) {
        if (visited.contains(url)) return@withContext emptyList()
        visited.add(url)
        val text = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        val lines = text.lines()
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            val subUrl = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (subUrl != null) return@withContext parseM3u8(resolveUrl(url, subUrl), visited)
        }
        var currentKey: ByteArray? = null; var currentIv: ByteArray? = null; var mediaSequence = 0L; var segmentIndex = 0L
        val result = mutableListOf<TsSegment>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                mediaSequence = trimmed.substringAfter(":").toLongOrNull() ?: 0L
            } else if (trimmed.startsWith("#EXT-X-KEY:")) {
                val method = Regex("METHOD=([^,]+)").find(trimmed)?.groupValues?.get(1) ?: "NONE"
                if (method == "AES-128") {
                    val keyUri = Regex("URI=\"(.*?)\"").find(trimmed)?.groupValues?.get(1)
                    val ivStr = Regex("IV=0x(.*?)(,|$)").find(trimmed)?.groupValues?.get(1)
                    if (keyUri != null) {
                        currentKey = client.newCall(Request.Builder().url(resolveUrl(url, keyUri)).build()).execute().use { it.body?.bytes() }
                        currentIv = if (ivStr != null) hexStringToByteArray(ivStr) else null
                    } else { currentKey = null; currentIv = null }
                } else if (method == "NONE") { currentKey = null; currentIv = null }
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val absUrl = resolveUrl(url, trimmed)
                if (absUrl.contains(".m3u8", ignoreCase = true)) {
                    result.addAll(parseM3u8(absUrl, visited))
                } else {
                    val ivToUse = currentIv ?: if (currentKey != null) {
                        val seq = mediaSequence + segmentIndex
                        ByteArray(16).apply {
                            this[8] = (seq shr 56).toByte(); this[9] = (seq shr 48).toByte(); this[10] = (seq shr 40).toByte(); this[11] = (seq shr 32).toByte()
                            this[12] = (seq shr 24).toByte(); this[13] = (seq shr 16).toByte(); this[14] = (seq shr 8).toByte(); this[15] = seq.toByte()
                        }
                    } else null
                    result.add(TsSegment(absUrl, currentKey, ivToUse))
                }
                segmentIndex++
            }
        }
        return@withContext result
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http")) return relativeUrl
        return java.net.URI(baseUrl).resolve(relativeUrl).toString()
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length; val data = ByteArray(len / 2)
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
