#ifndef BARESIP_VIDISP_H
#define BARESIP_VIDISP_H

#include <EGL/egl.h>
#include <GLES/gl.h>
#include <GLES/glext.h>

struct vidisp_st
{
    const struct vidisp *vd;
    struct vidframe *vf;

    GLuint texture_id;
    GLfloat vertices[4 * 3];

    EGLDisplay display;
    EGLSurface surface;
    EGLContext context;
    EGLint width;
    EGLint height;
};

extern struct vidisp *vid;

extern int opengles_alloc(struct vidisp_st **stp, const struct vidisp *vd, struct vidisp_prm *prm,
        const char *dev, vidisp_resize_h *resizeh, void *arg);

extern int opengles_display(
        struct vidisp_st *st, const char *title, const struct vidframe *frame, uint64_t timestamp);

#endif //BARESIP_VIDISP_H
