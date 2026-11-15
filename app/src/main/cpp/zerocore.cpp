#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <zlib.h>
#include <vector>

#define TAG "ZeroCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_downloadsource_core_ZeroCore_nativeFallocate(JNIEnv* env, jobject, jstring path, jlong size) {
    if (path == nullptr) return JNI_FALSE;
    
    const char* p = env->GetStringUTFChars(path, nullptr);
    int fd = open(p, O_WRONLY | O_CREAT, 0644);
    env->ReleaseStringUTFChars(path, p);

    if (fd < 0) {
        LOGE("Fallocate failed: Cannot open file.");
        return JNI_FALSE;
    }

    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size >= size) {
        close(fd);
        return JNI_TRUE;
    }

    // 调用 POSIX 预分配接口，失败时优雅回退，不导致崩溃
    int ret = posix_fallocate(fd, 0, (off_t)size);
    close(fd);
    
    if (ret != 0) {
        LOGI("posix_fallocate failed with code %d, fallback to standard write.", ret);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_downloadsource_core_ZeroCore_nativeMergeFiles(
        JNIEnv* env, jobject, jobjectArray paths, jstring output) {
    if (paths == nullptr || output == nullptr) return JNI_FALSE;

    const char* outPath = env->GetStringUTFChars(output, nullptr);
    int outFd = open(outPath, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    env->ReleaseStringUTFChars(output, outPath);

    if (outFd < 0) {
        LOGE("Merge failed: Cannot create output file.");
        return JNI_FALSE;
    }

    constexpr size_t BUF_SIZE = 1024 * 1024; // 1MB 大缓冲，提升合并速度
    std::vector<char> buf(BUF_SIZE);
    jsize n = env->GetArrayLength(paths);
    bool ok = true;
    size_t totalCopied = 0;

    for (jsize i = 0; i < n && ok; ++i) {
        auto jp = (jstring) env->GetObjectArrayElement(paths, i);
        if (jp == nullptr) { ok = false; break; }

        const char* p = env->GetStringUTFChars(jp, nullptr);
        int inFd = open(p, O_RDONLY);

        env->ReleaseStringUTFChars(jp, p);
        env->DeleteLocalRef(jp); // 关键：清理局部引用，防止 JNI 表溢出崩溃

        if (inFd < 0) { 
            LOGE("Merge failed: Cannot open part file index %d", i);
            ok = false; 
            break; 
        }

        ssize_t r;
        while ((r = read(inFd, buf.data(), BUF_SIZE)) > 0) {
            ssize_t written = 0;
            while (written < r) {
                ssize_t w = write(outFd, buf.data() + written, r - written);
                if (w <= 0) { ok = false; break; }
                written += w;
                totalCopied += w;
            }
            if (!ok) break;
        }
        close(inFd);
    }

    if (ok) {
        fsync(outFd); // 强制刷盘，确保数据真正写入物理存储
        LOGI("Merge success: %zu bytes copied.", totalCopied);
    }
    close(outFd);

    // 如果合并中途失败，删除残缺的最终文件，避免占用空间
    if (!ok) {
        const char* p = env->GetStringUTFChars(output, nullptr);
        unlink(p);
        env->ReleaseStringUTFChars(output, p);
        LOGE("Merge failed, output file deleted.");
    }

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_downloadsource_core_ZeroCore_nativeCrc32(JNIEnv* env, jobject, jstring path) {
    if (path == nullptr) return -1;

    const char* p = env->GetStringUTFChars(path, nullptr);
    int fd = open(p, O_RDONLY);
    env->ReleaseStringUTFChars(path, p);

    if (fd < 0) return -1; // 明确返回 -1 表示失败

    uLong crc = crc32(0L, Z_NULL, 0);
    char buf[512 * 1024];
    ssize_t r;

    while ((r = read(fd, buf, sizeof(buf))) > 0) {
        crc = crc32(crc, (const Bytef*) buf, (uInt) r);
    }
    close(fd);
    return (jlong) crc;
}
