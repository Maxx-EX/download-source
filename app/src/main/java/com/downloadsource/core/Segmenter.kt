package com.downloadsource.core

import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

class Segmenter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
) {
    data class Meta(val total: Long, val acceptsRange: Boolean)

    suspend fun probe(url: String): Meta = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).head().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            val ranges = resp.header("Accept-Ranges")?.contains("bytes", true) == true
            Meta(len, ranges)
        }
    }

    suspend fun download(
        url: String,
        targetDir: File,
        fileName: String,
        parts: Int = 8,
        onTick: (Long, Long, Long) -> Unit,
    ): File = coroutineScope {
        val meta = probe(url)
        val total = meta.total
        if (total <= 0) error("无法获取文件大小")

        val tmpDir = File(targetDir, ".${fileName}.parts").apply { mkdirs() }
        val n = if (meta.acceptsRange && total > 4L shl 20) parts else 1

        val downloaded = AtomicLong(0)
        var lastTick = System.currentTimeMillis()
        var lastBytes = 0L

        val ticker = launch(Dispatchers.Default) {
            while (isActive) {
                delay(300)
                val now = System.currentTimeMillis()
                val cur = downloaded.get()
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
                    val partFile = File(tmpDir, "$fileName.part$i")
                    downloadRange(url, start, end, partFile) { delta -> downloaded.addAndGet(delta) }
                }
            }.awaitAll()

            ticker.cancel()
            val partPaths = (0 until n).map { File(tmpDir, "$fileName.part$it").absolutePath }.toTypedArray()
            val out = File(targetDir, fileName)
            val ok = ZeroCore.mergeFiles(partPaths, out.absolutePath)
            if (!ok) error("合并文件失败")
            tmpDir.deleteRecursively()
            onTick(total, total, 0)
            out
        } catch (e: Exception) {
            ticker.cancel()
            throw e
        }
    }

    private fun downloadRange(url: String, start: Long, end: Long, file: File, onDelta: (Long) -> Unit) {
        val existing = if (file.exists()) file.length() else 0L
        val realStart = start + existing
        if (realStart > end) { onDelta(existing); return }

        val req = Request.Builder().url(url).header("Range", "bytes=$realStart-$end").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) error("下载失败 HTTP ${resp.code}")
            val body = resp.body ?: error("响应为空")
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(existing)
                body.byteStream().use { ins ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val r = ins.read(buf)
                        if (r <= 0) break
                        raf.write(buf, 0, r)
                        onDelta(r.toLong())
                    }
                }
            }
        }
    }
}
