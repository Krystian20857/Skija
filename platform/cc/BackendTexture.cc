#include <jni.h>
#include "GrBackendSurface.h"
#include "GrTypes.h"

static void deleteBackendTexture(GrBackendTexture* rt) {
    delete rt;
}

extern "C" JNIEXPORT jlong JNICALL Java_io_github_humbleui_skija_BackendTexture__1nGetFinalizer
        (JNIEnv* env, jclass jclass) {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(&deleteBackendTexture));
}

#ifdef SK_GL
#include "include/gpu/gl/GrGLTypes.h"
extern "C" JNIEXPORT jlong JNICALL Java_io_github_humbleui_skija_BackendTexture__1nMakeGL
  (JNIEnv* env, jclass jclass, jint width, jint height, jboolean mipmapped, jint target, jint id, jint format) {
    GrGLTextureInfo glTextureInfo = GrGLTextureInfo {
        static_cast<GrGLenum>(target),
        static_cast<GrGLuint>(id),
        static_cast<GrGLenum>(format)
    };
    GrBackendTexture* backendTexture = new GrBackendTexture(width, height, static_cast<GrMipMapped>(mipmapped), glTextureInfo);
    return reinterpret_cast<jlong>(backendTexture);
}
#endif


