#include <string.h>
#include <pthread.h>
#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <re.h>
#include <baresip.h>

#define LOGD(...) \
  ((void)__android_log_print(ANDROID_LOG_DEBUG, "Baresip", __VA_ARGS__))

#define LOGI(...) \
  ((void)__android_log_print(ANDROID_LOG_INFO, "Baresip", __VA_ARGS__))

#define LOGW(...) \
  ((void)__android_log_print(ANDROID_LOG_WARN, "Baresip", __VA_ARGS__))

#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, "Baresip", __VA_ARGS__))

typedef struct tick_context {
    JavaVM  *javaVM;
    jclass   jniHelperClz;
    jobject  jniHelperObj;
    jclass   mainActivityClz;
    jobject  mainActivityObj;
    pthread_mutex_t  lock;
    int      done;
} TickContext;

TickContext g_ctx;

static void signal_handler(int sig)
{
	static bool term = false;

	if (term) {
		mod_close();
		exit(0);
	}

	term = true;
    LOGI("terminated by signal (%d)\n", sig);
    ua_stop_all(false);
}

static void ua_exit_handler(void *arg)
{
	(void)arg;
	LOGD("ua exited -- stopping main runloop\n");
	re_cancel();
}

static const char *ua_event_reg_str(enum ua_event ev)
{
    switch (ev) {
        case UA_EVENT_REGISTERING:      return "registering";
        case UA_EVENT_REGISTER_OK:      return "registered";
        case UA_EVENT_REGISTER_FAIL:    return "registering failed";
        case UA_EVENT_UNREGISTERING:    return "unregistering";
        default: return "?";
    }
}

static void ua_event_handler(struct ua *ua, enum ua_event ev,
			     struct call *call, const char *prm, void *arg)
{
    const char *event;
    char event_buf[256];
    char ua_buf[256];
    char call_buf[256];

    LOGD("ua event (%s)\n", uag_event_str(ev));

    switch (ev) {
        case UA_EVENT_REGISTERING:
        case UA_EVENT_UNREGISTERING:
        case UA_EVENT_REGISTER_OK:
        case UA_EVENT_REGISTER_FAIL:
            re_snprintf(event_buf, sizeof event_buf, "%s", ua_event_reg_str(ev));
            break;
        case UA_EVENT_CALL_RINGING:
            re_snprintf(event_buf, sizeof event_buf, "%s", "call ringing");
            break;
        case UA_EVENT_CALL_PROGRESS:
            re_snprintf(event_buf, sizeof event_buf, "%s", "call progress");
            break;
        case UA_EVENT_CALL_ESTABLISHED:
            re_snprintf(event_buf, sizeof event_buf, "%s", "call established");
            break;
        case UA_EVENT_CALL_INCOMING:
            re_snprintf(event_buf, sizeof event_buf, "%s", "call incoming");
            break;
        case UA_EVENT_CALL_CLOSED:
            re_snprintf(event_buf, sizeof event_buf, "%s", "call closed");
            break;
        case UA_EVENT_EXIT:
            re_snprintf(event_buf, sizeof event_buf, "%s", "exit");
            break;
        default:
            re_snprintf(event_buf, sizeof event_buf, "%s", "unknown event");
    }
    event = event_buf;

    TickContext *pctx = (TickContext*)(&g_ctx);
    JavaVM *javaVM = pctx->javaVM;
    JNIEnv *env;
    jint res = (*javaVM)->GetEnv(javaVM, (void**)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        if (JNI_OK != res) {
            LOGE("Failed to AttachCurrentThread, ErrorCode = %d", res);
            return;
        }
    }
    jmethodID statusId = (*env)->GetMethodID(env, pctx->jniHelperClz,
                                             "updateStatus",
                                             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    sprintf(ua_buf, "%lu", (unsigned long)ua);
    jstring javaUA = (*env)->NewStringUTF(env, ua_buf);
    sprintf(call_buf, "%lu", (unsigned long)call);
    jstring javaCall = (*env)->NewStringUTF(env, call_buf);
    jstring javaEvent = (*env)->NewStringUTF(env, event);
    LOGD("sending ua/call %s/%s event %s\n", ua_buf, call_buf, event);
    (*env)->CallVoidMethod(env, pctx->jniHelperObj, statusId, javaEvent, javaUA, javaCall);
    (*env)->DeleteLocalRef(env, javaUA);
    (*env)->DeleteLocalRef(env, javaCall);
    (*env)->DeleteLocalRef(env, javaEvent);
}

#include <unistd.h>

static int pfd[2];
static pthread_t loggingThread;

static void *loggingFunction() {
    ssize_t readSize;
    char buf[128];

    while((readSize = read(pfd[0], buf, sizeof buf - 1)) > 0) {
        if(buf[readSize - 1] == '\n') {
            --readSize;
        }
        buf[readSize] = 0;
        LOGD("%s", buf);
    }

    return 0;
}

static int runLoggingThread() {
    setvbuf(stdout, 0, _IOLBF, 0);
    setvbuf(stderr, 0, _IONBF, 0);

    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    if (pthread_create(&loggingThread, 0, loggingFunction, 0) == -1) {
        return -1;
    }

    pthread_detach(loggingThread);

    return 0;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;

    LOGD("Executing JNI_OnLoad\n");

    memset(&g_ctx, 0, sizeof(g_ctx));

    g_ctx.javaVM = vm;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR; // JNI version not supported.
    }

    jclass  clz = (*env)->FindClass(env, "com/tutpro/baresip/MainActivity");
    g_ctx.jniHelperClz = (*env)->NewGlobalRef(env, clz);

    jmethodID  jniHelperCtor = (*env)->GetMethodID(env, g_ctx.jniHelperClz,
                                                   "<init>", "()V");
    jobject    handler = (*env)->NewObject(env, g_ctx.jniHelperClz,
                                           jniHelperCtor);
    g_ctx.jniHelperObj = (*env)->NewGlobalRef(env, handler);

    g_ctx.done = 0;
    g_ctx.mainActivityObj = NULL;
    return  JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_MainActivity_baresipStart(JNIEnv *env, jobject thiz, jstring javaPath)
{
    int err;
    const char *path = (*env)->GetStringUTFChars(env, javaPath, 0);
    struct le *le;

    runLoggingThread();

    err = libre_init();
    if (err)
	    goto out;

    conf_path_set(path);
    
    err = conf_configure();
    if (err) {
        LOGW("conf_configure() failed: (%d)\n", err);
        goto out;
    }

    err = baresip_init(conf_config(), false);
    if (err) {
        LOGW("baresip_init() failed (%d)\n", err);
        goto out;
    }

    play_set_path(baresip_player(), path);

    err = ua_init("baresip v" BARESIP_VERSION " (" ARCH "/" OS ")",
                  true, true, true, false);
    if (err) {
        LOGE("ua_init() failed (%d)\n", err);
        goto out;
    }

    uag_set_exit_handler(ua_exit_handler, NULL);
    uag_event_register(ua_event_handler, NULL);

    err = conf_modules();
    if (err) {
        LOGW("conf_modules() failed (%d)\n", err);
        goto out;
    }

    TickContext *pctx = (TickContext*)(&g_ctx);
    JavaVM *javaVM = pctx->javaVM;
    jint res = (*javaVM)->GetEnv(javaVM, (void**)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        if (JNI_OK != res) {
            LOGE("Failed to AttachCurrentThread, ErrorCode = %d", res);
            return;
        }
    }

    LOGD("Adding %u accounts", list_count(uag_list()));
    char ua_buf[256];
    struct ua *ua;
    for (le = list_head(uag_list()); le; le = le->next) {
        ua = le->data;
        sprintf(ua_buf, "%lu", (unsigned long)ua);
        jstring javaUA = (*env)->NewStringUTF(env, ua_buf);
        LOGD("adding account %s/%s\n", ua_aor(ua), ua_buf);
        jmethodID accountId = (*env)->GetMethodID(env, pctx->jniHelperClz, "addAccount",
                                                  "(Ljava/lang/String;)V");
        (*env)->CallVoidMethod(env, pctx->jniHelperObj, accountId, javaUA);
        (*env)->DeleteLocalRef(env, javaUA);
    }

    LOGI("Running main loop\n");
    err = re_main(signal_handler);

out:

    if (err) {
        LOGE("error: (%d)\n", err);
	    ua_stop_all(true);
    }

    LOGD("closing upon main loop exit");
    ua_close();
    conf_close();
    baresip_close();
    mod_close();
    libre_close();

    // tmr_debug();
    // mem_debug();

    return;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_MainActivity_baresipStop(JNIEnv *env, jobject thiz)
{
    LOGD("closing upon stop");
    ua_stop_all(false);
    ua_close();
    // conf_close();
    // baresip_close();
    // mod_close();
    // libre_close();

    // tmr_debug();
    // mem_debug();

    return;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1aor(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);

    if (strlen(native_ua) > 0)
        return (*env)->NewStringUTF(env, ua_aor(ua));
    else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jboolean JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1isregistered(JNIEnv *env, jobject thiz, jlong ua_ptr)
{
    struct ua *ua = (struct ua *)ua_ptr;
    bool result;

    result = ua_isregistered(ua);
    if (ua == NULL) {
        LOGD("ua_ptr is null\n");
    } else {
        LOGD("ua_ptr is NOT null\n");
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1current_1set(JNIEnv *env, jobject thiz,
                                                      jstring javaAoR)
{
    struct ua *new_current_ua;
    const char *native_aor = (*env)->GetStringUTFChars(env, javaAoR, 0);

    LOGD("running ua_current_set on %s\n", native_aor);
    new_current_ua = uag_find_aor(native_aor);
    uag_current_set(new_current_ua);
    (*env)->ReleaseStringUTFChars(env, javaAoR, native_aor);
    return;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1current(JNIEnv *env, jobject thiz)
{
    struct ua *current_ua = uag_current();
    char ua_buf[256];

    if (current_ua == NULL)
        ua_buf[0] = '\0';
    else
        sprintf(ua_buf, "%lu", (unsigned long)current_ua);
    return (*env)->NewStringUTF(env, ua_buf);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1prev_1call(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);

    struct call *call = ua_prev_call(ua);
    char call_buf[256];
    if (call == NULL)
        call_buf[0] = '\0';
    else
        sprintf(call_buf, "%lu", (unsigned long)call);
    return (*env)->NewStringUTF(env, call_buf);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1call(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);

    struct call *call = ua_call(ua);
    char call_buf[256];
    sprintf(call_buf, "%lu", (unsigned long)call);
    return (*env)->NewStringUTF(env, call_buf);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_MainActivity_call_1peeruri(JNIEnv *env, jobject thiz, jstring javaCall)
{
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call;

    call = (struct call *)strtoul(native_call, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    return (*env)->NewStringUTF(env, call_peeruri(call));
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1connect(JNIEnv *env, jobject thiz,
                                                jstring uri) {
    struct call *call;
    struct ua *ua;
    int err;
    const char *native_uri = (*env)->GetStringUTFChars(env, uri, 0);
    char call_buf[256];

    LOGD("connecting to %s\n", native_uri);
    ua = uag_current();
    if (ua != NULL) {
        err = ua_connect(ua, &call, NULL, native_uri, NULL, VIDMODE_ON);
        if (err) {
            LOGW("connecting to %s failed with error %d\n", native_uri, err);
            call_buf[0] = '\0';
        } else {
            sprintf(call_buf, "%lu", (unsigned long)call);
        }
    } else {
        LOGE("no current ua\n");
        call_buf[0] = '\0';
    }
    (*env)->ReleaseStringUTFChars(env, uri, native_uri);
    return (*env)->NewStringUTF(env, call_buf);
}


JNIEXPORT void JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1answer(JNIEnv *env, jobject thiz,
                                                jstring javaUA, jstring javaCall) {
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    LOGD("answering call %s/%s\n", native_ua, native_call);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    ua_answer(ua, call);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);

    return;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1hangup(JNIEnv *env, jobject thiz,
                                                jstring javaUA, jstring javaCall, jint code,
                                                jstring reason) {
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    const uint16_t native_code = code;
    const char *native_reason = (*env)->GetStringUTFChars(env, reason, 0);
    LOGD("hanging up call %s/%s\n", native_ua, native_call);
    // ua_hangup(ua, call, native_code, native_reason);
    ua_hangup(uag_current(), NULL, 0, NULL);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    (*env)->ReleaseStringUTFChars(env, reason, native_reason);
    return;
}
