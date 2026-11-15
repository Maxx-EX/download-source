package com.downloadsource.core

import android.content.Context
import android.os.Environment
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

object LocalWebServer {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    var isRunning = false
        private set
    var port = 8080
        private set

    fun start(context: Context): String {
        if (isRunning) return getIpAddress() + ":$port"
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            executor.execute {
                while (isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        handleClient(socket, context)
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
            return getIpAddress() + ":$port"
        } catch (e: Exception) {
            isRunning = false
            return "启动失败: ${e.message}"
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
    }

    private fun handleClient(socket: Socket, context: Context) {
        executor.execute {
            try {
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = PrintWriter(socket.getOutputStream())
                
                val requestLine = input.readLine() ?: return@execute
                if (!requestLine.startsWith("GET")) return@execute
                
                var filePath = requestLine.split(" ")[1]
                if (filePath == "/") filePath = "/index.html"

                if (filePath == "/index.html") {
                    val html = generateHtmlFileList(context)
                    val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n"
                    output.print(headers + html)
                    output.flush()
                } else {
                    val fileName = URLDecoder.decode(filePath.substring(1), "UTF-8")
                    val file = File(Environment.getExternalStorageDirectory(), "DownloadSource/$fileName")
                    if (file.exists() && file.isFile) {
                        val fileInput = FileInputStream(file)
                        val headers = "HTTP/1.1 200 OK\r\nContent-Disposition: attachment; filename=\"$fileName\"\r\nContent-Length: ${file.length()}\r\n\r\n"
                        socket.getOutputStream().write(headers.toByteArray())
                        fileInput.copyTo(socket.getOutputStream())
                        fileInput.close()
                    } else {
                        output.print("HTTP/1.1 404 Not Found\r\n\r\nFile Not Found")
                        output.flush()
                    }
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateHtmlFileList(context: Context): String {
        val dir = File(Environment.getExternalStorageDirectory(), "DownloadSource")
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val listHtml = files.joinToString("") { file -> 
            "<li><a href='/${file.name}'>${file.name}</a> (${file.length() / 1024 / 1024} MB)</li>" 
        }
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset='utf-8'><title>Download Source 互传</title>
            <style>body{font-family:sans-serif;padding:20px;background:#f0f0f0;} a{color:#007bff;text-decoration:none;} li{margin:10px 0;}</style>
            </head>
            <body>
                <h2>Download Source 局域网互传</h2>
                <p>手机端文件列表：</p>
                <ul>$listHtml</ul>
                <hr>
                <h3>上传文件到手机</h3>
                <form method='POST' action='/upload' enctype='multipart/form-data'>
                    <input type='file' name='file'>
                    <input type='submit' value='上传'>
                </form>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getIpAddress(): String {
        try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {}
        return "127.0.0.1"
    }
}
