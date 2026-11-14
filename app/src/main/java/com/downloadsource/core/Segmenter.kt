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
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Segmenter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
) {
    suspend fun download(url: String, targetDir: File, fileName: String, parts: Int, onTick: (Long, Long, Long) -> Unit): File = coroutineScope {
        if (url.contains(".m3u8") || url.contains(".m3u")) {
            return@coroutineScope downloadM3u8(url, targetDir, fileName, parts, onTick)
        }

        val headReq = Request.Builder().url(url).head().build()
        val meta = withContext(Dispatchers.IO) {
            client.newCall(headReq).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                Pair(resp.header("Content-Length")?.toLongOrNull() ?: -1L, resp.header("Accept-Ranges")?.contains("bytes", true) == true)
            }
        }
        val total = meta.first
        if (total <= 0) error("获取文件大小失败")
        
        val n = if (meta.second && total > 4L shl 20) parts else 1
        val tmpDir = File(targetDir, ".${fileName}.parts").apply { mkdirs() }
        val downloaded = AtomicLong(0)

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
                    val start = i * total / n; val end = if (i == n - 1) total - 1 else (i + 1) * total / n - 1
                    val partFile = File(tmpDir, "$fileName.part$i")
                    downloadRange(url, start, end, partFile, downloaded)
                }
            }.awaitAll()
            ticker.cancel()

            val partPaths = (0 until n).map { File(tmpDir, "$fileName.part$it").absolutePath }.toTypedArray()
            val out = File(targetDir, fileName)
            ZeroCore.fallocate(out.absolutePath, total)
            if (!ZeroCore.mergeFiles(partPaths, out.absolutePath)) error("合并失败")
            tmpDir.deleteRecursively()
            onTick(total, total, 0)
            out
        } catch (e: Exception) {
            ticker.cancel(); throw e
        }
    }

    private fun downloadRange(url: String, start: Long, end: Long, file: File, downloaded: AtomicLong) {
        val existing = if (file.exists()) file.length() else 0L
        val realStart = start + existing
        if (realStart > end) return
        val req = Request.Builder().url(url).header("Range", "bytes=$realStart-$end").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) error("分片失败 HTTP ${resp.code}")
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(existing)
                resp.body?.byteStream()?.use { ins ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val r = ins.read(buf)
                        if (r <= 0) break
                        raf.write(buf, 0, r)
                        downloaded.addAndGet(r.toLong())
                    }
                }
            }
        }
    }

    // 智能解析 M3U8 并进行 TS 到 MP4 无损重封装
    private suspend fun downloadM3u8(url: String, targetDir: File, fileName: String, parts: Int, onTick: (Long, Long, Long) -> Unit): File = coroutineScope {
        val m3u8Text = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        }
        val lines = m3u8Text.lines()
        var aesKey: ByteArray? = null
        var iv: ByteArray? = null

        // 解析加密 Key
        lines.find { it.startsWith("#EXT-X-KEY:") }?.let { keyLine ->
            if (keyLine.contains("METHOD=AES-128")) {
                val keyUri = Regex("URI=\"(.*?)\"").find(keyLine)?.groupValues?.get(1)
                val ivStr = Regex("IV=0x(.*?)(,|$)").find(keyLine)?.groupValues?.get(1)
                if (keyUri != null) {
                    aesKey = withContext(Dispatchers.IO) {
                        client.newCall(Request.Builder().url(if (keyUri.startsWith("http")) keyUri else url.substringBeforeLast('/') + "/" + keyUri).build()).execute().use { it.body?.bytes() }
                    }
                    if (ivStr != null) iv = hexStringToByteArray(ivStr)
                }
            }
        }

        val tsUrls = lines.filter { it.isNotBlank() && !it.startsWith("#") }
            .map { if (it.startsWith("http")) it else url.substringBeforeLast('/') + "/" + it }
        
        if (tsUrls.isEmpty()) error("M3U8 解析为空")
        
        val tmpDir = File(targetDir, ".${fileName}.ts_parts").apply { mkdirs() }
        val downloaded = AtomicLong(0)
        val total = tsUrls.size.toLong()
        
        val ticker = launch(Dispatchers.Default) {
            while (isActive) { delay(300); onTick(downloaded.get(), total, 0) }
        }

        val semaphore = Semaphore(parts)
        tsUrls.mapIndexed { index, tsUrl ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val partFile = File(tmpDir, "$index.ts")
                    if (!partFile.exists()) {
                        client.newCall(Request.Builder().url(tsUrl).build()).execute().use { resp ->
                            var bytes = resp.body?.bytes() ?: ByteArray(0)
                            if (aesKey != null) {
                                val useIv = iv ?: ByteArray(16).apply { 
                                    // 如果没有指定IV，默认使用序号作为IV (标准要求)
                                    val seq = index
                                    this[12] = (seq shr 24).toByte(); this[13] = (seq shr 16).toByte()
                                    this[14] = (seq shr 8).toByte(); this[15] = seq.toByte()
                                }
                                bytes = decryptAES(bytes, aesKey!!, useIv)
                            }
                            partFile.writeBytes(bytes)
                        }
                    }
                    downloaded.incrementAndGet()
                }
            }
        }.awaitAll()
        ticker.cancel()

        val mergedTs = File(targetDir, "$fileName.tmp_merged.ts")
        val partPaths = tsUrls.indices.map { File(tmpDir, "$it.ts").absolutePath }.toTypedArray()
        if (!ZeroCore.mergeFiles(partPaths, mergedTs.absolutePath)) error("合并 TS 失败")

        // 无损转换 MP4
        val outMp4 = File(targetDir, if (fileName.endsWith(".mp4")) fileName else "$fileName.mp4")
        withContext(Dispatchers.IO) { remuxTsToMp4(mergedTs, outMp4) }

        tmpDir.deleteRecursively()
        mergedTs.delete()
        onTick(total, total, 0)
        outMp4
    }

    private fun decryptAES(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            cipher.doFinal(data)
        } catch (e: Exception) { data } // 解密失败原样返回
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
            val trackCount = extractor.trackCount
            val trackMap = HashMap<Int, Int>()
            val buffer = ByteBuffer.allocate(1 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                }
            }
            muxer.start()

            var offset = 0L
            while (true) {
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                info.offset = 0
                val trackIdx = extractor.sampleTrackIndex
                if (trackMap.containsKey(trackIdx)) {
                    muxer.writeSampleData(trackMap[trackIdx]!!, buffer, info)
                }
                extractor.advance()
            }
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { extractor.release() }
        }
    }
}
