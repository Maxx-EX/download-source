#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <zlib.h>
#include <string>
#include <vector>

#define TAG "ZeroCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 把若干 .partN 文件按序合并成 output，使用 256KB 缓冲 + posix read/write
extern "C" JNIEXPORT jboolean JNICALL
Java_com_downloadsource_core_ZeroCore_mergeFiles(
        JNIEnv* env, jobject, jobjectArray paths, jstring output) {
    const char* outPath = env->GetStringUTFChars(output, nullptr);
    int outFd = open(outPath, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    env->ReleaseStringUTFChars(output, outPath);
    if (outFd < 0) { LOGE("open output failed"); return JNI_FALSE; }

    constexpr size_t BUF = 256 * 1024;
    std::vector<char> buf(BUF);
    jsize n = env->GetArrayLength(paths);
    bool ok = true;

    for (jsize i = 0; i < n && ok; ++i) {
        auto jp = (jstring) env->GetObjectArrayElement(paths, i);
        const char* p = env->GetStringUTFChars(jp, nullptr);
        int inFd = open(p, O_RDONLY);
        env->ReleaseStringUTFChars(jp, p);
        env->DeleteLocalRef(jp);
        if (inFd < 0) { ok = false; break; }

        ssize_t r;
        while ((r = read(inFd, buf.data(), BUF)) > 0) {
            ssize_t written = 0;
            while (written < r) {
                ssize_t w = write(outFd, buf.data() + written, r - written);
                if (w <= 0) { ok = false; break; }
                written += w;
            }
            if (!ok) break;
        }
        close(inFd);
    }
    close(outFd);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// 计算文件 CRC32
extern "C" JNIEXPORT jlong JNICALL
Java_com_downloadsource_core_ZeroCore_crc32(
        JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    int fd = open(p, O_RDONLY);
    env->ReleaseStringUTFChars(path, p);
    if (fd < 0) return -1;

    constexpr size_t BUF = 256 * 1024;
    std::vector<char> buf(BUF);
    uLong crc = crc32(0L, Z_NULL, 0);
    ssize_t r;
    while ((r = read(fd, buf.data(), BUF)) > 0) {
        crc = crc32(crc, (const Bytef*) buf.data(), (uInt) r);
    }
    close(fd);
    return (jlong) crc;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_downloadsource_core_ZeroCore_version(JNIEnv* env, jobject) {
    return env->NewStringUTF("Zero-core 1.0");
}
