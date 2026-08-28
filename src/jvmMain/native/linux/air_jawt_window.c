#include <jawt.h>
#include <jawt_md.h>
#include <jni.h>

static void air_throw(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (type != NULL) {
        (*env)->ThrowNew(env, type, message);
    }
}

JNIEXPORT jlong JNICALL
Java_com_getair_video_JawtWindowHandle_nativeGetWindowHandle(
    JNIEnv *env,
    jclass type,
    jobject component
) {
    (void) type;
    JAWT awt;
    awt.version = JAWT_VERSION_1_4;
    if (JAWT_GetAWT(env, &awt) == JNI_FALSE) {
        air_throw(env, "JAWT is unavailable");
        return 0;
    }

    JAWT_DrawingSurface *surface = awt.GetDrawingSurface(env, component);
    if (surface == NULL) {
        air_throw(env, "The video surface is not displayable");
        return 0;
    }

    jlong handle = 0;
    jint lock = surface->Lock(surface);
    if ((lock & JAWT_LOCK_ERROR) != 0) {
        awt.FreeDrawingSurface(surface);
        air_throw(env, "The video surface could not be locked");
        return 0;
    }

    JAWT_DrawingSurfaceInfo *info = surface->GetDrawingSurfaceInfo(surface);
    if (info != NULL) {
        JAWT_X11DrawingSurfaceInfo *platform =
            (JAWT_X11DrawingSurfaceInfo *) info->platformInfo;
        if (platform != NULL) {
            handle = (jlong) platform->drawable;
        }
        surface->FreeDrawingSurfaceInfo(info);
    }
    surface->Unlock(surface);
    awt.FreeDrawingSurface(surface);

    if (handle == 0) {
        air_throw(env, "The video surface has no native X11 window");
    }
    return handle;
}
