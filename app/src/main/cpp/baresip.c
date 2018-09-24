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

typedef struct baresip_context {
    JavaVM  *javaVM;
    jclass   jniHelperClz;
    jobject  jniHelperObj;
    jclass   mainActivityClz;
    jobject  mainActivityObj;
} BaresipContext;

BaresipContext g_ctx;

struct play *play = NULL;
struct message_lsnr *message;

static int vprintf_null(const char *p, size_t size, void *arg)
{
    (void)p;
    (void)size;
    (void)arg;
    return 0;
}

static struct re_printf pf_null = {vprintf_null, 0};

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

static const char *translate_errorcode(uint16_t scode)
{
	switch (scode) {
        case 404: return "notfound.wav";
	    case 486: return "busy.wav";
	    case 487: return NULL; /* ignore */
	    default:  return "error.wav";
	}
}

static void ua_event_handler(struct ua *ua, enum ua_event ev,
			     struct call *call, const char *prm, void *arg)
{
    const char *event;
    char event_buf[256];
    char ua_buf[256];
    char call_buf[256];

    struct player *player = baresip_player();

    LOGD("ua event (%s) %s\n", uag_event_str(ev), prm);

    switch (ev) {
        case UA_EVENT_REGISTERING:
        case UA_EVENT_UNREGISTERING:
        case UA_EVENT_REGISTER_OK:
        case UA_EVENT_REGISTER_FAIL:
            re_snprintf(event_buf, sizeof event_buf, "%s", ua_event_reg_str(ev));
            break;
        case UA_EVENT_CALL_INCOMING:
            if (list_count(ua_calls(ua)) > 1) {
                play = mem_deref(play);
                (void)play_file(&play, player, "callwaiting.wav", 3);
            }
            re_snprintf(event_buf, sizeof event_buf, "%s", "call incoming");
            break;
        case UA_EVENT_CALL_RINGING:
            play = mem_deref(play);
            (void)play_file(&play, player, "ringback.wav", -1);
            re_snprintf(event_buf, sizeof event_buf, "%s", "call ringing");
            break;
        case UA_EVENT_CALL_PROGRESS:
            re_snprintf(event_buf, sizeof event_buf, "%s", "call progress");
            break;
        case UA_EVENT_CALL_ESTABLISHED:
            play = mem_deref(play);
            re_snprintf(event_buf, sizeof event_buf, "%s", "call established");
            break;
        case UA_EVENT_CALL_MENC:
            if (prm[0] == '0')
                re_snprintf(event_buf, sizeof event_buf, "call secure");
            else if (prm[0] == '1')
                re_snprintf(event_buf, sizeof event_buf, "call verify,%s", prm+2);
            else if (prm[0] == '2')
                re_snprintf(event_buf, sizeof event_buf, "call verified,%s", prm+2);
            else
                re_snprintf(event_buf, sizeof event_buf, "%s", "unknown menc event");
            break;
        case UA_EVENT_CALL_CLOSED:
            play = mem_deref(play);
            if (call_scode(call)) {
			    const char *tone;
			    tone = translate_errorcode(call_scode(call));
			    if (tone) {
				    (void)play_file(&play, player, tone, 1);
			    }
		    }
            re_snprintf(event_buf, sizeof event_buf, "%s", "call closed");
            break;
        case UA_EVENT_MWI_NOTIFY:
            re_snprintf(event_buf, sizeof event_buf, "mwi notify,%s", prm);
            break;
        case UA_EVENT_EXIT:
            re_snprintf(event_buf, sizeof event_buf, "%s", "exit");
            break;
        default:
            re_snprintf(event_buf, sizeof event_buf, "%s", "unknown event");
            return;
    }
    event = event_buf;

    BaresipContext *pctx = (BaresipContext*)(&g_ctx);
    JavaVM *javaVM = pctx->javaVM;
    JNIEnv *env;
    jint res = (*javaVM)->GetEnv(javaVM, (void**)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        if (JNI_OK != res) {
            LOGE("failed to AttachCurrentThread, ErrorCode = %d", res);
            return;
        }
    }
    jmethodID methodId = (*env)->GetMethodID(env, pctx->mainActivityClz,
                                             "uaEvent",
                                             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    sprintf(ua_buf, "%lu", (unsigned long)ua);
    jstring javaUA = (*env)->NewStringUTF(env, ua_buf);
    sprintf(call_buf, "%lu", (unsigned long)call);
    jstring javaCall = (*env)->NewStringUTF(env, call_buf);
    jstring javaEvent = (*env)->NewStringUTF(env, event);
    LOGD("sending ua/call %s/%s event %s\n", ua_buf, call_buf, event);
    (*env)->CallVoidMethod(env, pctx->mainActivityObj, methodId, javaEvent, javaUA, javaCall);
    (*env)->DeleteLocalRef(env, javaUA);
    (*env)->DeleteLocalRef(env, javaCall);
    (*env)->DeleteLocalRef(env, javaEvent);
}

static void message_handler(struct ua *ua, const struct pl *peer, const struct pl *ctype,
                            struct mbuf *body, void *arg)
{
    (void)ctype;
    (void)arg;
    char ua_buf[32];
    char peer_buf[256];
    size_t size;

    LOGD("got message '%.*s' from peer '%.*s'", mbuf_get_left(body), mbuf_buf(body),
        peer->l, peer->p);

    BaresipContext *pctx = (BaresipContext*)(&g_ctx);
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
    jmethodID methodId = (*env)->GetMethodID(env, pctx->mainActivityClz,
                                             "messageEvent",
                                             "(Ljava/lang/String;Ljava/lang/String;[B)V");
    sprintf(ua_buf, "%lu", (unsigned long)ua);
    jstring javaUA = (*env)->NewStringUTF(env, ua_buf);
    sprintf(peer_buf, "%.*s", peer->l, peer->p);
    jstring javaPeer = (*env)->NewStringUTF(env, peer_buf);
    jbyteArray javaMsg;
    size = mbuf_get_left(body);
    javaMsg = (*env)->NewByteArray(env, size);
    if ((*env)->GetArrayLength(env, javaMsg) != size) {
        (*env)->DeleteLocalRef(env, javaMsg);
        javaMsg = (*env)->NewByteArray(env, size);
    }
    void *temp = (*env)->GetPrimitiveArrayCritical(env, (jarray)javaMsg, 0);
    memcpy(temp, mbuf_buf(body), size);
    (*env)->ReleasePrimitiveArrayCritical(env, javaMsg, temp, 0);
    LOGD("sending message %s/%s/%.*s\n", ua_buf, peer_buf, size, mbuf_buf(body));
    (*env)->CallVoidMethod(env, pctx->mainActivityObj, methodId, javaUA, javaPeer, javaMsg);
    (*env)->DeleteLocalRef(env, javaUA);
    (*env)->DeleteLocalRef(env, javaPeer);
    (*env)->DeleteLocalRef(env, javaMsg);
}

static void send_resp_handler(int err, const struct sip_msg *msg, void *arg)
{
    (void)arg;

    if (err) {
        LOGD("send_response_handler received error %d\n", err);
        return;
    }

    LOGD("send_response_handler received response %u at %s\n", msg->scode, (char *)arg);

    BaresipContext *pctx = (BaresipContext*)(&g_ctx);
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
    jmethodID methodId = (*env)->GetMethodID(env, pctx->mainActivityClz,
                                             "messageResponse", "(ILjava/lang/String;)V");
    jstring javaTime = (*env)->NewStringUTF(env, (char *)arg);
    (*env)->CallVoidMethod(env, pctx->mainActivityObj, methodId, msg->scode, javaTime);
    (*env)->DeleteLocalRef(env, javaTime);

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

    jmethodID jniHelperCtor = (*env)->GetMethodID(env, g_ctx.jniHelperClz, "<init>", "()V");
    jobject    handler = (*env)->NewObject(env, g_ctx.jniHelperClz, jniHelperCtor);
    g_ctx.jniHelperObj = (*env)->NewGlobalRef(env, handler);

    g_ctx.mainActivityObj = NULL;

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_BaresipService_baresipStart(JNIEnv *env, jobject instance, jstring javaPath) {

    BaresipContext *pctx = (BaresipContext *)(&g_ctx);
    JavaVM *javaVM = pctx->javaVM;
    jint res = (*javaVM)->GetEnv(javaVM, (void **) &env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        LOGE("failed to GetEnv, ErrorCode = %d", res);
        goto stopped;
    }
    jclass clz = (*env)->GetObjectClass(env, instance);
    g_ctx.mainActivityClz = (*env)->NewGlobalRef(env, clz);
    g_ctx.mainActivityObj = (*env)->NewGlobalRef(env, instance);

    int err;
    const char *path = (*env)->GetStringUTFChars(env, javaPath, 0);
    struct le *le;

    runLoggingThread();

    err = libre_init();
    if (err)
        goto out;

    conf_path_set(path);

    // log_enable_debug(true);

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

    err = uag_event_register(ua_event_handler, NULL);
    if (err) {
        LOGE("uag_event_register() failed (%d)\n", err);
        goto out;
    }

    err = message_listen(&message, baresip_message(), message_handler, NULL);
    if (err) {
        LOGE("message_listen() failed (%d)\n", err);
        goto out;
    }

    err = conf_modules();
    if (err) {
        LOGW("conf_modules() failed (%d)\n", err);
        goto out;
    }

    LOGD("adding %u accounts", list_count(uag_list()));
    char ua_buf[256];
    struct ua *ua;
    for (le = list_head(uag_list()); le != NULL; le = le->next) {
        ua = le->data;
        sprintf(ua_buf, "%lu", (unsigned long) ua);
        jstring javaUA = (*env)->NewStringUTF(env, ua_buf);
        LOGD("adding UA for AoR %s/%s\n", ua_aor(ua), ua_buf);
        jmethodID uaAddId = (*env)->GetMethodID(env, pctx->mainActivityClz, "uaAdd",
                                                  "(Ljava/lang/String;)V");
        (*env)->CallVoidMethod(env, pctx->mainActivityObj, uaAddId, javaUA);
        (*env)->DeleteLocalRef(env, javaUA);
    }

    LOGI("running main loop ...\n");
    err = re_main(signal_handler);

    out:

    if (err) {
        LOGE("stopping UAs due to error: (%d)\n", err);
        ua_stop_all(true);
    }

    play = mem_deref(play);
    message = mem_deref(message);

    LOGD("closing ...");
    ua_close();
    conf_close();
    baresip_close();

    uag_event_unregister(ua_event_handler);

    LOGD("unloading modules ...");
    mod_close();

    libre_close();

    // tmr_debug();
    // mem_debug();

    stopped:

    LOGD("tell app about baresip stop");
    jmethodID stoppedId = (*env)->GetMethodID(env, pctx->mainActivityClz, "stopped", "()V");
    (*env)->CallVoidMethod(env, pctx->mainActivityObj, stoppedId);

    return;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_BaresipService_baresipStop(JNIEnv *env, jobject thiz, jboolean force) {
    LOGD("ua_stop_all upon baresipStop");
    ua_stop_all(force);
    return;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_AccountKt_account_1display_1name(JNIEnv *env, jobject thiz, jstring javaAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc) {
        const char *dn = account_display_name(acc);
        if (dn)
            return (*env)->NewStringUTF(env, dn);
        else
            return (*env)->NewStringUTF(env, "");
    } else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_AccountKt_account_1set_1display_1name(JNIEnv *env, jobject thiz,
                                                              jstring javaAcc, jstring javaDn) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *dn = (*env)->GetStringUTFChars(env, javaDn, 0);\
    int res;
    if (strlen(dn) > 0)
        res = account_set_display_name(acc, dn);
    else
        res = account_set_display_name(acc, NULL);
    (*env)->ReleaseStringUTFChars(env, javaDn, dn);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_AccountKt_account_1aor(JNIEnv *env, jobject thiz, jstring javaAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc)
        return (*env)->NewStringUTF(env, account_aor(acc));
    else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_AccountKt_account_1auth_1user(JNIEnv *env, jobject thiz, jstring javaAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc) {
        const char *au = account_auth_user(acc);
        if (au)
            return (*env)->NewStringUTF(env, au);
        else
            return (*env)->NewStringUTF(env, "");
    } else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_AccountKt_account_1set_1auth_1user(JNIEnv *env, jobject thiz,
                                                           jstring javaAcc, jstring javaUser) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *user = (*env)->GetStringUTFChars(env, javaUser, 0);
    int res;
    if (strlen(user) > 0)
        res = account_set_auth_user(acc, user);
    else
        res = account_set_auth_user(acc, NULL);
    (*env)->ReleaseStringUTFChars(env, javaUser, user);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_AccountKt_account_1auth_1pass(JNIEnv *env, jobject thiz, jstring javaAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc) {
        const char *ap = account_auth_pass(acc);
        if (ap)
            return (*env)->NewStringUTF(env, ap);
        else
            return (*env)->NewStringUTF(env, "");
    } else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_AccountKt_account_1set_1auth_1pass(JNIEnv *env, jobject thiz,
                                                           jstring javaAcc, jstring javaPass) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *pass = (*env)->GetStringUTFChars(env, javaPass, 0);
    int res;
    if (strlen(pass) > 0)
        res = account_set_auth_pass(acc, pass);
    else
        res = account_set_auth_pass(acc, NULL);
    (*env)->ReleaseStringUTFChars(env, javaPass, pass);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_AccountKt_account_1outbound(JNIEnv *env, jobject thiz, jstring javaAcc,
                                                    jint ix) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    const uint16_t native_ix = ix;
    const char *outbound;
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc) {
        outbound = account_outbound(acc, native_ix);
        if (outbound)
            return (*env)->NewStringUTF(env, outbound);
        else
            return (*env)->NewStringUTF(env, "");
    } else {
        return (*env)->NewStringUTF(env, "");
    }
}

JNIEXPORT int JNICALL
Java_com_tutpro_baresip_AccountKt_account_1set_1outbound(JNIEnv *env, jobject thiz, jstring javaAcc,
                                                         jstring javaOb, jint javaIx) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *ob = (*env)->GetStringUTFChars(env, javaOb, 0);
    const uint16_t ix = javaIx;
    int res;
    if (strlen(ob) > 0)
        res = account_set_outbound(acc, ob, ix);
    else
        res = account_set_outbound(acc, NULL, ix);
    (*env)->ReleaseStringUTFChars(env, javaOb, ob);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_AccountKt_account_1audio_1codec(JNIEnv *env, jobject thiz, jstring javaAcc,
                                                    jint ix) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const struct list *codecl;
    char codec_buf[32];
    struct le *le;
    if (acc) {
        codecl = account_aucodecl(acc);
        if (!list_isempty(codecl)) {
            int i = -1;
            for (le = list_head(codecl); le != NULL; le = le->next) {
                i++;
                if (i > ix) break;
                if (i == ix) {
                    const struct aucodec *ac = le->data;
                    re_snprintf(codec_buf, sizeof codec_buf, "%s/%u/%u", ac->name, ac->srate, ac->ch);
                    break;
                }
            }
            if (i == ix) {
                return (*env)->NewStringUTF(env, codec_buf);
            }
        }
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_AccountKt_account_1set_1audio_1codecs(JNIEnv *env, jobject thiz,
                                                             jstring javaAcc, jstring javaCodecs) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *codecs = (*env)->GetStringUTFChars(env, javaCodecs, 0);
    LOGD("setting audio codecs '%s'\n", codecs);
    int res = account_set_audio_codecs(acc, codecs);
    (*env)->ReleaseStringUTFChars(env, javaCodecs, codecs);
    return res;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_AccountKt_account_1regint(JNIEnv *env, jobject thiz, jstring javaAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc)
        return account_regint(acc);
    else
        return 0;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_AccountKt_account_1set_1regint(JNIEnv *env, jobject thiz,
                                                           jstring javaAcc, jint javaRegint) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const uint32_t regint = (uint32_t)javaRegint;
    return account_set_regint(acc, regint);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_AccountKt_account_1mediaenc(JNIEnv *env, jobject thiz, jstring javaAcc)
{
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc) {
        const char *mediaenc = account_mediaenc(acc);
        if (mediaenc) return (*env)->NewStringUTF(env, mediaenc);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_AccountKt_account_1set_1mediaenc(JNIEnv *env, jobject thiz,
                                                              jstring javaAcc, jstring javaMencid) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *mencid = (*env)->GetStringUTFChars(env, javaMencid, 0);
    int res;
    if (strlen(mencid) > 0)
        res = account_set_mediaenc(acc, mencid);
    else
        res = account_set_mediaenc(acc, NULL);
    (*env)->ReleaseStringUTFChars(env, javaMencid, mencid);
    return res;
}
JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_AccountKt_account_1sipnat(JNIEnv *env, jobject thiz, jstring javaAcc)
{
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc) {
        const char *sipnat = account_sipnat(acc);
        if (sipnat) return (*env)->NewStringUTF(env, sipnat);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_AccountKt_account_1set_1sipnat(JNIEnv *env, jobject thiz,
                                                         jstring javaAcc, jstring javaSipNat) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *sipnat = (*env)->GetStringUTFChars(env, javaSipNat, 0);
    int res;
    if (strlen(sipnat) > 0)
        res = account_set_sipnat(acc, sipnat);
    else
        res = account_set_sipnat(acc, NULL);
    (*env)->ReleaseStringUTFChars(env, javaSipNat, sipnat);
    return res;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_AccountKt_account_1set_1mwi(JNIEnv *env, jobject thiz,
                                                              jstring javaAcc, jstring javaValue) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *value = (*env)->GetStringUTFChars(env, javaValue, 0);
    LOGD("setting account mwi '%s'\n", value);
    int res = account_set_mwi(acc, value);
    (*env)->ReleaseStringUTFChars(env, javaValue, value);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_AccountKt_account_1vm_1uri(JNIEnv *env, jobject thiz, jstring javaAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    char uri_buf[256];
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc) {
        struct pl pl;
        const struct sip_addr *addr = account_laddr(acc);
        int err = msg_param_decode(&(addr->params), "vm_uri", &pl);
        if (err) {
            return (*env)->NewStringUTF(env, "");
        } else {
            pl_strcpy(&pl, uri_buf, 256);
            return (*env)->NewStringUTF(env, uri_buf);
        }
    } else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_UserAgent_00024Companion_ua_1alloc(JNIEnv *env, jobject thiz,
                                                           jstring javaUri) {
    const char *uri = (*env)->GetStringUTFChars(env, javaUri, 0);
    struct ua *ua;
    LOGD("allocating UA '%s'\n", uri);
    int res = ua_alloc(&ua, uri);
    (*env)->ReleaseStringUTFChars(env, javaUri, uri);
    char ua_buf[64];
    ua_buf[0] = '\0';
    if (res == 0) sprintf(ua_buf, "%lu", (unsigned long)ua);
    return (*env)->NewStringUTF(env, ua_buf);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_UserAgent_00024Companion_ua_1register(JNIEnv *env, jobject thiz,
                                                              jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    return ua_register(ua);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_UserAgent_00024Companion_ua_1unregister(JNIEnv *env, jobject thiz,
                                                                jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    ua_unregister(ua);
}

JNIEXPORT jboolean JNICALL
Java_com_tutpro_baresip_UserAgent_00024Companion_ua_1isregistered(JNIEnv *env, jobject thiz,
                                                                  jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    return ua_isregistered(ua);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_UserAgent_00024Companion_ua_1update_1account(JNIEnv *env, jobject thiz,
                                                                    jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    return ua_update_account(ua);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_UserAgent_00024Companion_ua_1destroy(JNIEnv *env, jobject thiz,
                                                             jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    mem_deref(ua);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_UserAgentKt_ua_1account(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    struct account *acc;
    char acc_buf[64];
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    acc_buf[0] = '\0';
    if (ua) {
        acc = ua_account(ua);
        if (acc) sprintf(acc_buf, "%lu", (unsigned long) acc);
    }
    return (*env)->NewStringUTF(env, acc_buf);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_UserAgentKt_ua_1aor(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    if (ua)
        return (*env)->NewStringUTF(env, ua_aor(ua));
    else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_MainActivity_uag_1current_1set(JNIEnv *env, jobject thiz,
                                                       jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    LOGD("running uag_current_set on %s\n", native_ua);
    uag_current_set(ua);
    return;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_call_1peeruri(JNIEnv *env, jobject thiz, jstring javaCall)
{
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call;
    call = (struct call *)strtoul(native_call, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    return (*env)->NewStringUTF(env, call_peeruri(call));
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1connect(JNIEnv *env, jobject thiz,
                                                jstring javaUA, jstring javaURI) {
    struct call *call;
    struct ua *ua;
    int err;
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_uri = (*env)->GetStringUTFChars(env, javaURI, 0);
    char call_buf[64];
    LOGD("connecting ua %s to %s\n", native_ua, native_uri);
    ua = (struct ua *)strtoul(native_ua, NULL, 10);
    re_thread_enter();
    err = ua_connect(ua, &call, NULL, native_uri, VIDMODE_OFF);
    re_thread_leave();
    if (err) {
        LOGW("connecting to %s failed with error %d\n", native_uri, err);
        call_buf[0] = '\0';
    } else {
        sprintf(call_buf, "%lu", (unsigned long)call);
    }
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaURI, native_uri);
    return (*env)->NewStringUTF(env, call_buf);
}


JNIEXPORT void JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1answer(JNIEnv *env, jobject thiz,
                                                jstring javaUA, jstring javaCall) {
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    LOGD("answering call %s/%s\n", native_ua, native_call);
    struct ua *ua = (struct ua *) strtoul(native_ua, NULL, 10);
    struct call *call = (struct call *) strtoul(native_call, NULL, 10);
    play = mem_deref(play);
    re_thread_enter();
    ua_answer(ua, call);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    return;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_MainActivity_ua_1hold_1answer(JNIEnv *env, jobject thiz,
                                                      jstring javaUA, jstring javaCall) {
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    int ret;
    LOGD("answering call %s/%s\n", native_ua, native_call);
    struct ua *ua = (struct ua *) strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    struct call *call;
    if (strlen(native_call) > 0)
        call = (struct call *) strtoul(native_call, NULL, 10);
    else
        call = NULL;
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    play = mem_deref(play);
    audio_set_source(call_audio(ua_call(uag_current())), NULL, NULL);
    re_thread_enter();
    call_hold(ua_call(uag_current()), true);
    ua_answer(ua, call);
    ret = ua_hold_answer(ua, call);
    re_thread_leave();
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_MainActivity_call_1hold(JNIEnv *env, jobject thiz,
                                                jstring javaCall) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *) strtoul(native_call, NULL, 10);
    int ret;
    LOGD("holding call %s\n", native_call);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    struct audio *au = call_audio(call);
    audio_set_hold(au, true);
    audio_set_source(au, NULL, NULL);
    re_thread_enter();
    ret = call_hold(call, true);
    re_thread_leave();
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_MainActivity_call_1unhold(JNIEnv *env, jobject thiz,
                                                  jstring javaCall) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *) strtoul(native_call, NULL, 10);
    int ret;
    LOGD("unholding call %s\n", native_call);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    struct audio *au = call_audio(call);
    audio_set_hold(au, false);
    audio_set_source(au, "opensles", "nil");
    re_thread_enter();
    ret = call_hold(call, false);
    re_thread_leave();
    return ret;
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
    re_thread_enter();
    if (strlen(native_reason) == 0)
        ua_hangup(ua, call, native_code, NULL);
    else
        ua_hangup(ua, call, native_code, native_reason);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    (*env)->ReleaseStringUTFChars(env, reason, native_reason);
    return;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_MainActivity_call_1send_1digit(JNIEnv *env, jobject thiz,
                                                  jstring javaCall, jchar digit) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    const uint16_t native_digit = digit;
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    LOGD("sending DTMF digit '%c' to call %s\n", (char)native_digit, native_call);
    int res = call_send_digit(call, (char)native_digit);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    return res;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_MessageActivity_message_1send(JNIEnv *env, jobject thiz, jstring javaUA,
                                                      jstring javaPeer, jstring javaMsg,
                                                      jstring javaTime) {
    struct ua *ua;
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_peer = (*env)->GetStringUTFChars(env, javaPeer, 0);
    const char *native_msg = (*env)->GetStringUTFChars(env, javaMsg, 0);
    const char *native_time = (*env)->GetStringUTFChars(env, javaTime, 0);
    LOGD("sending message from ua %s to %s at %s\n", native_ua, native_peer, native_time);
    ua = (struct ua *)strtoul(native_ua, NULL, 10);
    int err = message_send(ua, native_peer, native_msg, send_resp_handler, (void *)native_time);
    if (err) {
        LOGW("message_send failed with error %d\n", err);
    }
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaPeer, native_peer);
    (*env)->ReleaseStringUTFChars(env, javaMsg, native_msg);
    return err;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_ContactsActivity_00024Companion_contacts_1remove(JNIEnv *env, jobject thiz) {
    struct le *le;
    le = list_head(contact_list(baresip_contacts()));
    while ((le = list_head(contact_list(baresip_contacts())))) {
        struct contact *c = le->data;
        contact_remove(baresip_contacts(), c);
    }
    return;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_ContactsActivity_00024Companion_contact_1add(JNIEnv *env, jobject thiz,
                                                                 jstring javaContact) {
    struct pl pl_addr;
    const struct list *lst;
    struct le *le;
    const char *native_contact = (*env)->GetStringUTFChars(env, javaContact, 0);
    pl_set_str(&pl_addr, native_contact);
    if (contact_add(baresip_contacts(), NULL, &pl_addr) != 0) {
        LOGE("failed to add contact %s\n", native_contact);
    } else {
        LOGD("added contact %s\n", native_contact);
    }
    return;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_MainActivity_reload_1config(JNIEnv *env, jobject thiz) {
    int err;
    err = conf_configure();
    if (err) {
        LOGE("failed to reload config %d\n", err);
    } else {
        LOGD("config reload succeeded");
    }
    return err;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_cmd_1exec(JNIEnv *env, jobject thiz, jstring javaCmd) {
    const char *native_cmd = (*env)->GetStringUTFChars(env, javaCmd, 0);
    LOGD("processing command '%s'\n", native_cmd);
    if (strcmp(native_cmd, "audio_debug") == 0) {
        re_printf("Baresip audio debug '%H\n", audio_debug, call_audio(ua_call(uag_current())));
    }
    int res = cmd_process_long(baresip_commands(), native_cmd, strlen(native_cmd), &pf_null, NULL);
    (*env)->ReleaseStringUTFChars(env, javaCmd, native_cmd);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_audio_1codecs(JNIEnv *env, jobject thiz)
{
    struct list *aucodecl = baresip_aucodecl();
    struct le *le;
    char codec_buf[256];
    char *start = &(codec_buf[0]);
    unsigned int left = sizeof codec_buf;
    int len;
    for (le = list_head(aucodecl); le != NULL; le = le->next) {
        const struct aucodec *ac = le->data;
        if (start == &(codec_buf[0]))
            len = re_snprintf(start, left, "%s/%u/%u", ac->name, ac->srate, ac->ch);
        else
            len = re_snprintf(start, left, ",%s/%u/%u", ac->name, ac->srate, ac->ch);
        if (len == -1) {
            LOGE("failed to print codec to buffer\n");
            codec_buf[0] = '\0';
            break;
        }
        start = start + len;
        left = left - len;
    }
    return (*env)->NewStringUTF(env, codec_buf);
}

