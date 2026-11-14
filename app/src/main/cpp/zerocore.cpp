#include <jni.h>
extern "C" JNIEXPORT jstring JNICALL
Java_com_downloadsource_ZeroCore_version(JNIEnv* env, jobject) {
    return env->NewStringUTF("Zero-core 0.1");
}
