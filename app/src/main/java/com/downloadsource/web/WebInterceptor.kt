package com.downloadsource.web

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class WebInterceptor {
    data class InterceptedData(val url: String, val fileName: String?, val mimeType: String?)

    // 用 SharedFlow 将 JS 拦截到的数据传给 Compose UI
    private val _intercepted = MutableSharedFlow<InterceptedData>()
    val intercepted: SharedFlow<InterceptedData> = _intercepted

    @JavascriptInterface
    fun onDownloadTriggered(url: String, fileName: String?, mime: String?) {
        // 过滤掉空数据和无效的 javascript
        if (url.isBlank() || url.startsWith("javascript:")) return
        _intercepted.tryEmit(InterceptedData(url, fileName, mime))
    }

    @JavascriptInterface
    fun onBlobData(base64Data: String, fileName: String?, mime: String?) {
        if (base64Data.isBlank()) return
        // 将 Base64 包装成 DataURL 交给后续的下载器处理
        val url = "data:${mime ?: "application/octet-stream"};base64,$base64Data"
        _intercepted.tryEmit(InterceptedData(url, fileName ?: "blob_${System.currentTimeMillis()}", mime))
    }

    // 注入的终极拦截脚本
    val injectionScript = """
        (function() {
            // 1. 拦截 a 标签的点击 (覆盖动态生成的 a 标签)
            var originalClick = HTMLAnchorElement.prototype.click;
            HTMLAnchorElement.prototype.click = function() {
                if (this.href && (this.href.startsWith('blob:') || this.href.startsWith('data:') || this.download)) {
                    if (this.href.startsWith('blob:')) {
                        fetch(this.href).then(r => r.blob()).then(b => {
                            var reader = new FileReader();
                            reader.onload = function() {
                                window.AndroidBridge.onBlobData(reader.result.split(',')[1], this.download, b.type);
                            };
                            reader.readAsDataURL(b);
                        }).catch(e => window.AndroidBridge.onDownloadTriggered(this.href, this.download, ''));
                    } else {
                        window.AndroidBridge.onDownloadTriggered(this.href, this.download, '');
                    }
                    return; 
                }
                originalClick.apply(this, arguments);
            };

            // 2. 拦截 window.open
            var originalOpen = window.open;
            window.open = function(url, name, features) {
                if (url && (url.startsWith('http') || url.startsWith('blob:'))) {
                    window.AndroidBridge.onDownloadTriggered(url, null, null);
                }
                return originalOpen.apply(this, arguments);
            };
            
            // 3. 拦截 fetch 的 blob 请求
            var originalFetch = window.fetch;
            window.fetch = function() {
                return originalFetch.apply(this, arguments).then(response => {
                    var contentType = response.headers.get("content-type");
                    if (contentType && (contentType.includes("application/octet-stream") || contentType.includes("application/vnd"))) {
                        response.clone().blob().then(blob => {
                            var reader = new FileReader();
                            reader.onload = function() {
                                window.AndroidBridge.onBlobData(reader.result.split(',')[1], null, contentType);
                            };
                            reader.readAsDataURL(blob);
                        });
                    }
                    return response;
                });
            };
        })();
    """.trimIndent()
}
