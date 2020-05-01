#include <stdint.h>
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <re.h>
#include <rem.h>
#include "logger.h"
#include "vidisp.h"

#define LOG_TAG "Baresip vidisp"

static ANativeWindow *window = NULL;

struct vidisp *vid;

static void renderer_destroy(struct vidisp_st *st) {

    LOGI("Destroying renderer context");

    eglMakeCurrent(st->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(st->display, st->context);
    eglDestroySurface(st->display, st->surface);
    eglTerminate(st->display);

    st->display = EGL_NO_DISPLAY;
    st->surface = EGL_NO_SURFACE;
    st->context = EGL_NO_CONTEXT;

    return;
}

static void destructor(void *arg)
{
    struct vidisp_st *st = arg;

    renderer_destroy(st);
    mem_deref(st->vf);
}

static int renderer_initialize(struct vidisp_st *st)
{
    const EGLint attribs[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_BLUE_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_RED_SIZE, 8,
            EGL_NONE
    };

    EGLDisplay display;
    EGLConfig config;
    EGLConfig *configs;
    EGLint numConfigs;
    EGLint format;
    EGLSurface surface;
    EGLContext context;

    LOGI("Initializing context\n");

    if ((display = eglGetDisplay(EGL_DEFAULT_DISPLAY)) == EGL_NO_DISPLAY) {
        LOGW("eglGetDisplay() returned error %d\n", eglGetError());
        return eglGetError();
    }
    if (!eglInitialize(display, NULL, NULL)) {
        LOGW("eglInitialize() returned error %d\n", eglGetError());
        return eglGetError();
    }

    if (!eglChooseConfig(display, attribs, &config, 1, &numConfigs)) {
        LOGW("eglChooseConfig() returned error %d\n", eglGetError());
        renderer_destroy(st);
        return eglGetError();
    }

    if (!eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format)) {
        LOGW("eglGetConfigAttrib() returned error %d\n", eglGetError());
        renderer_destroy(st);
        return eglGetError();
    }

    ANativeWindow_setBuffersGeometry(st->window, 0, 0, format);

    if (!(surface = eglCreateWindowSurface(display, config, st->window, NULL))) {
        LOGW("eglCreateWindowSurface() returned error %d\n", eglGetError());
        renderer_destroy(st);
        return eglGetError();
    }

    EGLint value;
    eglQuerySurface(display, surface, EGL_RENDER_BUFFER, &value);
    LOGI("surface render buffer is %d\n", value);

    if (!(context = eglCreateContext(display, config, EGL_NO_CONTEXT, NULL))) {
        LOGW("eglCreateContext() returned error %d\n", eglGetError());
        renderer_destroy(st);
        return eglGetError();
    }

    if (!eglMakeCurrent(display, surface, surface, context)) {
        LOGW("eglMakeCurrent() returned error %d\n", eglGetError());
        renderer_destroy(st);
        return eglGetError();
    }

    eglQueryContext(display, context, EGL_RENDER_BUFFER, &value);
    LOGI("context render buffer is %d\n", value);

    if (!eglQuerySurface(display, surface, EGL_WIDTH, &st->width) ||
        !eglQuerySurface(display, surface, EGL_HEIGHT, &st->height)) {
        LOGW("eglQuerySurface() returned error %d\n", eglGetError());
        renderer_destroy(st);
        return eglGetError();
    }

    st->display = display;
    st->surface = surface;
    st->context = context;

    glDisable(GL_DITHER);
    glEnable(GL_DEPTH_TEST);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    LOGI("Context initialized\n");

    return 0;
}

int opengles_alloc(struct vidisp_st **stp, const struct vidisp *vd, struct vidisp_prm *prm,
        const char *dev, vidisp_resize_h *resizeh, void *arg)
{
    struct vidisp_st *st;
    int err = 0;

    (void)prm;
    (void)dev;
    (void)resizeh;
    (void)arg;

    LOGI("At opengles_alloc()\n");

    st = mem_zalloc(sizeof(*st), destructor);
    if (!st)
        return ENOMEM;

    st->vd = vd;
    st->window = window;

    if (st->window == NULL) {
        LOGW("Window is NULL\n");
        return EINVAL;
    }

    err = renderer_initialize(st);

    if (err)
        mem_deref(st);
    else
        *stp = st;

    return err;
}


static int texture_init(struct vidisp_st *st)
{
    glGenTextures(1, &st->texture_id);
    if (!st->texture_id)
        return ENOMEM;

    glBindTexture(GL_TEXTURE_2D, st->texture_id);
    glTexParameterf(GL_TEXTURE_2D, GL_GENERATE_MIPMAP, GL_FALSE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, st->vf->size.w, st->vf->size.h, 0,
                 GL_RGB, GL_UNSIGNED_SHORT_5_6_5, st->vf->data[0]);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);

    return 0;
}

static void texture_render(struct vidisp_st *st)
{
    static const GLfloat coords[4 * 2] = {
            0.0, 1.0,
            1.0, 1.0,
            0.0, 0.0,
            1.0, 0.0
    };

    glBindTexture(GL_TEXTURE_2D, st->texture_id);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, st->vf->size.w, st->vf->size.h, 0, GL_RGB,
            GL_UNSIGNED_SHORT_5_6_5, st->vf->data[0]);

    glClientActiveTexture(GL_TEXTURE0);

    glEnableClientState(GL_VERTEX_ARRAY);
    glVertexPointer(3, GL_FLOAT, 0, st->vertices);

    glEnableClientState(GL_TEXTURE_COORD_ARRAY);
    glTexCoordPointer(2, GL_FLOAT, 0, coords);

    glBindTexture(GL_TEXTURE_2D, st->texture_id);

    glEnable(GL_TEXTURE_2D);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisable(GL_TEXTURE_2D);
}

static void setup_layout(struct vidisp_st *st, const struct vidsz *screensz, struct vidrect *ortho,
        struct vidrect *vp)
{
    int x, y, w, h, i = 0;

    w = st->vf->size.w;
    h = st->vf->size.h;

    st->vertices[i++] = 0;
    st->vertices[i++] = 0;
    st->vertices[i++] = 0;
    st->vertices[i++] = w;
    st->vertices[i++] = 0;
    st->vertices[i++] = 0;
    st->vertices[i++] = 0;
    st->vertices[i++] = h;
    st->vertices[i++] = 0;
    st->vertices[i++] = w;
    st->vertices[i++] = h;
    st->vertices[i++] = 0;

    x = (screensz->w - w) / 2;
    y = (screensz->h - h) / 2;

    if (x < 0) {
        vp->x    = 0;
        ortho->x = -x;
    }
    else {
        vp->x    = x;
        ortho->x = 0;
    }

    if (y < 0) {
        vp->y    = 0;
        ortho->y = -y;
    }
    else {
        vp->y    = y;
        ortho->y = 0;
    }

    vp->w = screensz->w - 2 * vp->x;
    vp->h = screensz->h - 2 * vp->y;

    ortho->w = w - ortho->x;
    ortho->h = h - ortho->y;
}

void opengles_render(struct vidisp_st *st)
{
    if (!st->texture_id) {

        struct vidrect ortho, vp;
        struct vidsz bufsz;
        int err = 0;

        err = texture_init(st);
        if (err)
            return;

        bufsz.w = st->width;
        bufsz.h = st->height;

        LOGI("********* frame width/height = %d/%d\n", st->vf->size.w, st->vf->size.h);

        setup_layout(st, &bufsz, &ortho, &vp);

        GLint params[2];
        glGetIntegerv(GL_MAX_VIEWPORT_DIMS, params);
        LOGI("***** viewport max w/h = %d/%d\n", params[0], params[1]);

        LOGI("******* glViewport x/y/w/h = %d/%d/%d/%d\n", vp.x, vp.y, vp.w, vp.h);
        glViewport(vp.x, vp.y, vp.w, vp.h);

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        LOGI("******* ortho x/w/y/h = %d/%d/%d/%d\n",
             ortho.x, ortho.w, ortho.y, ortho.h);
        glOrthof(ortho.x, ortho.w, ortho.y, ortho.h, 0.0f, 1.0f);
        //		float ar = (float)st->_width / (float)st->_height;

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        glDisableClientState(GL_COLOR_ARRAY);

    }

    texture_render(st);

    glDisable(GL_TEXTURE_2D);
    glDisableClientState(GL_VERTEX_ARRAY);
    glDisableClientState(GL_COLOR_ARRAY);
    glDisableClientState(GL_TEXTURE_COORD_ARRAY);
    glBindTexture(GL_TEXTURE_2D, 0);
    glEnable(GL_DEPTH_TEST);

    glFlush();
}

int opengles_display(struct vidisp_st *st, const char *title, const struct vidframe *frame,
        uint64_t timestamp)
{
    (void)title;
    (void)timestamp;
    int err;

    if (!st->vf) {
        if (frame->size.w & 3) {
            LOGW("opengles: width must be multiple of 4\n");
            return EINVAL;
        }

        err = vidframe_alloc(&st->vf, VID_FMT_RGB565, &frame->size);
        if (err)
            return err;
    }

    vidconv(st->vf, frame, NULL);

    opengles_render(st);

    if (!eglSwapBuffers(st->display, st->surface))
        LOGW("eglSwapBuffers() returned error %d\n", eglGetError());

    return 0;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_VideoView_on_1start(JNIEnv *env, jclass thiz) {
    LOGI("VideoView on_start");
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_VideoView_on_1resume(JNIEnv *env, jclass thiz) {
    LOGI("VideoView on_resume");
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_VideoView_on_1pause(JNIEnv *env, jclass thiz) {
    LOGI("VideoView on_pause");
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_VideoView_on_1stop(JNIEnv *env, jclass thiz) {
    LOGI("VideoView on_stop");
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_VideoView_set_1surface(JNIEnv *env, jclass thiz, jobject surface) {
    LOGI("VideoView set_surface");
    if (surface != 0) {
        window = ANativeWindow_fromSurface(env, surface);
        LOGI("Got window %p", window);
    } else {
        LOGI("Releasing window");
        ANativeWindow_release(window);
    }
}

