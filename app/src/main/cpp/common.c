//
// Created by Administrator on 2025/9/19.
//
#include "common.h"

JavaVM *jni_jvm = NULL;

void jni_set_jvm(JavaVM *jvm)
{
    jni_jvm = jvm;
}

bool jni_attach_jvm(void **jni_env)
{
    if (!jni_jvm)
        return false;

    if ((*jni_jvm)->GetEnv(jni_jvm, jni_env, JNI_VERSION_1_6) < 0) {
        if ((*jni_jvm)->AttachCurrentThread(jni_jvm, (JNIEnv **)jni_env, NULL) < 0) {
            jni_env = NULL;
            return false;
        }
        return true;
    }

    return false;
}

void jni_detach_jvm(bool attached)
{
    if (!jni_jvm)
        return;

    if (attached)
        (*jni_jvm)->DetachCurrentThread(jni_jvm);
}