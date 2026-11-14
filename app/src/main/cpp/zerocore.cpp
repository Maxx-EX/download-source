#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <zlib.h>
#include <vector>

#define TAG "ZeroCore"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_downloadsource_core_ZeroCore_nativeFallocate(JNIEnv* env, jobject, jstring path, jlong size) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    int fd = open(p, O_WRONLY | O_CREAT, 0644);
    env->ReleaseStringUTFChars(path, p);
    
    if (fd < 0) return JNI_FALSE;
    
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size >= size) {
        close(fd);
        return JNI_TRUE;
    }
    
    // 修复：部分 FAT32 / 老设备不支持 posix_fallocate，优雅处理失败
    int ret = posix_fallocate(fd, 0, (off_t)size);
    close(fd);
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_downloadsource_core_ZeroCore_nativeMergeFiles(
        JNIEnv* env, jobject, jobjectArray paths, jstring output) {
    const char* outPath = env->GetStringUTFChars(output, nullptr);
    int outFd = open(outPath, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    env->ReleaseStringUTFChars(output, outPath);
    
    if (outFd < 0) return JNI_FALSE;

    constexpr size_t BUF_SIZE = 1024 * 1024; 
    std::vector<char> buf(BUF_SIZE);
    jsize n = env->GetArrayLength(paths);
    bool ok = true;

    for (jsize i = 0; i < n && ok; ++i) {
        auto jp = (jstring) env->GetObjectArrayElement(paths, i);
        const char* p = env->GetStringUTFChars(jp, nullptr);
        int inFd = open(p, O_RDONLY);
        
        env->ReleaseStringUTFChars(jp, p);
        env->DeleteLocalRef(jp); // 修复：清理局部引用，防止 JNI 表溢出崩溃
        
        if (inFd < 0) { ok = false; break; }

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
    
    if (ok) fsync(outFd); // 强制刷盘
    close(outFd);
    
    // 修复：失败清理残缺文件
    if (!ok) {
        unlink(outPath);
    }
    
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_downloadsource_core_ZeroCore_nativeCrc32(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    int fd = open(p, O_RDONLY);
    env->ReleaseStringUTFChars(path, p);
    
    if (fd < 0) return -1; // 明确返回 -1
    
    uLong crc = crc32(0L, Z_NULL, 0);
    char buf[512 * 1024];
    ssize_t r;
    
    while ((r = read(fd, buf, sizeof(buf))) > 0) {
        crc = crc32(crc, (const Bytef*) buf, (uInt) r);
    }
    close(fd);
    return (jlong) crc;
}
