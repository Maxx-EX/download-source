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

// 1. 预分配磁盘空间，大幅减少文件碎片，提速多线程写入
extern "C" JNIEXPORT jboolean JNICALL
Java_com_downloadsource_core_ZeroCore_fallocate(JNIEnv* env, jobject, jstring path, jlong size) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    int fd = open(p, O_WRONLY | O_CREAT, 0644);
    env->ReleaseStringUTFChars(path, p);
    
    if (fd < 0) return JNI_FALSE;
    
    // 检查文件现有大小，如果已经足够大则跳过
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size >= size) {
        close(fd);
        return JNI_TRUE;
    }
    
    int ret = posix_fallocate(fd, 0, (off_t)size);
    close(fd);
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

// 2. 零拷贝分片合并（升级：1MB Buffer + fsync 强制刷盘 + 失败清理）
extern "C" JNIEXPORT jboolean JNICALL
Java_com_downloadsource_core_ZeroCore_mergeFiles(
        JNIEnv* env, jobject, jobjectArray paths, jstring output) {
    const char* outPath = env->GetStringUTFChars(output, nullptr);
    int outFd = open(outPath, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    env->ReleaseStringUTFChars(output, outPath);
    
    if (outFd < 0) {
        LOGE("Failed to open output file");
        return JNI_FALSE;
    }

    constexpr size_t BUF_SIZE = 1024 * 1024; // 提升到 1MB 缓冲区
    std::vector<char> buf(BUF_SIZE);
    jsize n = env->GetArrayLength(paths);
    bool ok = true;

    for (jsize i = 0; i < n && ok; ++i) {
        auto jp = (jstring) env->GetObjectArrayElement(paths, i);
        const char* p = env->GetStringUTFChars(jp, nullptr);
        int inFd = open(p, O_RDONLY);
        env->ReleaseStringUTFChars(jp, p);
        env->DeleteLocalRef(jp);
        
        if (inFd < 0) { 
            LOGE("Failed to open part file: %d", i);
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
            }
            if (!ok) break;
        }
        close(inFd);
    }
    
    if (ok) {
        // 核心升级：强制刷盘，确保断电不丢数据
        fsync(outFd);
    }
    close(outFd);
    
    // 如果合并失败，删除残缺的合并文件
    if (!ok) {
        unlink(outPath);
    }
    
    return ok ? JNI_TRUE : JNI_FALSE;
}

// 3. CRC32 校验，确保文件下载无损
extern "C" JNIEXPORT jlong JNICALL
Java_com_downloadsource_core_ZeroCore_crc32(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    int fd = open(p, O_RDONLY);
    env->ReleaseStringUTFChars(path, p);
    
    if (fd < 0) return -1;
    
    uLong crc = crc32(0L, Z_NULL, 0);
    char buf[512 * 1024];
    ssize_t r;
    
    while ((r = read(fd, buf, sizeof(buf))) > 0) {
        crc = crc32(crc, (const Bytef*) buf, (uInt) r);
    }
    close(fd);
    return (jlong) crc;
}
