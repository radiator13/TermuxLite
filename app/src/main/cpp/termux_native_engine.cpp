#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_termux_lite_NativeBridge_nativeIsAvailable(
    JNIEnv* /* env */,
    jclass /* clazz */
) {
    return JNI_TRUE;
}
