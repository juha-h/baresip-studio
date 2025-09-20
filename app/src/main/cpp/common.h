//
// Created by Administrator on 2025/9/19.
//

#ifndef BARESIP_STUDIO_COMMON_H
#define BARESIP_STUDIO_COMMON_H

#include <jni.h>
#include <stdbool.h>

#define jni_get_env(jni_env) jni_attach_jvm((void **)jni_env)
#define jni_detach_env(attached) jni_detach_jvm(attached)

void jni_set_jvm(JavaVM *jvm);

JavaVM *jni_get_jvm(void);

bool jni_attach_jvm(void **jni_env);

void jni_detach_jvm(bool attached);

#endif //BARESIP_STUDIO_COMMON_H
