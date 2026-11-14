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
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
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
        
        // 修复 1：如果已经下完，补上进度并跳过
        if (realStart > end) {
            downloaded.addAndGet(existing)
            return
        }
        
        // 修复 2：把已下载的大小计入计数器，防止暂停恢复后进度归零
        downloaded.addAndGet(existing)
        
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

    private suspend fun downloadM3u8(url: String, targetDir: File, fileName: String, parts: Int, onTick: (Long, Long, Long) -> Unit): File = coroutineScope {
        val tsUrls = extractTsUrls(url, mutableSetOf())
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
                            partFile.writeBytes(resp.body?.bytes() ?: ByteArray(0))
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

        val outMp4 = File(targetDir, if (fileName.endsWith(".mp4")) fileName else "$fileName.mp4")
        withContext(Dispatchers.IO) { remuxTsToMp4(mergedTs, outMp4) }

        tmpDir.deleteRecursively()
        mergedTs.delete()
        onTick(total, total, 0)
        outMp4
    }

    // 修复 3：递归解析嵌套的 M3U8 子列表，防死循环
    private suspend fun extractTsUrls(url: String, visited: MutableSet<String>): List<String> = withContext(Dispatchers.IO) {
        if (visited.contains(url)) return@withContext emptyList()
        visited.add(url)

        val text = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        val lines = text.lines()
        val urls = mutableListOf<String>()
        
        for (line in lines) {
            if (line.isNotBlank() && !line.startsWith("#")) {
                val absUrl = if (line.startsWith("http")) line else url.substringBeforeLast('/') + "/" + line
                if (absUrl.contains(".m3u8")) {
                    urls.addAll(extractTsUrls(absUrl, visited)) // 递归展开
                } else {
                    urls.add(absUrl)
                }
            }
        }
        return@withContext urls
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
