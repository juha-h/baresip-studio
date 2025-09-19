//
// Created by Administrator on 2025/9/19.
//
#include <jni.h>
#include <libyuv.h>
#include <string.h>
#include "common.h"
#include "android_camera2.h"
#include "logger.h"

#define CAMERA "Camera2"
#define CLASS_PATH "com/tutpro/baresip/plus/"
#define CAMERA_CLASS_PATH CLASS_PATH CAMERA


struct
{
    struct
    {
        jclass cls;
        jmethodID m_init;
        jmethodID m_start;
        jmethodID m_stop;
    } cam2;
} jobjs;

struct vidsrc *vid_android_camera2;

static void JNICALL OnGetFrame(JNIEnv *env, jobject obj, jlong user_data, jobject plane0,
        jint rowStride0, jint pixStride0, jobject plane1, jint rowStride1, jint pixStride1,
        jobject plane2, jint rowStride2, jint pixStride2);

static bool jni_init_ids()
{
    JNIEnv *jni_env;
    bool status = true;
    bool with_attach = jni_get_env(&jni_env);

#define GET_CLASS(class_path, class_name, cls)                \
    cls = (*jni_env)->FindClass(jni_env, class_path);         \
    if (cls == NULL || (*jni_env)->ExceptionCheck(jni_env)) { \
        (*jni_env)->ExceptionClear(jni_env);                  \
        status = false;                                       \
        goto on_return;                                       \
    } else {                                                  \
        jclass tmp = cls;                                     \
        cls = (jclass)(*jni_env)->NewGlobalRef(jni_env, tmp); \
        (*jni_env)->DeleteLocalRef(jni_env, tmp);             \
        if (cls == NULL) {                                    \
            status = false;                                   \
            goto on_return;                                   \
        }                                                     \
    }

#define GET_METHOD_ID(cls, class_name, method_name, signature, id)      \
    id = (*jni_env)->GetMethodID(jni_env, cls, method_name, signature); \
    if (id == 0) {                                                      \
        status = false;                                                 \
        goto on_return;                                                 \
    }

#define GET_SMETHOD_ID(cls, class_name, method_name, signature, id)           \
    id = (*jni_env)->GetStaticMethodID(jni_env, cls, method_name, signature); \
    if (id == 0) {                                                            \
        status = false;                                                       \
        goto on_return;                                                       \
    }

    /* Camera2 class info */
    GET_CLASS(CAMERA_CLASS_PATH, CAMERA, jobjs.cam2.cls);
    GET_METHOD_ID(jobjs.cam2.cls, CAMERA, "<init>", "(IIIJ)V", jobjs.cam2.m_init);
    //    GET_METHOD_ID(jobjs.cam2.cls, CAMERA, "<init>","(IIIIIJ)V",jobjs.cam2.m_init);
    GET_METHOD_ID(jobjs.cam2.cls, CAMERA, "startCamera", "(Landroid/view/Surface;I)V",
            jobjs.cam2.m_start);
    GET_METHOD_ID(jobjs.cam2.cls, CAMERA, "stopCamera", "()V", jobjs.cam2.m_stop);

    /* 注册 native PushFrame 回调 */
    {
        JNINativeMethod m[] = {{"PushFrame",
                "(JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)V",
                (void *)&OnGetFrame}};
        if ((*jni_env)->RegisterNatives(jni_env, jobjs.cam2.cls, m, 1)) {
            status = false;
        }
    }

#undef GET_CLASS
#undef GET_METHOD_ID
#undef GET_SMETHOD_ID

on_return:
    jni_detach_env(with_attach);
    return status;
}

static void jni_deinit_ids()
{
    JNIEnv *jni_env;
    bool with_attach = jni_get_env(&jni_env);

    if (jobjs.cam2.cls) {
        (*jni_env)->DeleteGlobalRef(jni_env, jobjs.cam2.cls);
        jobjs.cam2.cls = NULL;
    }
    jni_detach_env(with_attach);
}

void android_camera2_destructor(void *arg)
{
    struct vidsrc_st *st = arg;

    JNIEnv *jni_env;
    bool with_attach = jni_get_env(&jni_env);
    if (st->jcam) {
        /* Call Camera2::Stop() method */
        (*jni_env)->CallVoidMethod(jni_env, st->jcam, jobjs.cam2.m_stop);
    }

    /* Wait for termination of other thread */
    //    if (re_atomic_rlx(&st->run)) {
    //        debug("android_camera2: stopping read thread\n");
    //        re_atomic_rlx_set(&st->run, false);
    //        thrd_join(st->thread, NULL);
    //    }

    if (st->frame) {
        st->frame = mem_deref(st->frame);
    }

    if (st->rotate_buf) {
        free(st->rotate_buf);
        st->rotate_buf = NULL;
        st->rotate_buf_size = 0;
    }

    if (st->buf) {
        free(st->buf);
        st->buf = NULL;
        st->frameSize = 0;
    }

    jni_detach_env(with_attach);

    jni_deinit_ids();
}

static void process_frame(struct vidsrc_st *st)
{
    st->ts += (uint64_t)(VIDEO_TIMEBASE / st->fps);

    st->frameh(st->frame, st->ts, st->arg);
}

int android_camera2_alloc(struct vidsrc_st **stp, const struct vidsrc *vs, struct vidsrc_prm *prm,
        const struct vidsz *size, const char *fmt, const char *dev, vidsrc_frame_h *frameh,
        vidsrc_packet_h *packeth, vidsrc_error_h *errorh, void *arg)
{
    struct vidsrc_st *st;
    unsigned x;
    int err;

    (void)fmt;
    (void)dev;
    (void)packeth;
    (void)errorh;
    (void)vs;

    if (!stp || !prm || !size || !frameh)
        return EINVAL;

    st = mem_zalloc(sizeof(*st), android_camera2_destructor);
    if (!st)
        return ENOMEM;

    st->fps = prm->fps;
    st->frameh = frameh;
    st->arg = arg;
    st->size = size;
    st->fmt = prm->fmt;
    st->rotate = 270; // Here you can read the configuration items
    st->frameSize = size->w * size->h * 3 / 2;
    st->buf = malloc(st->frameSize);

    if (!jni_init_ids()) {
        return ENOMEM;
    }

    JNIEnv *jni_env;
    bool with_attach = jni_get_env(&jni_env);
    jobject jcam =
            (*jni_env)->NewObject(jni_env, jobjs.cam2.cls, jobjs.cam2.m_init, st->size->w, /* w */
                    st->size->h,                                                           /* h */
                    (jint)st->fps,                                                         /* fps */
                    (jlong)(intptr_t)st /* user data */
            );
    if (jcam == NULL) {
        err = ENOMEM;
        goto out;
    }
    st->jcam = (jobject)(*jni_env)->NewGlobalRef(jni_env, jcam);
    (*jni_env)->DeleteLocalRef(jni_env, jcam);
    if (st->jcam == NULL) {
        err = ENOMEM;
        goto out;
    }
    (*jni_env)->CallVoidMethod(jni_env, st->jcam, jobjs.cam2.m_start, NULL, /* Preview */
            0 /* Camera */);

    err = vidframe_alloc(&st->frame, prm->fmt, size);
    if (err)
        goto out;

    /* Pattern of three vertical bars in RGB */
    for (x = 0; x < size->w; x++) {
        uint8_t r = 0, g = 0, b = 0;

        vidframe_draw_vline(st->frame, x, 0, size->h, r, g, b);
    }

out:
    jni_detach_env(with_attach);
    LOGD("android_camera2_alloc err:%d",err);
    if (err)
        mem_deref(st);
    else
        *stp = st;

    return err;
}

static void JNICALL OnGetFrame(JNIEnv *env, jobject obj, jlong user_data, jobject plane0,
        jint rowStride0, jint pixStride0, jobject plane1, jint rowStride1, jint pixStride1,
        jobject plane2, jint rowStride2, jint pixStride2)
{
    struct vidsrc_st *st = (struct vidsrc_st *)(intptr_t)user_data;

    int width = st->size->w;
    int height = st->size->h;
    struct vidsz size = {
            .w = width,
            .h = height,
    };
    // Whether to write frame data
    bool isWrite = true;

    uint8_t *srcY = (uint8_t *)(*env)->GetDirectBufferAddress(env, plane0);
    uint8_t *srcU = (uint8_t *)(*env)->GetDirectBufferAddress(env, plane1);
    uint8_t *srcV = (uint8_t *)(*env)->GetDirectBufferAddress(env, plane2);

    if (st->fmt == VID_FMT_YUV420P) {
        int dst_w = height; // After rotation, the width and height are reversed
        int dst_h = width;

        // Make sure the buffer is sufficient
        int buf_size = dst_w * dst_h * 3 / 2;
        if (st->rotate_buf_size < buf_size) {
            free(st->rotate_buf);
            st->rotate_buf = malloc(buf_size);
            st->rotate_buf_size = buf_size;
        }

        uint8_t *dst_y = st->rotate_buf;
        uint8_t *dst_u = dst_y + dst_w * dst_h;
        uint8_t *dst_v = dst_u + (dst_w / 2) * (dst_h / 2);

        // 一步完成: YUV_420_888 → I420 (rotate 270)
        Android420ToI420Rotate((const uint8_t *)srcY, rowStride0, (const uint8_t *)srcU, rowStride1,
                (const uint8_t *)srcV, rowStride2,
                pixStride1, // UV pixel stride (usually 2, but must use the actual value)
                dst_y, dst_w, dst_u, dst_w / 2, dst_v, dst_w / 2, width, height, st->rotate);
        memcpy(st->buf, st->rotate_buf, st->rotate_buf_size);
        size.w = dst_w;
        size.h = dst_h;
    } else if (st->fmt == VID_FMT_NV12) {
        int dst_w = height; // After rotation, the width and height are reversed
        int dst_h = width;

        int i420_size = dst_w * dst_h * 3 / 2;

        if (!st->rotate_buf || st->rotate_buf_size < i420_size) {
            free(st->rotate_buf);
            st->rotate_buf = malloc(i420_size);
            st->rotate_buf_size = i420_size;
        }

        uint8_t *i420_y = st->rotate_buf;
        uint8_t *i420_u = i420_y + dst_w * dst_h;
        uint8_t *i420_v = i420_u + (dst_w / 2) * (dst_h / 2);

        uint8_t *dst_y = st->buf;
        uint8_t *dst_uv = dst_y + dst_w * dst_h;

        // Step1: YUV_420_888 → I420 (rotate 270)
        Android420ToI420Rotate((const uint8_t *)plane0, rowStride0, (const uint8_t *)plane1,
                rowStride1, (const uint8_t *)plane2, rowStride2, pixStride1, i420_y, dst_w, i420_u,
                dst_w / 2, i420_v, dst_w / 2, width, height, st->rotate);

        // Step2: I420 → NV12
        I420ToNV12(i420_y, dst_w, i420_u, dst_w / 2, i420_v, dst_w / 2, dst_y, dst_w, dst_uv, dst_w,
                dst_w, dst_h);

        size.w = dst_w;
        size.h = dst_h;
    } else {
        // If the format is not supported, the black screen data will be output by default
        isWrite = false;
    }

    if (isWrite) {
        // Fill the frame
        vidframe_init_buf(st->frame, st->fmt, &size, st->buf);
    }

    // Send data to the encoder
    process_frame(st);
}
