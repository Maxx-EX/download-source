#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <zlib.h>
#include <vector>

#define TAG "ZeroCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// 预分配磁盘空间，大幅提升多线程写入速度
extern "C" JNIEXPORT void JNICALL
Java_com_downloadsource_core_ZeroCore_fallocate(JNIEnv* env, jobject, jstring path, jlong size) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    int fd = open(p, O_WRONLY | O_CREAT, 0644);
    env->ReleaseStringUTFChars(path, p);
    if (fd >= 0) {
        posix_fallocate(fd, 0, (off_t)size);
        close(fd);
    }
}

// 零拷贝合并（用于多线程分片、M3U8合并）
extern "C" JNIEXPORT jboolean JNICALL
Java_com_downloadsource_core_ZeroCore_mergeFiles(
        JNIEnv* env, jobject, jobjectArray paths, jstring output) {
    const char* outPath = env->GetStringUTFChars(output, nullptr);
    int outFd = open(outPath, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    env->ReleaseStringUTFChars(output, outPath);
    if (outFd < 0) return JNI_FALSE;

    constexpr size_t BUF_SIZE = 512 * 1024; // 512KB 大缓冲
    std::vector<char> buf(BUF_SIZE);
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
        while ((r = read(inFd, buf.data(), BUF_SIZE)) > 0) {
            write(outFd, buf.data(), r);
        }
        close(inFd);
    }
    close(outFd);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// 计算 CRC32
extern "C" JNIEXPORT jlong JNICALL
Java_com_downloadsource_core_ZeroCore_crc32(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    int fd = open(p, O_RDONLY);
    env->ReleaseStringUTFChars(path, p);
    if (fd < 0) return -1;
    uLong crc = crc32(0L, Z_NULL, 0);
    char buf[256 * 1024];
    ssize_t r;
    while ((r = read(fd, buf, sizeof(buf))) > 0) {
        crc = crc32(crc, (const Bytef*) buf, (uInt) r);
    }
    close(fd);
    return (jlong) crc;
}
