#include <string.h>
#include <pthread.h>
#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <re.h>
#include <baresip.h>

#define LOGD(...) \
    if (log_level_get() < LEVEL_INFO) ((void)__android_log_print(ANDROID_LOG_DEBUG, "Baresip Lib", __VA_ARGS__))

#define LOGI(...) \
    if (log_level_get() < LEVEL_WARN) ((void)__android_log_print(ANDROID_LOG_DEBUG, "Baresip Lib", __VA_ARGS__))

#define LOGW(...) \
    if (log_level_get() < LEVEL_ERROR) ((void)__android_log_print(ANDROID_LOG_DEBUG, "Baresip Lib", __VA_ARGS__))

#define LOGE(...) \
    if (log_level_get() <= LEVEL_ERROR) ((void)__android_log_print(ANDROID_LOG_DEBUG, "Baresip Lib", __VA_ARGS__))


typedef struct baresip_context {
    JavaVM  *javaVM;
    JNIEnv  *env;
    jclass  mainActivityClz;
    jobject mainActivityObj;
} BaresipContext;

BaresipContext g_ctx;

struct play *play = NULL;

static int vprintf_null(const char *p, size_t size, void *arg) {
    (void)p;
    (void)size;
    (void)arg;
    return 0;
}

static void net_debug_log() {
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", net_debug, baresip_network());
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void net_dns_debug_log() {
    char debug_buf[2048];
    int l;
    LOGD("net_dns_debug_log\n");
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", net_dns_debug, baresip_network());
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void ua_debug_log(struct ua *ua) {
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", ua_debug, ua);
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void account_debug_log(struct account *acc) {
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", account_debug, acc);
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void ua_print_status_log(struct ua *ua) {
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", ua_print_status, ua);
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
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
        case UA_EVENT_REGISTERING:
            return "registering";
        case UA_EVENT_REGISTER_OK:
        case UA_EVENT_FALLBACK_OK:
            return "registered";
        case UA_EVENT_REGISTER_FAIL:
        case UA_EVENT_FALLBACK_FAIL:
            return "registering failed";
        case UA_EVENT_UNREGISTERING:
            return "unregistering";
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

static void get_password(char *aor, char *password)
{
    LOGD("getting password of AoR %s\n", aor);

    JavaVM *javaVM = g_ctx.javaVM;
    JNIEnv *env;
    jint res = (*javaVM)->GetEnv(javaVM, (void**)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        if (JNI_OK != res) {
            LOGE("failed to AttachCurrentThread, ErrorCode = %d\n", res);
            return;
        }
    }

    jmethodID methodId = (*env)->GetMethodID(env, g_ctx.mainActivityClz, "getPassword",
                                                 "(Ljava/lang/String;)Ljava/lang/String;");
    jstring javaAoR = (*env)->NewStringUTF(env, aor);
    jstring javaPassword = (*env)->CallObjectMethod(env, g_ctx.mainActivityObj, methodId, javaAoR);
    const char *pwd = (*env)->GetStringUTFChars(env, javaPassword, 0);
    strcpy(password, pwd);
    (*env)->ReleaseStringUTFChars(env, javaPassword, pwd);
}

static void ua_event_handler(struct ua *ua, enum ua_event ev,
			     struct call *call, const char *prm, void *arg)
{
    (void)arg;
    const char *event;
    char event_buf[256];
    char ua_buf[32];
    char call_buf[32];
    int len;

    struct player *player = baresip_player();

    LOGD("ua event (%s) %s\n", uag_event_str(ev), prm);

    switch (ev) {
        case UA_EVENT_REGISTERING:
        case UA_EVENT_UNREGISTERING:
        case UA_EVENT_REGISTER_OK:
        case UA_EVENT_FALLBACK_OK:
            len = re_snprintf(event_buf, sizeof event_buf, "%s", ua_event_reg_str(ev));
            break;
        case UA_EVENT_REGISTER_FAIL:
        case UA_EVENT_FALLBACK_FAIL:
            len = re_snprintf(event_buf, sizeof event_buf, "registering failed,%s", prm);
            break;
        case UA_EVENT_CALL_INCOMING:
            //if (list_count(ua_calls(ua)) > 1) {
            //    play = mem_deref(play);
            //    (void)play_file(&play, player, "callwaiting.wav", 3, NULL, NULL);
            //}
            len = re_snprintf(event_buf, sizeof event_buf, "%s", "call incoming");
            break;
        case UA_EVENT_CALL_LOCAL_SDP:
            if (strcmp(prm, "offer") != 0)
                len = re_snprintf(event_buf, sizeof event_buf, "%s", "call offered");
            else
                len = re_snprintf(event_buf, sizeof event_buf, "%s", "call answered");
            break;
        case UA_EVENT_CALL_RINGING:
            play = mem_deref(play);
            (void)play_file(&play, player, "ringback.wav", -1, NULL, NULL);
            len = re_snprintf(event_buf, sizeof event_buf, "%s", "call ringing");
            break;
        case UA_EVENT_CALL_PROGRESS:
            len = re_snprintf(event_buf, sizeof event_buf, "%s", "call progress");
            break;
        case UA_EVENT_CALL_ESTABLISHED:
            play = mem_deref(play);
            len = re_snprintf(event_buf, sizeof event_buf, "%s", "call established");
            break;
        case UA_EVENT_CALL_MENC:
            if (prm[0] == '0')
                len = re_snprintf(event_buf, sizeof event_buf, "call secure");
            else if (prm[0] == '1')
                len = re_snprintf(event_buf, sizeof event_buf, "call verify,%s", prm+2);
            else if (prm[0] == '2')
                len = re_snprintf(event_buf, sizeof event_buf, "call verified,%s", prm+2);
            else
                len = re_snprintf(event_buf, sizeof event_buf, "%s", "unknown menc event");
            break;
        case UA_EVENT_CALL_TRANSFER:
            len = re_snprintf(event_buf, sizeof event_buf, "call transfer,%s", prm);
            break;
        case UA_EVENT_CALL_TRANSFER_FAILED:
            len = re_snprintf(event_buf, sizeof event_buf, "refer failed,%s", prm);
            break;
        case UA_EVENT_CALL_CLOSED:
            play = mem_deref(play);
            if (call_scode(call)) {
			    const char *tone;
			    tone = translate_errorcode(call_scode(call));
			    if (tone) {
				    (void)play_file(&play, player, tone, 1, NULL, NULL);
			    }
		    }
            len = re_snprintf(event_buf, sizeof event_buf, "call closed,%s", prm);
            break;
        case UA_EVENT_MWI_NOTIFY:
            len = re_snprintf(event_buf, sizeof event_buf, "mwi notify,%s", prm);
            break;
        case UA_EVENT_CUSTOM:
            get_password((char *)ua, (char *)call);
            return;
        default:
            return;
    }

    if (len == -1) {
        LOGE("failed to print event to buffer\n");
        return;
    }

    event = event_buf;

    JavaVM *javaVM = g_ctx.javaVM;
    JNIEnv *env;
    jint res = (*javaVM)->GetEnv(javaVM, (void**)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        if (JNI_OK != res) {
            LOGE("failed to AttachCurrentThread, ErrorCode = %d\n", res);
            return;
        }
    }

    jmethodID methodId = (*env)->GetMethodID(env, g_ctx.mainActivityClz,
                                             "uaEvent",
                                             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    sprintf(ua_buf, "%lu", (unsigned long)ua);
    jstring javaUA = (*env)->NewStringUTF(env, ua_buf);
    sprintf(call_buf, "%lu", (unsigned long)call);
    jstring javaCall = (*env)->NewStringUTF(env, call_buf);
    jstring javaEvent = (*env)->NewStringUTF(env, event);
    LOGD("sending ua/call %s/%s event %s\n", ua_buf, call_buf, event);
    (*env)->CallVoidMethod(env, g_ctx.mainActivityObj, methodId, javaEvent, javaUA, javaCall);
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

    LOGD("got message '%.*s' from peer '%.*s'", (int)mbuf_get_left(body), mbuf_buf(body),
         (int)peer->l, peer->p);
    if (snprintf(peer_buf, 256, "%.*s", (int)peer->l, peer->p) >= 256) {
        LOGE("message peer is too long (max 255 charcaters)\n");
        return;
    }
    BaresipContext *pctx = (BaresipContext*)(&g_ctx);
    JavaVM *javaVM = pctx->javaVM;
    JNIEnv *env;
    jint res = (*javaVM)->GetEnv(javaVM, (void**)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        if (JNI_OK != res) {
            LOGE("failed to AttachCurrentThread, ErrorCode = %d\n", res);
            return;
        }
    }
    jmethodID methodId = (*env)->GetMethodID(env, pctx->mainActivityClz,
                                             "messageEvent",
                                             "(Ljava/lang/String;Ljava/lang/String;[B)V");
    sprintf(ua_buf, "%lu", (unsigned long)ua);
    jstring javaUA = (*env)->NewStringUTF(env, ua_buf);
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
    LOGD("sending message %s/%s/%.*s\n", ua_buf, peer_buf, (int)size, mbuf_buf(body));
    (*env)->CallVoidMethod(env, pctx->mainActivityObj, methodId, javaUA, javaPeer, javaMsg);
    (*env)->DeleteLocalRef(env, javaUA);
    (*env)->DeleteLocalRef(env, javaPeer);
    (*env)->DeleteLocalRef(env, javaMsg);
}

static void send_resp_handler(int err, const struct sip_msg *msg, void *arg)
{
    (void)arg;
    char reason_buf[64];

    if (err) {
        LOGD("send_response_handler received error %d\n", err);
        return;
    }

    pl_strcpy(&(msg->reason), reason_buf, 64);
    LOGD("send_response_handler received response '%u %s' at %s\n", msg->scode,
            reason_buf, (char *)arg);

    BaresipContext *pctx = (BaresipContext*)(&g_ctx);
    JavaVM *javaVM = pctx->javaVM;
    JNIEnv *env;
    jint res = (*javaVM)->GetEnv(javaVM, (void**)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        if (JNI_OK != res) {
            LOGE("failed to AttachCurrentThread, ErrorCode = %d\n", res);
            return;
        }
    }
    jmethodID methodId = (*env)->GetMethodID(env, pctx->mainActivityClz,
                                             "messageResponse",
                                             "(ILjava/lang/String;Ljava/lang/String;)V");
    jstring javaReason = (*env)->NewStringUTF(env, reason_buf);
    jstring javaTime = (*env)->NewStringUTF(env, (char *)arg);
    (*env)->CallVoidMethod(env, pctx->mainActivityObj, methodId, msg->scode, javaReason, javaTime);
    (*env)->DeleteLocalRef(env, javaReason);
    (*env)->DeleteLocalRef(env, javaTime);
}

enum {
    ID_UA_STOP_ALL
};

static struct mqueue *mq;

static void mqueue_handler(int id, void *data, void *arg)
{
    if (id == ID_UA_STOP_ALL) {
        LOGD("calling ua_stop_all with force %u\n", (unsigned)data);
        ua_stop_all((bool)data);
    }
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

    int ret = pthread_create(&loggingThread, 0, loggingFunction, 0);
    if (ret != 0)
        return ret;

    pthread_detach(loggingThread);

    return 0;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {

    LOGD("at JNI_OnLoad\n");

    memset(&g_ctx, 0, sizeof(g_ctx));

    g_ctx.javaVM = vm;
    g_ctx.env = NULL;
    g_ctx.mainActivityClz = NULL;
    g_ctx.mainActivityObj = NULL;

    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_net_1set_1address(JNIEnv *env, jobject thiz, jstring javaIp);

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_net_1use_1nameserver(JNIEnv *env, jobject thiz, jstring javaServers);

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_BaresipService_baresipStart(JNIEnv *env, jobject instance,
        jstring jPath, jstring jIpV4Addr, jstring jIpV6Addr, jstring jNetInterface,
        jstring jDnsServers, jint jNetAf, jint javaLogLevel) {

    LOGD("starting baresip\n");

    struct sa temp_sa;
    const char *net_interface = (*env)->GetStringUTFChars(env, jNetInterface, 0);
    const int net_af = jNetAf;

    char start_error[64] = "";

    JavaVM *javaVM = g_ctx.javaVM;
    jint res = (*javaVM)->GetEnv(javaVM, (void **) &env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        LOGE("failed to GetEnv, ErrorCode = %d", res);
        goto stopped;
    }
    jclass clz = (*env)->GetObjectClass(env, instance);
    g_ctx.mainActivityClz = (*env)->NewGlobalRef(env, clz);
    g_ctx.mainActivityObj = (*env)->NewGlobalRef(env, instance);
    g_ctx.env = env;

    int err;
    const char *path = (*env)->GetStringUTFChars(env, jPath, 0);
    struct le *le;

    runLoggingThread();

    err = libre_init();
    if (err)
        goto out;

    conf_path_set(path);

    log_level_set((enum log_level)javaLogLevel);

    err = conf_configure();
    if (err) {
        LOGW("conf_configure() failed: (%d)\n", err);
        strcpy(start_error, "conf_configure");
        goto out;
    }

    if (strlen(net_interface) > 0) {
        struct config *theconf = conf_config();
        str_ncpy(theconf->net.ifname, net_interface,
             sizeof(theconf->net.ifname));
    }

    if (net_af != AF_UNSPEC)
        conf_config()->net.af = net_af;

    err = baresip_init(conf_config());
    if (err) {
        LOGW("baresip_init() failed (%d)\n", err);
        strcpy(start_error, "baresip_init");
        goto out;
    }

    Java_com_tutpro_baresip_Api_net_1set_1address(env, instance, jIpV4Addr);
    Java_com_tutpro_baresip_Api_net_1set_1address(env, instance, jIpV6Addr);
    Java_com_tutpro_baresip_Api_net_1use_1nameserver(env, instance, jDnsServers);

    net_debug_log();

    play_set_path(baresip_player(), path);

    err = ua_init("baresip v" BARESIP_VERSION " (" ARCH "/" OS ")",
                  true, true, true);
    if (err) {
        LOGE("ua_init() failed (%d)\n", err);
        strcpy(start_error, "ua_init");
        goto out;
    }

    uag_set_exit_handler(ua_exit_handler, NULL);

    err = uag_event_register(ua_event_handler, NULL);
    if (err) {
        LOGE("uag_event_register() failed (%d)\n", err);
        strcpy(start_error, "uag_event_register");
        goto out;
    }

    err = message_listen(baresip_message(), message_handler, NULL);
    if (err) {
        LOGE("message_listen() failed (%d)\n", err);
        strcpy(start_error, "message_listen");
        goto out;
    }

    err = conf_modules();
    if (err) {
        LOGW("conf_modules() failed (%d)\n", err);
        strcpy(start_error, "conf_modules");
        goto out;
    }

    char ua_buf[32];
    struct ua *ua;
    for (le = list_head(uag_list()); le != NULL; le = le->next) {
        ua = le->data;
        sprintf(ua_buf, "%lu", (unsigned long) ua);
        jstring javaUA = (*env)->NewStringUTF(env, ua_buf);
        LOGD("adding UA for AoR %s/%s\n", account_aor(ua_account(ua)), ua_buf);
        jmethodID uaAddId = (*env)->GetMethodID(env, g_ctx.mainActivityClz, "uaAdd",
                                                  "(Ljava/lang/String;)V");
        (*env)->CallVoidMethod(env, g_ctx.mainActivityObj, uaAddId, javaUA);
        (*env)->DeleteLocalRef(env, javaUA);
    }

    LOGI("allocating mqeue\n");
    err = mqueue_alloc(&mq, mqueue_handler, NULL);
    if (err) {
        LOGW("mqueue_alloc failed (%d)\n", err);
        strcpy(start_error, "mqueue_alloc");
        goto out;
    }

    for (le = list_head(uag_list()); le != NULL; le = le->next)
        ua_print_status_log(le->data);

    jmethodID startedId = (*env)->GetMethodID(env, g_ctx.mainActivityClz, "started", "()V");
    (*env)->CallVoidMethod(env, g_ctx.mainActivityObj, startedId);

    LOGI("running main loop ...\n");
    err = re_main(signal_handler);

    out:

    if (err) {
        LOGE("stopping UAs due to error: (%d)\n", err);
        ua_stop_all(true);
    } else {
        LOGI("main loop exit\n");
    }

    play = mem_deref(play);
    mq = mem_deref(mq);

    LOGD("closing ...");
    ua_close();
    module_app_unload();
    conf_close();
    baresip_close();

    uag_event_unregister(ua_event_handler);

    LOGD("unloading modules ...");
    mod_close();

    libre_close();

    // tmr_debug();
    // mem_debug();

    stopped:

    LOGD("tell main that baresip has stopped");
    jstring javaError = (*env)->NewStringUTF(env, start_error);
    jmethodID stoppedId = (*env)->GetMethodID(env, g_ctx.mainActivityClz, "stopped",
            "(Ljava/lang/String;)V");
    (*env)->CallVoidMethod(env, g_ctx.mainActivityObj, stoppedId, javaError);
    (*env)->DeleteLocalRef(env, javaError);

}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_BaresipService_baresipStop(JNIEnv *env, jobject thiz, jboolean force) {
    LOGD("ua_stop_all upon baresipStop");
    mqueue_push(mq, ID_UA_STOP_ALL, (void *)((long)force));
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_account_1display_1name(JNIEnv *env, jobject thiz, jstring javaAcc) {
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
Java_com_tutpro_baresip_Api_account_1set_1display_1name(JNIEnv *env, jobject thiz,
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
Java_com_tutpro_baresip_Api_account_1aor(JNIEnv *env, jobject thiz, jstring javaAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc)
        return (*env)->NewStringUTF(env, account_aor(acc));
    else
        return (*env)->NewStringUTF(env, "");
}


JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_account_1luri(JNIEnv *env, jclass clazz, jstring javaAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const struct uri *uri = account_luri(acc);
    char uri_buf[512];
    int l;
    l = re_snprintf(&(uri_buf[0]), 511, "%H", uri_encode, uri);
    if (l != -1)
        uri_buf[l] = '\0';
    else
        uri_buf[0] = '\0';
    return (*env)->NewStringUTF(env, uri_buf);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_account_1auth_1user(JNIEnv *env, jobject thiz, jstring javaAcc) {
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
Java_com_tutpro_baresip_Api_account_1set_1auth_1user(JNIEnv *env, jobject thiz,
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
Java_com_tutpro_baresip_Api_account_1auth_1pass(JNIEnv *env, jobject thiz, jstring javaAcc) {
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
Java_com_tutpro_baresip_Api_account_1set_1auth_1pass(JNIEnv *env, jobject thiz,
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
Java_com_tutpro_baresip_Api_account_1outbound(JNIEnv *env, jobject thiz, jstring javaAcc,
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

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1outbound(JNIEnv *env, jobject thiz, jstring javaAcc,
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
Java_com_tutpro_baresip_Api_account_1audio_1codec(JNIEnv *env, jobject thiz,
        jstring javaAcc, jint ix) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const struct list *codecl;
    char codec_buf[32];
    int len;
    struct le *le;
    codec_buf[0] = '\0';
    if (acc) {
        codecl = account_aucodecl(acc);
        if (!list_isempty(codecl)) {
            int i = -1;
            for (le = list_head(codecl); le != NULL; le = le->next) {
                i++;
                if (i == ix) {
                    const struct aucodec *ac = le->data;
                    len = re_snprintf(codec_buf, sizeof codec_buf, "%s/%u/%u", ac->name, ac->srate, ac->ch);
                    if (len == -1) {
                        LOGE("failed to print audio codec to buffer\n");
                        codec_buf[0] = '\0';
                    }
                    break;
                }
            }
        }
    }
    return (*env)->NewStringUTF(env, codec_buf);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1audio_1codecs(JNIEnv *env, jobject thiz,
                                                             jstring javaAcc, jstring javaCodecs) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *codecs = (*env)->GetStringUTFChars(env, javaCodecs, 0);
    int res = account_set_audio_codecs(acc, codecs);
    (*env)->ReleaseStringUTFChars(env, javaCodecs, codecs);
    return res;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1video_1codecs(JNIEnv *env, jobject thiz,
                                                              jstring jAcc, jstring jCodecs) {
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    const char *codecs = (*env)->GetStringUTFChars(env, jCodecs, 0);
    int res = account_set_video_codecs(acc, codecs);
    (*env)->ReleaseStringUTFChars(env, jCodecs, codecs);
    return res;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1regint(JNIEnv *env, jobject thiz, jstring javaAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc)
        return account_regint(acc);
    else
        return 0;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1regint(JNIEnv *env, jobject thiz,
                                                           jstring javaAcc, jint javaRegint) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const uint32_t regint = (uint32_t)javaRegint;
    return account_set_regint(acc, regint);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_account_1mediaenc(JNIEnv *env, jobject thiz, jstring javaAcc)
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
Java_com_tutpro_baresip_Api_account_1set_1mediaenc(JNIEnv *env, jobject thiz,
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
Java_com_tutpro_baresip_Api_account_1medianat(JNIEnv *env, jobject thiz, jstring javaAcc)
{
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc) {
        const char *medianat = account_medianat(acc);
        if (medianat) return (*env)->NewStringUTF(env, medianat);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1medianat(JNIEnv *env, jobject thiz,
                                                         jstring javaAcc, jstring javaMedNat) {
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    const char *mednat = (*env)->GetStringUTFChars(env, javaMedNat, 0);
    int res;
    if (strlen(mednat) > 0)
        res = account_set_medianat(acc, mednat);
    else
        res = account_set_medianat(acc, NULL);
    (*env)->ReleaseStringUTFChars(env, javaMedNat, mednat);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_account_1sipnat(JNIEnv *env, jobject thiz, jstring javaAcc)
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
Java_com_tutpro_baresip_Api_account_1set_1sipnat(JNIEnv *env, jobject thiz,
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

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_account_1stun_1uri(JNIEnv *env, jobject thiz, jstring jAcc)
{
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    if (acc) {
        const struct stun_uri *stun_uri = account_stun_uri(acc);
        if (stun_uri) {
            char uri_str[256];
            if (stun_uri->port != 0)
                sprintf(uri_str, "%s:%s:%d", stunuri_scheme_name(stun_uri->scheme),
                        stun_uri->host, stun_uri->port);
            else
                sprintf(uri_str, "%s:%s", stunuri_scheme_name(stun_uri->scheme),
                        stun_uri->host);
            return (*env)->NewStringUTF(env, uri_str);
        }
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1stun_1uri(JNIEnv *env, jobject thiz, jstring jAcc, jstring jUri) {
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    const char *uri = (*env)->GetStringUTFChars(env, jUri, 0);
    int res;
    if (strlen(uri) > 0)
        res = account_set_stun_uri(acc, uri);
    else
        res = account_set_stun_uri(acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jUri, uri);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_account_1stun_1user(JNIEnv *env, jobject thiz, jstring jAcc)
{
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    if (acc) {
        const char *stun_user = account_stun_user(acc);
        if (stun_user) return (*env)->NewStringUTF(env, stun_user);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1stun_1user(JNIEnv *env, jobject thiz,
                                                           jstring jAcc, jstring jUser) {
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    const char *user = (*env)->GetStringUTFChars(env, jUser, 0);
    int res;
    if (strlen(user) > 0)
        res = account_set_stun_user(acc, user);
    else
        res = account_set_stun_user(acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jUser, user);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_account_1stun_1pass(JNIEnv *env, jobject thiz, jstring jAcc)
{
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    if (acc) {
        const char *stun_pass = account_stun_pass(acc);
        if (stun_pass) return (*env)->NewStringUTF(env, stun_pass);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1stun_1pass(JNIEnv *env, jobject thiz,
                                                           jstring jAcc, jstring jPass) {
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    const char *pass = (*env)->GetStringUTFChars(env, jPass, 0);
    int res;
    if (strlen(pass) > 0)
        res = account_set_stun_pass(acc, pass);
    else
        res = account_set_stun_pass(acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jPass, pass);
    return res;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1mwi(JNIEnv *env, jobject thiz,
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
Java_com_tutpro_baresip_Api_account_1vm_1uri(JNIEnv *env, jobject thiz, jstring javaAcc) {
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

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1answermode(JNIEnv *env, jobject thiz, jstring jAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    return account_answermode(acc);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1answermode(JNIEnv *env, jobject thiz,
        jstring jAcc, jint jMode) {
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    const uint32_t mode = (uint32_t)jMode;
    return account_set_answermode(acc, mode);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1dtmfmode(JNIEnv *env, jobject thiz, jstring jAcc) {
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    return account_dtmfmode(acc);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_account_1set_1dtmfmode(JNIEnv *env, jobject thiz,
        jstring jAcc, jint jMode) {
    const char *native_acc = (*env)->GetStringUTFChars(env, jAcc, 0);
    struct account *acc = (struct account *)strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, jAcc, native_acc);
    const uint32_t mode = (uint32_t)jMode;
    return account_set_dtmfmode(acc, mode);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_account_1extra(JNIEnv *env, jobject thiz, jstring javaAcc)
{
    const char *native_acc = (*env)->GetStringUTFChars(env, javaAcc, 0);
    struct account *acc = (struct account *) strtoul(native_acc, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaAcc, native_acc);
    if (acc) {
        const char *extra = account_extra(acc);
        if (extra)
            return (*env)->NewStringUTF(env, extra);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_account_1debug(JNIEnv *env, jobject thiz, jstring javaAccount) {
    const char *native_account = (*env)->GetStringUTFChars(env, javaAccount, 0);
    struct account *acc = (struct account *)strtoul(native_account, NULL, 10);
    account_debug_log(acc);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_ua_1alloc(JNIEnv *env, jobject thiz, jstring javaUri) {
    const char *uri = (*env)->GetStringUTFChars(env, javaUri, 0);
    struct ua *ua;
    LOGD("allocating UA '%s'\n", uri);
    int res = ua_alloc(&ua, uri);
    char ua_buf[32];
    ua_buf[0] = '\0';
    if (res == 0) {
        sprintf(ua_buf, "%lu", (unsigned long)ua);
        LOGD("allocated ua '%lu'\n", (unsigned long)ua);
    } else {
        LOGE("failed to allocate ua '%s'\n", uri);
    }
    (*env)->ReleaseStringUTFChars(env, javaUri, uri);
    return (*env)->NewStringUTF(env, ua_buf);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_ua_1register(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    LOGD("registering UA '%s'\n", native_ua);
    return ua_register(ua);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_ua_1unregister(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    ua_unregister(ua);
}

JNIEXPORT jboolean JNICALL
Java_com_tutpro_baresip_Api_ua_1isregistered(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    return ua_isregistered(ua)?true:false;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_ua_1update_1account(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    LOGD("updating account of ua %s\n", native_ua);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    return ua_update_account(ua);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_ua_1destroy(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    LOGD("destroying ua %s\n", native_ua);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (void)ua_destroy(ua);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_ua_1account(JNIEnv *env, jobject thiz, jstring javaUA)
{
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    struct account *acc;
    char acc_buf[32];
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    acc_buf[0] = '\0';
    if (ua) {
        acc = ua_account(ua);
        if (acc) sprintf(acc_buf, "%lu", (unsigned long) acc);
    }
    return (*env)->NewStringUTF(env, acc_buf);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_ua_1hangup(JNIEnv *env, jobject thiz,
                                                jstring javaUA, jstring javaCall, jint code,
                                                jstring reason) {
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    const uint16_t native_code = code;
    const char *native_reason = (*env)->GetStringUTFChars(env, reason, 0);
    LOGD("hanging up call %s/%s\n", native_ua, native_call);
    if (strlen(native_reason) == 0)
        ua_hangup(ua, call, native_code, NULL);
    else
        ua_hangup(ua, call, native_code, native_reason);
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    (*env)->ReleaseStringUTFChars(env, reason, native_reason);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_ua_1connect(JNIEnv *env, jobject thiz, jstring javaUA, jstring javaURI, jint javaVidMode) {
    struct call *call;
    struct ua *ua;
    int err;
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_uri = (*env)->GetStringUTFChars(env, javaURI, 0);
    char call_buf[32];
    LOGD("connecting ua %s to %s %s video\n", native_ua, native_uri,
            javaVidMode == 0 ? "without" : "with");
    ua = (struct ua *)strtoul(native_ua, NULL, 10);
    re_thread_enter();
    err = ua_connect(ua, &call, NULL, native_uri, (enum vidmode)javaVidMode);
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

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_ua_1call_1alloc(JNIEnv *env, jobject thiz, jstring javaUA,
        jstring javaXCall, jint javaVidMode) {
    struct call *xcall, *call = NULL;
    struct ua *ua;
    int err;
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_xcall = (*env)->GetStringUTFChars(env, javaXCall, 0);
    char call_buf[32];
    LOGD("allocating new call for ua %s xcall %s\n", native_ua, native_xcall);
    ua = (struct ua *)strtoul(native_ua, NULL, 10);
    xcall = (struct call *)strtoul(native_xcall, NULL, 10);
    re_thread_enter();
    err = ua_call_alloc(&call, ua, (enum vidmode)javaVidMode, NULL, xcall, call_localuri(xcall), true);
    if (err) {
        LOGW("call allocation for ua %s failed with error %d\n", native_ua, err);
        call_buf[0] = '\0';
    } else {
        sprintf(call_buf, "%lu", (unsigned long)call);
    }
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaXCall, native_xcall);
    return (*env)->NewStringUTF(env, call_buf);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_ua_1answer(JNIEnv *env, jobject thiz, jstring javaUA,
                                       jstring javaCall, jint javaVidMode) {
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    LOGD("answering call %s/%s\n", native_ua, native_call);
    struct ua *ua = (struct ua *) strtoul(native_ua, NULL, 10);
    struct call *call = (struct call *) strtoul(native_call, NULL, 10);
    play = mem_deref(play);
    re_thread_enter();
    ua_answer(ua, call, (enum vidmode)javaVidMode);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_ua_1set_1media_1af(JNIEnv *env, jobject thiz, jstring javaUA,
                                               jint javaAf) {
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    LOGD("setting ua media af to '%d'\n", javaAf);
    ua_set_media_af(ua, javaAf);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_ua_1debug(JNIEnv *env, jobject thiz, jstring javaUA) {
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    struct ua *ua = (struct ua *)strtoul(native_ua, NULL, 10);
    ua_debug_log(ua);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Call_call_1connect(JNIEnv *env, jobject thiz, jstring javaCall,
                                          jstring javaPeer) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    const char *native_peer = (*env)->GetStringUTFChars(env, javaPeer, 0);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    LOGD("connecting call %s to %s\n", native_call, native_peer);
    re_thread_enter();
    struct pl pl;
    pl_set_str(&pl, native_peer);
    int err = call_connect(call, &pl);
    re_thread_leave();
    if (err) LOGW("call_connect error: %d\n", err);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    (*env)->ReleaseStringUTFChars(env, javaPeer, native_peer);
    return err;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Call_call_1notify_1sipfrag(JNIEnv *env, jobject thiz, jstring javaCall,
                                                  jint code, jstring reason) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    const uint16_t native_code = code;
    const char *native_reason = (*env)->GetStringUTFChars(env, reason, 0);
    LOGD("notifying call %s/%s\n", native_call, native_reason);
    re_thread_enter();
    (void)call_notify_sipfrag(call, native_code, native_reason);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    (*env)->ReleaseStringUTFChars(env, reason, native_reason);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Call_call_1start_1audio(JNIEnv *env, jobject thiz, jstring javaCall) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    LOGD("starting audio of call %s\n", native_call);
    re_thread_enter();
    struct audio *a = call_audio(call);
    if (!audio_started(a)) audio_start(a);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Call_call_1stop_1audio(JNIEnv *env, jobject thiz, jstring javaCall) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    LOGD("stopping audio of call %s\n", native_call);
    re_thread_enter();
    struct audio *a = call_audio(call);
    if (audio_started(a)) audio_stop(a);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Call_call_1hold(JNIEnv *env, jobject thiz, jstring javaCall) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *) strtoul(native_call, NULL, 10);
    int ret;
    LOGD("holding call %s\n", native_call);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    re_thread_enter();
    struct audio *au = call_audio(call);
    //audio_set_hold(au, true);
    // audio_set_source(au, NULL, NULL);
    ret = call_hold(call, true);
    re_thread_leave();
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Call_call_1unhold(JNIEnv *env, jobject thiz, jstring javaCall) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *) strtoul(native_call, NULL, 10);
    int ret;
    LOGD("unholding call %s\n", native_call);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    re_thread_enter();
    struct audio *au = call_audio(call);
    // audio_set_hold(au, false);
    // audio_set_source(au, "opensles", "nil");
    ret = call_hold(call, false);
    re_thread_leave();
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Call_call_1transfer(JNIEnv *env, jobject thiz, jstring jCall,
        jstring jPeer) {
    const char *native_call = (*env)->GetStringUTFChars(env, jCall, 0);
    const char *native_peer = (*env)->GetStringUTFChars(env, jPeer, 0);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    LOGD("transfering call %s to %s\n", native_call, native_peer);
    re_thread_enter();
    int err = call_transfer(call, native_peer);
    re_thread_leave();
    if (err) LOGW("call_transfer error: %d\n", err);
    (*env)->ReleaseStringUTFChars(env, jCall, native_call);
    (*env)->ReleaseStringUTFChars(env, jPeer, native_peer);
    return err;
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

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Call_call_1send_1digit(JNIEnv *env, jobject thiz, jstring javaCall,
                                              jchar digit) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    const uint16_t native_digit = digit;
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    LOGD("sending DTMF digit '%c' to call %s\n", (char)native_digit, native_call);
    re_thread_enter();
    int res = call_send_digit(call, (char)native_digit);
    if (!res) res = call_send_digit(call, KEYCODE_REL);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Call_call_1audio_1codecs(JNIEnv *env, jobject thiz, jstring javaCall) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *) strtoul(native_call, NULL, 10);
    const struct aucodec *tx = audio_codec(call_audio(call), true);
    const struct aucodec *rx = audio_codec(call_audio(call), false);
    char codec_buf[256];
    char *start = &(codec_buf[0]);
    unsigned int left = sizeof codec_buf;
    int len = -1;
    if (tx && rx)
        len = re_snprintf(start, left, "%s/%u/%u,%s/%u/%u", tx->name, tx->srate, tx->ch,
                          rx->name, rx->srate, rx->ch);
    if (len == -1) {
        LOGE("failed to get audio codecs of call %s\n", native_call);
        codec_buf[0] = '\0';
    }
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    return (*env)->NewStringUTF(env, codec_buf);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Call_call_1status(JNIEnv *env, jobject thiz, jstring javaCall) {
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    char status_buf[256];
    int len = re_snprintf(&(status_buf[0]), 255, "%H", call_status, call);
    if (len == -1) {
        LOGE("failed to get status of call %s\n", native_call);
        status_buf[0] = '\0';
    }
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    return (*env)->NewStringUTF(env, status_buf);
}

JNIEXPORT jboolean JNICALL
Java_com_tutpro_baresip_Call_call_1has_1video(JNIEnv *env, jobject thiz, jstring javaCall)
{
    const char *native_call = (*env)->GetStringUTFChars(env, javaCall, 0);
    struct call *call = (struct call *)strtoul(native_call, NULL, 10);
    (*env)->ReleaseStringUTFChars(env, javaCall, native_call);
    return call_has_video(call) ? true : false;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_message_1send(JNIEnv *env, jobject thiz, jstring javaUA,
                                          jstring javaPeer, jstring javaMsg, jstring javaTime) {
    struct ua *ua;
    const char *native_ua = (*env)->GetStringUTFChars(env, javaUA, 0);
    const char *native_peer = (*env)->GetStringUTFChars(env, javaPeer, 0);
    const char *native_msg = (*env)->GetStringUTFChars(env, javaMsg, 0);
    const char *native_time = (*env)->GetStringUTFChars(env, javaTime, 0);
    LOGD("sending message from ua %s to %s at %s\n", native_ua, native_peer, native_time);
    ua = (struct ua *)strtoul(native_ua, NULL, 10);
    re_thread_enter();
    int err = message_send(ua, native_peer, native_msg, send_resp_handler, (void *)native_time);
    re_thread_leave();
    if (err) {
        LOGW("message_send failed with error %d\n", err);
    }
    (*env)->ReleaseStringUTFChars(env, javaUA, native_ua);
    (*env)->ReleaseStringUTFChars(env, javaPeer, native_peer);
    (*env)->ReleaseStringUTFChars(env, javaMsg, native_msg);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_reload_1config(JNIEnv *env, jobject thiz) {
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
            return (*env)->NewStringUTF(env, codec_buf);
        }
        start = start + len;
        left = left - len;
    }
    *start = '\0';
    return (*env)->NewStringUTF(env, codec_buf);
}

JNIEXPORT jstring JNICALL
Java_com_tutpro_baresip_Api_video_1codecs(JNIEnv *env, jobject thiz)
{
    struct list *vidcodecl = baresip_vidcodecl();
    struct le *le;
    char codec_buf[256];
    char *start = &(codec_buf[0]);
    unsigned int left = sizeof codec_buf;
    int len;
    for (le = list_head(vidcodecl); le != NULL; le = le->next) {
        const struct vidcodec *vc = le->data;
        if (start == &(codec_buf[0]))
            len = re_snprintf(start, left, "%s", vc->name);
        else
            len = re_snprintf(start, left, ",%s", vc->name);
        if (len == -1) {
            LOGE("failed to print codec to buffer\n");
            codec_buf[0] = '\0';
            return (*env)->NewStringUTF(env, codec_buf);
        }
        start = start + len;
        left = left - len;
    }
    *start = '\0';
    return (*env)->NewStringUTF(env, codec_buf);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_contact_1add(JNIEnv *env, jobject thiz, jstring javaContact) {
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
    (*env)->ReleaseStringUTFChars(env, javaContact, native_contact);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_contacts_1remove(JNIEnv *env, jobject thiz) {
    struct le *le;
    le = list_head(contact_list(baresip_contacts()));
    while ((le = list_head(contact_list(baresip_contacts())))) {
        struct contact *c = le->data;
        contact_remove(baresip_contacts(), c);
    }
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_log_1level_1set(JNIEnv *env, jobject thiz, jint level) {
    const enum log_level native_level = (enum log_level)level;
    LOGD("setting log level to '%u'\n", native_level);
    log_level_set(native_level);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_net_1use_1nameserver(JNIEnv *env, jobject thiz, jstring javaServers) {
    const char *native_servers = (*env)->GetStringUTFChars(env, javaServers, 0);
    char servers[256];
    char *server;
    struct sa nsv[NET_MAX_NS];
    uint32_t count = 0;
    char *comma;
    int err;
    LOGD("setting dns servers '%s'\n", native_servers);
    if (str_len(native_servers) > 255) {
        LOGW("net_use_nameserver: too long servers list (%s)\n", native_servers);
        return 1;
    }
    str_ncpy(servers, native_servers, 256);
    (*env)->ReleaseStringUTFChars(env, javaServers, native_servers);
    server = &(servers[0]);
    while ((count < NET_MAX_NS) && ((comma = strchr(server, ',')) != NULL)) {
        *comma = '\0';
        err = sa_decode(&(nsv[count]), server, str_len(server));
        if (err) {
            LOGW("net_use_nameserver: could not decode '%s' (%u)\n", server, err);
            return err;
        }
        server = ++comma;
        count++;
    }
    if ((count < NET_MAX_NS) && (str_len(server) > 0)) {
        err = sa_decode(&(nsv[count]), server, str_len(server));
        if (err) {
            LOGW("net_use_nameserver: could not decode `%s' (%u)\n", server, err);
            return err;
        }
        count++;
    }
    return net_use_nameserver(baresip_network(), nsv, count);
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_net_1set_1address(JNIEnv *env, jobject thiz, jstring javaIp) {
    const char *native_ip = (*env)->GetStringUTFChars(env, javaIp, 0);
    int res = 0;
    struct sa temp_sa;
    char buf[256];
    LOGD("setting address '%s'\n", native_ip);
    if (str_len(native_ip) == 0) {
        (*env)->ReleaseStringUTFChars(env, javaIp, native_ip);
        return 0;
    }
    if (0 == sa_set_str(&temp_sa, native_ip, 0)) {
        sa_ntop(&temp_sa, buf, 256);
        net_set_address(baresip_network(), &temp_sa);
    } else {
        LOGE("invalid ip address %s\n", native_ip);
        res = EAFNOSUPPORT;
    }
    (*env)->ReleaseStringUTFChars(env, javaIp, native_ip);
    return res;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_net_1unset_1address(JNIEnv *env, jobject thiz, jint javaAf) {
    struct sa temp_sa;
    LOGD("unsetting AF '%d' address\n", javaAf);
    sa_init(&temp_sa, javaAf);
    net_set_address(baresip_network(), &temp_sa);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_uag_1reset_1transp(JNIEnv *env, jobject thiz, jboolean reg,
        jboolean reinvite) {
    LOGD("reseting transports (%d, %d)\n", reg, reinvite);
    (void)uag_reset_transp(reg, reinvite);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_uag_1enable_1sip_1trace(JNIEnv *env, jobject thiz, jboolean enable) {
    LOGD("enabling sip trace (%d)\n", enable);
    (void)uag_enable_sip_trace(enable);
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_net_1debug(JNIEnv *env, jobject thiz) {
    net_debug_log();
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_net_1dns_1debug(JNIEnv *env, jobject thiz) {
    net_dns_debug_log();
}

JNIEXPORT jint JNICALL
Java_com_tutpro_baresip_Api_module_1load(JNIEnv *env, jobject thiz, jstring javaModule) {
    const char *native_module = (*env)->GetStringUTFChars(env, javaModule, 0);
    int result = module_load(".", native_module);
    (*env)->ReleaseStringUTFChars(env, javaModule, native_module);
    return result;
}

JNIEXPORT void JNICALL
Java_com_tutpro_baresip_Api_module_1unload(JNIEnv *env, jobject thiz, jstring javaModule) {
    const char *native_module = (*env)->GetStringUTFChars(env, javaModule, 0);
    module_unload(native_module);
    LOGD("unloaded module %s\n", native_module);
    (*env)->ReleaseStringUTFChars(env, javaModule, native_module);
}
