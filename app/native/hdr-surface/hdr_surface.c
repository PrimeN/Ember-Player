/* Ember Android surface metadata bridge. Distributed under the repository GPL-3.0 license. */
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

JNIEXPORT jint JNICALL Java_app_ember_tv_player_HdrSurface_readSpace(JNIEnv *env, jobject self, jobject surface) {
    (void)self;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) return -1;
    int space = ANativeWindow_getBuffersDataSpace(window);
    ANativeWindow_release(window);
    return space;
}
JNIEXPORT jboolean JNICALL Java_app_ember_tv_player_HdrSurface_writeSpace(JNIEnv *env, jobject self, jobject surface, jint space) {
    (void)self;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) return JNI_FALSE;
    int result = ANativeWindow_setBuffersDataSpace(window, space);
    int actual = ANativeWindow_getBuffersDataSpace(window);
    ANativeWindow_release(window);
    return result == 0 && actual == space ? JNI_TRUE : JNI_FALSE;
}
