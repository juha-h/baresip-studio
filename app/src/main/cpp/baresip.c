#include <string.h>
#include <pthread.h>
#include <unistd.h>
#include <jni.h>
#include <stdlib.h>
#include <re.h>
#include <baresip.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <libavcodec/jni.h>
#include "logger.h"
#include "vidisp.h"

#if defined(__ARM_ARCH_7A__)
    #define long long long
#endif

enum
{
    ASYNC_WORKERS = 4
};

typedef struct baresip_context
{
    JavaVM *javaVM;
    JNIEnv *env;
    jclass mainActivityClz;
    jobject mainActivityObj;

} BaresipContext;

BaresipContext g_ctx;

static int vprintf_null(const char *p, size_t size, void *arg)
{
    (void)p;
    (void)size;
    (void)arg;
    return 0;
}

static void net_debug_log()
{
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", net_debug, baresip_network());
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void net_dns_debug_log()
{
    char debug_buf[2048];
    int l;
    LOGD("net_dns_debug_log\n");
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", net_dns_debug, baresip_network());
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void ua_debug_log(struct ua *ua)
{
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", ua_debug, ua);
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void account_debug_log(struct account *acc)
{
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", account_debug, acc);
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void sdp_media_debug_log(const struct sdp_media *m)
{
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", sdp_media_debug, m);
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
        LOGD("local media dir %d\n", sdp_media_ldir(m));
        LOGD("remote media dir %d\n", sdp_media_rdir(m));
    }
}

#if 0
static void ua_print_status_log(struct ua *ua)
{
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", ua_print_status, ua);
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void call_video_debug_log(struct call *call) {
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", video_debug, call_video(call));
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}

static void stream_debug_log(const struct stream *s) {
    char debug_buf[2048];
    int l;
    l = re_snprintf(&(debug_buf[0]), 2047, "%H", stream_debug, s);
    if (l != -1) {
        debug_buf[l] = '\0';
        LOGD("%s\n", debug_buf);
    }
}
#endif

static struct re_printf pf_null = {vprintf_null, 0};

static void signal_handler(int sig)
{
    static bool term = false;

    if (term) {
        module_app_unload();
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
        default:
            return "?";
    }
}

static const char *translate_errorcode(uint16_t scode)
{
    switch (scode) {
        case 404:
            return ""; /* ignore */
        case 486:
        case 603:
            return "busy";
        case 487:
            return ""; /* ignore */
        default:
            return "error";
    }
}

static void ua_event_handler(
        struct ua *ua, enum ua_event ev, struct call *call, const char *prm, void *arg)
{
    (void)arg;
    const char *tone;
    char event_buf[256];
    enum sdp_dir ardir;
    int len, ldir, rdir, err;
    struct pl module, module_event, data;
    struct sdp_media *media;
    int remote_has_video;
    ANativeWindow **win;

    LOGD("ua event (%s) %s\n", uag_event_str(ev), prm);

    switch (ev) {
        case UA_EVENT_CREATE:
            len = re_snprintf(event_buf, sizeof event_buf, "create");
            break;
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
            len = re_snprintf(event_buf, sizeof event_buf, "call incoming,%s", prm);
            break;
        case UA_EVENT_CALL_OUTGOING:
            len = re_snprintf(event_buf, sizeof event_buf, "call outgoing");
            break;
        case UA_EVENT_CALL_ANSWERED:
            len = re_snprintf(event_buf, sizeof event_buf, "call answered");
            break;
        case UA_EVENT_CALL_LOCAL_SDP:
            if (strcmp(prm, "offer") == 0)
                return;
            len = re_snprintf(event_buf, sizeof event_buf, "call %sed", prm);
            break;
        case UA_EVENT_CALL_REMOTE_SDP:
            media = stream_sdpmedia(video_strm(call_video(call)));
            remote_has_video = sdp_media_rport(media) != 0
                               && list_head(sdp_media_format_lst(media, false)) != NULL;
            ldir = sdp_media_ldir(media);
            rdir = sdp_media_rdir(media);
            sdp_media_debug_log(media);
            //stream_debug_log(video_strm(call_video(call)));
            //call_video_debug_log(call);
            ardir = sdp_media_rdir(stream_sdpmedia(audio_strm(call_audio(call))));
            len = re_snprintf(event_buf, sizeof event_buf, "remote call %sed,%d,%d,%d,%d,%d", prm,
                    call_has_video(call), remote_has_video, ldir, rdir, ardir);
            break;
        case UA_EVENT_CALL_RINGING:
            len = re_snprintf(event_buf, sizeof event_buf, "call ringing");
            break;
        case UA_EVENT_CALL_PROGRESS:
            ardir = sdp_media_rdir(stream_sdpmedia(audio_strm(call_audio(call))));
            len = re_snprintf(event_buf, sizeof event_buf, "call progress,%d", ardir);
            break;
        case UA_EVENT_CALL_ESTABLISHED:
            len = re_snprintf(event_buf, sizeof event_buf, "call established");
            break;
        case UA_EVENT_CALL_MENC:
            if (prm[0] == '0')
                len = re_snprintf(event_buf, sizeof event_buf, "call secure");
            else if (prm[0] == '1')
                len = re_snprintf(event_buf, sizeof event_buf, "call verify,%s", prm + 2);
            else if (prm[0] == '2')
                len = re_snprintf(event_buf, sizeof event_buf, "call verified,%s", prm + 2);
            else
                len = re_snprintf(event_buf, sizeof event_buf, "unknown menc event");
            break;
        case UA_EVENT_CALL_TRANSFER:
            len = re_snprintf(event_buf, sizeof event_buf, "call transfer,%s", prm);
            break;
        case UA_EVENT_CALL_TRANSFER_FAILED:
            call_hold(call, false);
            len = re_snprintf(event_buf, sizeof event_buf, "transfer failed,%s", prm);
            break;
        case UA_EVENT_CALL_CLOSED:
            tone = call_scode(call) ? translate_errorcode(call_scode(call)) : "";
            len = re_snprintf(event_buf, sizeof event_buf, "call closed,%s,%s", prm, tone);
            break;
        case UA_EVENT_MWI_NOTIFY:
            len = re_snprintf(event_buf, sizeof event_buf, "mwi notify,%s", prm);
            break;
        case UA_EVENT_MODULE:
            err = re_regex(prm, strlen(prm), "[^,]*,[^,]*,[~]*", &module, &module_event, &data);
            if (err)
                return;
            if (!pl_strcmp(&module_event, "dump")) {
                len = re_snprintf(event_buf, sizeof event_buf, "sndfile dump,%r", &data);
                break;
            } else {
                len = re_snprintf(event_buf, sizeof event_buf, "%s", prm);
            }
        default:
            return;
    }

    if (len == -1) {
        LOGE("failed to print event to buffer\n");
        return;
    }

    re_thread_leave();
    JNIEnv *env;
    jint res = (*g_ctx.javaVM)->GetEnv(g_ctx.javaVM, (void **)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        LOGE("failed to get environment: %d\n", res);
        return;
    }
    jmethodID methodId =
            (*env)->GetMethodID(env, g_ctx.mainActivityClz, "uaEvent", "(Ljava/lang/String;JJ)V");
    jstring jEvent = (*env)->NewStringUTF(env, event_buf);
    LOGD("sending ua/call %ld/%ld event %s\n", (long)ua, (long)call, event_buf);
    (*env)->CallVoidMethod(env, g_ctx.mainActivityObj, methodId, jEvent, (jlong)ua, (jlong)call);
    (*env)->DeleteLocalRef(env, jEvent);
    re_thread_enter();
}

static void message_handler(
        struct ua *ua, const struct pl *peer, const struct pl *ctype, struct mbuf *body, void *arg)
{
    (void)arg;
    char ctype_buf[128];
    char peer_buf[256];
    size_t size;

    if (snprintf(peer_buf, 256, "%.*s", (int)peer->l, peer->p) >= 256) {
        LOGE("message peer is too long (max 255 characters)\n");
        return;
    }

    re_thread_leave();
    JNIEnv *env;
    jint res = (*g_ctx.javaVM)->GetEnv(g_ctx.javaVM, (void **)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        LOGE("failed to get environment: %d\n", res);
        return;
    }
    jmethodID methodId = (*env)->GetMethodID(env, g_ctx.mainActivityClz, "messageEvent",
            "(JLjava/lang/String;Ljava/lang/String;[B)V");
    jstring jPeer = (*env)->NewStringUTF(env, peer_buf);
    pl_strcpy(ctype, ctype_buf, 256);
    jstring jCtype = (*env)->NewStringUTF(env, ctype_buf);
    jbyteArray jMsg;
    size = mbuf_get_left(body);
    jMsg = (*env)->NewByteArray(env, (jsize)size);
    if ((*env)->GetArrayLength(env, jMsg) != size) {
        (*env)->DeleteLocalRef(env, jMsg);
        jMsg = (*env)->NewByteArray(env, (jsize)size);
    }
    void *temp = (*env)->GetPrimitiveArrayCritical(env, (jarray)jMsg, 0);
    memcpy(temp, mbuf_buf(body), size);
    (*env)->ReleasePrimitiveArrayCritical(env, jMsg, temp, 0);
    LOGD("sending message %ld/%s/%s/%.*s\n", (long)ua, peer_buf, ctype_buf, (int)size,
            mbuf_buf(body));
    (*env)->CallVoidMethod(env, g_ctx.mainActivityObj, methodId, (jlong)ua, jPeer, jCtype, jMsg);
    (*env)->DeleteLocalRef(env, jCtype);
    (*env)->DeleteLocalRef(env, jPeer);
    (*env)->DeleteLocalRef(env, jMsg);
    re_thread_enter();
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
    LOGD("send_response_handler received response '%u %s' at %s\n", msg->scode, reason_buf,
            (char *)arg);

    re_thread_leave();
    JNIEnv *env;
    jint res = (*g_ctx.javaVM)->GetEnv(g_ctx.javaVM, (void **)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        LOGE("failed to get environment: %d\n", res);
        return;
    }
    jmethodID methodId = (*env)->GetMethodID(env, g_ctx.mainActivityClz, "messageResponse",
            "(ILjava/lang/String;Ljava/lang/String;)V");
    jstring javaReason = (*env)->NewStringUTF(env, reason_buf);
    jstring javaTime = (*env)->NewStringUTF(env, (char *)arg);
    (*env)->CallVoidMethod(env, g_ctx.mainActivityObj, methodId, msg->scode, javaReason, javaTime);
    (*env)->DeleteLocalRef(env, javaReason);
    (*env)->DeleteLocalRef(env, javaTime);
    re_thread_enter();
}

enum
{
    ID_UA_STOP_ALL
};

static struct mqueue *mq;

static void mqueue_handler(int id, void *data, void *arg)
{
    if (id == ID_UA_STOP_ALL) {
        LOGD("calling ua_stop_all with force %u\n", (unsigned)(uintptr_t)data);
        ua_stop_all((bool)(uintptr_t)data);
    }
}

static int pfd[2];
static pthread_t loggingThread;

static void *loggingFunction(void *arg)
{
    ssize_t readSize;
    char buf[128];

    while ((readSize = read(pfd[0], buf, sizeof buf - 1)) > 0) {
        if (buf[readSize - 1] == '\n') {
            --readSize;
        }
        buf[readSize] = 0;
        LOGD("%s", buf);
    }

    return 0;
}

static int runLoggingThread()
{
    setvbuf(stdout, 0, _IOLBF, 0);
    setvbuf(stderr, 0, _IONBF, 0);

    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    int ret = pthread_create(&loggingThread, NULL, loggingFunction, NULL);
    if (ret != 0)
        return ret;

    pthread_detach(loggingThread);

    return 0;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    LOGW("JNI_OnLoad on thread %li\n", (long)pthread_self());

    memset(&g_ctx, 0, sizeof(g_ctx));

    g_ctx.javaVM = vm;
    g_ctx.mainActivityClz = NULL;
    g_ctx.mainActivityObj = NULL;

    av_jni_set_java_vm(vm, NULL);

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_BaresipService_baresipStart(
        JNIEnv *env, jobject instance, jstring jPath, jstring jAddrs, jint jLogLevel)
{
    LOGI("starting baresip\n");

    char start_error[64] = "";

    JavaVM *javaVM = g_ctx.javaVM;

    jclass clz = (*env)->GetObjectClass(env, instance);
    g_ctx.mainActivityClz = (*env)->NewGlobalRef(env, clz);
    g_ctx.mainActivityObj = (*env)->NewGlobalRef(env, instance);

    int err;
    const char *path = (*env)->GetStringUTFChars(env, jPath, 0);
    const char *addrs = (*env)->GetStringUTFChars(env, jAddrs, 0);
    struct le *le;

    runLoggingThread();

    err = libre_init();
    if (err)
        goto out;

    if (re_thread_check(true) == 0) {
        LOGI("attaching to re thread\n");
        jint res = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        if (JNI_OK != res) {
            LOGE("failed to AttachCurrentThread: %d\n", res);
            goto out;
        }
    } else {
        LOGE("not on re thread\n");
        goto out;
    }

    conf_path_set(path);

    log_level_set((enum log_level)jLogLevel);

    err = conf_configure();
    if (err) {
        LOGW("conf_configure() failed: (%d)\n", err);
        strcpy(start_error, "conf_configure");
        goto out;
    }

    re_thread_async_init(ASYNC_WORKERS);

    err = baresip_init(conf_config());
    if (err) {
        LOGW("baresip_init() failed (%d)\n", err);
        strcpy(start_error, "baresip_init");
        goto out;
    }

    // Turn off DNS client cache (should be OK with async workers, but it not)
    dnsc_cache_max(net_dnsc(baresip_network()), 0);

    if (strlen(addrs) > 0) {
        char *addr_list = (char *)malloc(strlen(addrs) + 1);
        struct sa temp_sa;
        char buf[256];
        net_flush_addresses(baresip_network());
        strcpy(addr_list, addrs);
        char *ptr = strtok(addr_list, ";");
        while (ptr != NULL) {
            if (0 == sa_set_str(&temp_sa, ptr, 0)) {
                sa_ntop(&temp_sa, buf, 256);
                ptr = strtok(NULL, ";");
                net_add_address_ifname(baresip_network(), &temp_sa, ptr);
            } else {
                LOGE("invalid ip address (%s)\n", ptr);
                ptr = strtok(NULL, ";");
            }
            *(ptr - 1) = ';';
            ptr = strtok(NULL, ";");
        }
        free(addr_list);
    }

    // net_debug_log();

    err = ua_init("baresip v" BARESIP_VERSION " (" ARCH "/" OS ")", true, true, true);
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

    err = vidisp_register(
            &vid, baresip_vidispl(), "opengles", opengles_alloc, NULL, opengles_display, NULL);
    if (err) {
        LOGW("vidisp_register failed (%d)\n", err);
        goto out;
    }

    err = conf_modules();
    if (err) {
        LOGW("conf_modules() failed (%d)\n", err);
        strcpy(start_error, "conf_modules");
        goto out;
    }

    err = mqueue_alloc(&mq, mqueue_handler, NULL);
    if (err) {
        LOGW("mqueue_alloc failed (%d)\n", err);
        strcpy(start_error, "mqueue_alloc");
        goto out;
    }

    // no need to call re_leave/enter since main is not running yet
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

    mq = mem_deref(mq);

    LOGD("closing ...");
    ua_close();
    module_app_unload();
    conf_close();
    baresip_close();

    uag_event_unregister(ua_event_handler);

    LOGD("unloading modules ...");
    mod_close();

    vid = mem_deref(vid);

    LOGD("closing re thread\n");
    re_thread_async_close();

    LOGD("closing libre\n");
    libre_close();

    // tmr_debug();
    // mem_debug();

    LOGD("tell main that baresip has stopped");
    jstring javaError = (*env)->NewStringUTF(env, start_error);
    jmethodID stoppedId =
            (*env)->GetMethodID(env, g_ctx.mainActivityClz, "stopped", "(Ljava/lang/String;)V");
    (*env)->CallVoidMethod(env, g_ctx.mainActivityObj, stoppedId, javaError);
    (*env)->DeleteLocalRef(env, javaError);
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_BaresipService_baresipStop(
        JNIEnv *env, jobject thiz, jboolean force)
{
    LOGD("ua_stop_all upon baresipStop");
    mqueue_push(mq, ID_UA_STOP_ALL, (void *)((long)force));
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1display_1name(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc) {
        const char *dn = account_display_name((struct account *)acc);
        if (dn)
            return (*env)->NewStringUTF(env, dn);
        else
            return (*env)->NewStringUTF(env, "");
    } else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1display_1name(
        JNIEnv *env, jobject thiz, jlong acc, jstring jDn)
{
    const char *dn = (*env)->GetStringUTFChars(env, jDn, 0);
    int res;
    if (strlen(dn) > 0)
        res = account_set_display_name((struct account *)acc, dn);
    else
        res = account_set_display_name((struct account *)acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jDn, dn);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1aor(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc)
        return (*env)->NewStringUTF(env, account_aor((struct account *)acc));
    else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1luri(
        JNIEnv *env, jclass clazz, jlong acc)
{
    const struct uri *uri = account_luri((struct account *)acc);
    char uri_buf[512];
    int l;
    l = re_snprintf(&(uri_buf[0]), 511, "%H", uri_encode, uri);
    if (l != -1)
        uri_buf[l] = '\0';
    else
        uri_buf[0] = '\0';
    return (*env)->NewStringUTF(env, uri_buf);
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1auth_1user(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc) {
        const char *au = account_auth_user((struct account *)acc);
        if (au)
            return (*env)->NewStringUTF(env, au);
        else
            return (*env)->NewStringUTF(env, "");
    } else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1auth_1user(
        JNIEnv *env, jobject thiz, jlong acc, jstring jUser)
{
    const char *user = (*env)->GetStringUTFChars(env, jUser, 0);
    int res;
    if (strlen(user) > 0)
        res = account_set_auth_user((struct account *)acc, user);
    else
        res = account_set_auth_user((struct account *)acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jUser, user);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1auth_1pass(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc) {
        const char *ap = account_auth_pass((struct account *)acc);
        if (ap)
            return (*env)->NewStringUTF(env, ap);
        else
            return (*env)->NewStringUTF(env, "");
    } else
        return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1auth_1pass(
        JNIEnv *env, jobject thiz, jlong acc, jstring jPass)
{
    const char *pass = (*env)->GetStringUTFChars(env, jPass, 0);
    int res;
    if (strlen(pass) > 0)
        res = account_set_auth_pass((struct account *)acc, pass);
    else
        res = account_set_auth_pass((struct account *)acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jPass, pass);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1outbound(
        JNIEnv *env, jobject thiz, jlong acc, jint jIx)
{
    const uint16_t native_ix = jIx;
    const char *outbound;
    if (acc) {
        outbound = account_outbound((struct account *)acc, native_ix);
        if (outbound)
            return (*env)->NewStringUTF(env, outbound);
        else
            return (*env)->NewStringUTF(env, "");
    } else {
        return (*env)->NewStringUTF(env, "");
    }
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1outbound(
        JNIEnv *env, jobject thiz, jlong acc, jstring jOb, jint jIx)
{
    const char *ob = (*env)->GetStringUTFChars(env, jOb, 0);
    const uint16_t ix = jIx;
    int res;
    if (strlen(ob) > 0)
        res = account_set_outbound((struct account *)acc, ob, ix);
    else
        res = account_set_outbound((struct account *)acc, NULL, ix);
    (*env)->ReleaseStringUTFChars(env, jOb, ob);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1audio_1codec(
        JNIEnv *env, jobject thiz, jlong acc, jint ix)
{
    const struct list *codecl;
    char codec_buf[32];
    int len;
    struct le *le;
    codec_buf[0] = '\0';
    if (acc) {
        codecl = account_aucodecl((struct account *)acc);
        if (!list_isempty(codecl)) {
            int i = -1;
            for (le = list_head(codecl); le != NULL; le = le->next) {
                i++;
                if (i == ix) {
                    const struct aucodec *ac = le->data;
                    len = re_snprintf(
                            codec_buf, sizeof codec_buf, "%s/%u/%u", ac->name, ac->srate, ac->ch);
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

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1audio_1codecs(
        JNIEnv *env, jobject thiz, jlong acc, jstring jCodecs)
{
    const char *codecs = (*env)->GetStringUTFChars(env, jCodecs, 0);
    int res = account_set_audio_codecs((struct account *)acc, codecs);
    (*env)->ReleaseStringUTFChars(env, jCodecs, codecs);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1video_1codec(
        JNIEnv *env, jobject thiz, jlong acc, jint ix)
{
    const struct list *codecl;
    char codec_buf[32];
    int len;
    struct le *le;
    codec_buf[0] = '\0';
    if (acc) {
        codecl = account_vidcodecl((struct account *)acc);
        if (!list_isempty(codecl)) {
            int i = -1;
            for (le = list_head(codecl); le != NULL; le = le->next) {
                i++;
                if (i == ix) {
                    const struct vidcodec *vc = le->data;
                    len = re_snprintf(codec_buf, sizeof codec_buf, "%s", vc->name);
                    if (len == -1) {
                        LOGE("failed to print video codec to buffer\n");
                        codec_buf[0] = '\0';
                    }
                    break;
                }
            }
        }
    }
    return (*env)->NewStringUTF(env, codec_buf);
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1video_1codecs(
        JNIEnv *env, jobject thiz, jlong acc, jstring jCodecs)
{
    const char *codecs = (*env)->GetStringUTFChars(env, jCodecs, 0);
    int res = account_set_video_codecs((struct account *)acc, codecs);
    (*env)->ReleaseStringUTFChars(env, jCodecs, codecs);
    return res;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1regint(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc)
        return (jint)account_regint((struct account *)acc);
    else
        return 0;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1regint(
        JNIEnv *env, jobject thiz, jlong acc, jint jRegint)
{
    const uint32_t regint = (uint32_t)jRegint;
    return account_set_regint((struct account *)acc, regint);
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1mediaenc(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc) {
        const char *mediaenc = account_mediaenc((struct account *)acc);
        if (mediaenc)
            return (*env)->NewStringUTF(env, mediaenc);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1mediaenc(
        JNIEnv *env, jobject thiz, jlong acc, jstring jMencid)
{
    const char *mencid = (*env)->GetStringUTFChars(env, jMencid, 0);
    int res;
    if (strlen(mencid) > 0)
        res = account_set_mediaenc((struct account *)acc, mencid);
    else
        res = account_set_mediaenc((struct account *)acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jMencid, mencid);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1medianat(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc) {
        const char *medianat = account_medianat((struct account *)acc);
        if (medianat)
            return (*env)->NewStringUTF(env, medianat);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1medianat(
        JNIEnv *env, jobject thiz, jlong acc, jstring jMedNat)
{
    const char *mednat = (*env)->GetStringUTFChars(env, jMedNat, 0);
    int res;
    if (strlen(mednat) > 0)
        res = account_set_medianat((struct account *)acc, mednat);
    else
        res = account_set_medianat((struct account *)acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jMedNat, mednat);
    return res;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1sipnat(
        JNIEnv *env, jobject thiz, jlong acc, jstring jSipNat)
{
    const char *sipnat = (*env)->GetStringUTFChars(env, jSipNat, 0);
    int res;
    if (strlen(sipnat) > 0)
        res = account_set_sipnat((struct account *)acc, sipnat);
    else
        res = account_set_sipnat((struct account *)acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jSipNat, sipnat);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1stun_1uri(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc) {
        const struct stun_uri *stun_uri = account_stun_uri((struct account *)acc);
        if (stun_uri) {
            char uri_str[256];
            if (stun_uri->port != 0) {
                if (stun_uri->proto == IPPROTO_TCP)
                    sprintf(uri_str, "%s:%s:%d?transport=tcp",
                            stunuri_scheme_name(stun_uri->scheme), stun_uri->host, stun_uri->port);
                else
                    sprintf(uri_str, "%s:%s:%d", stunuri_scheme_name(stun_uri->scheme),
                            stun_uri->host, stun_uri->port);
            } else {
                if (stun_uri->proto == IPPROTO_TCP)
                    sprintf(uri_str, "%s:%s?transport=tcp", stunuri_scheme_name(stun_uri->scheme),
                            stun_uri->host);
                else
                    sprintf(uri_str, "%s:%s", stunuri_scheme_name(stun_uri->scheme),
                            stun_uri->host);
            }
            return (*env)->NewStringUTF(env, uri_str);
        }
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1stun_1uri(
        JNIEnv *env, jobject thiz, jlong acc, jstring jUri)
{
    const char *uri = (*env)->GetStringUTFChars(env, jUri, 0);
    int res;
    if (strlen(uri) > 0)
        res = account_set_stun_uri((struct account *)acc, uri);
    else
        res = account_set_stun_uri((struct account *)acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jUri, uri);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1stun_1user(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc) {
        const char *stun_user = account_stun_user((struct account *)acc);
        if (stun_user)
            return (*env)->NewStringUTF(env, stun_user);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1stun_1user(
        JNIEnv *env, jobject thiz, jlong acc, jstring jUser)
{
    const char *user = (*env)->GetStringUTFChars(env, jUser, 0);
    int res;
    if (strlen(user) > 0)
        res = account_set_stun_user((struct account *)acc, user);
    else
        res = account_set_stun_user((struct account *)acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jUser, user);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1stun_1pass(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc) {
        const char *stun_pass = account_stun_pass((struct account *)acc);
        if (stun_pass)
            return (*env)->NewStringUTF(env, stun_pass);
    }
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1stun_1pass(
        JNIEnv *env, jobject thiz, jlong acc, jstring jPass)
{
    const char *pass = (*env)->GetStringUTFChars(env, jPass, 0);
    int res;
    if (strlen(pass) > 0)
        res = account_set_stun_pass((struct account *)acc, pass);
    else
        res = account_set_stun_pass((struct account *)acc, NULL);
    (*env)->ReleaseStringUTFChars(env, jPass, pass);
    return res;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1mwi(
        JNIEnv *env, jobject thiz, jlong acc, jboolean value)
{
    return account_set_mwi((struct account *)acc, value);
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1vm_1uri(
        JNIEnv *env, jobject thiz, jlong acc)
{
    char uri_buf[256];
    if (acc) {
        struct pl pl;
        const struct sip_addr *addr = account_laddr((struct account *)acc);
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

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1answermode(
        JNIEnv *env, jobject thiz, jlong acc)
{
    return account_answermode((struct account *)acc);
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1answermode(
        JNIEnv *env, jobject thiz, jlong acc, jint jMode)
{
    const uint32_t mode = (uint32_t)jMode;
    return account_set_answermode((struct account *)acc, mode);
}

JNIEXPORT jboolean JNICALL Java_com_tutpro_baresip_plus_Api_account_1rtcp_1mux(
        JNIEnv *env, jobject thiz, jlong acc)
{
    return account_rtcp_mux((struct account *)acc);
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1rtcp_1mux(
        JNIEnv *env, jobject thiz, jlong acc, jboolean value)
{
    return account_set_rtcp_mux((struct account *)acc, value);
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1dtmfmode(
        JNIEnv *env, jobject thiz, jlong acc)
{
    return account_dtmfmode((struct account *)acc);
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_account_1set_1dtmfmode(
        JNIEnv *env, jobject thiz, jlong acc, jint jMode)
{
    const uint32_t mode = (uint32_t)jMode;
    return account_set_dtmfmode((struct account *)acc, mode);
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_account_1extra(
        JNIEnv *env, jobject thiz, jlong acc)
{
    if (acc) {
        const char *extra = account_extra((struct account *)acc);
        if (extra)
            return (*env)->NewStringUTF(env, extra);
    }
    return (*env)->NewStringUTF(env, "");
}
JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_account_1debug(
        JNIEnv *env, jobject thiz, jlong acc)
{
    account_debug_log((struct account *)acc);
}

JNIEXPORT jlong JNICALL Java_com_tutpro_baresip_plus_Api_ua_1alloc(
        JNIEnv *env, jobject thiz, jstring jUri)
{
    const char *uri = (*env)->GetStringUTFChars(env, jUri, 0);
    struct ua *ua;
    LOGD("allocating UA '%s'\n", uri);
    re_thread_enter();
    int res = ua_alloc(&ua, uri);
    re_thread_leave();
    if (res == 0) {
        LOGD("allocated ua '%ld'\n", (long)ua);
    } else {
        LOGE("failed to allocate ua '%s'\n", uri);
    }
    (*env)->ReleaseStringUTFChars(env, jUri, uri);
    return (jlong)ua;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_ua_1register(
        JNIEnv *env, jobject thiz, jlong ua)
{
    LOGD("registering UA '%ld'\n", (long)ua);
    re_thread_enter();
    int res = ua_register((struct ua *)ua);
    re_thread_leave();
    return res;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_ua_1unregister(
        JNIEnv *env, jobject thiz, jlong ua)
{
    re_thread_enter();
    ua_unregister((struct ua *)ua);
    re_thread_leave();
}

JNIEXPORT jboolean JNICALL Java_com_tutpro_baresip_plus_Api_ua_1isregistered(
        JNIEnv *env, jobject thiz, jlong ua)
{
    return ua_isregistered((struct ua *)ua) ? true : false;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_ua_1destroy(
        JNIEnv *env, jobject thiz, jlong ua)
{
    LOGD("destroying ua %ld\n", (long)ua);
    re_thread_enter();
    (void)ua_destroy((struct ua *)ua);
    re_thread_leave();
}

JNIEXPORT jlong JNICALL Java_com_tutpro_baresip_plus_Api_ua_1account(
        JNIEnv *env, jobject thiz, jlong ua)
{
    struct account *acc = 0;
    if (ua)
        acc = ua_account((struct ua *)ua);
    return (jlong)acc;
}

JNIEXPORT jlong JNICALL Java_com_tutpro_baresip_plus_Api_ua_1update_1account(
        JNIEnv *env, jobject thiz, jlong ua)
{
    LOGD("updating account of ua %ld\n", (long)ua);
    return ua_update_account((struct ua *)ua);
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_ua_1hangup(
        JNIEnv *env, jobject thiz, jlong ua, jlong call, jint code, jstring reason)
{
    const uint16_t native_code = code;
    const char *native_reason = (*env)->GetStringUTFChars(env, reason, 0);
    const int thread_check = re_thread_check(false);
    LOGD("hanging up call %ld/%ld\n", (long)ua, (long)call);
    re_thread_enter();
    if (strlen(native_reason) == 0)
        ua_hangup((struct ua *)ua, (struct call *)call, native_code, NULL);
    else
        ua_hangup((struct ua *)ua, (struct call *)call, native_code, native_reason);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, reason, native_reason);
}

JNIEXPORT jlong JNICALL Java_com_tutpro_baresip_plus_Api_ua_1call_1alloc(
        JNIEnv *env, jobject thiz, jlong ua, jlong xCall, jint vidMode)
{
    struct call *call = NULL;
    int err;
    LOGD("allocating new call for ua %ld xcall %ld\n", (long)ua, (long)xCall);
    re_thread_enter();
    err = ua_call_alloc(&call, (struct ua *)ua, (enum vidmode)vidMode, NULL, (struct call *)xCall,
            call_localuri((struct call *)xCall), true);
    re_thread_leave();
    if (err)
        LOGW("call allocation for ua %ld failed with error %d\n", (long)ua, err);
    return (jlong)call;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_ua_1answer(
        JNIEnv *env, jobject thiz, jlong ua, jlong call, jint vidMode)
{
    LOGD("answering ua/call %ld/%ld with video mode %d\n", (long)ua, (long)call, vidMode);
    re_thread_enter();
    ua_answer((struct ua *)ua, (struct call *)call, (enum vidmode)vidMode);
    re_thread_leave();
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_ua_1add_1custom_1header(
        JNIEnv *env, jobject thiz, jlong ua, jstring jname, jstring jbody)
{
    const char *name = (*env)->GetStringUTFChars(env, jname, 0);
    const char *body = (*env)->GetStringUTFChars(env, jbody, 0);
    LOGD("adding header to %ld with name/body %s/%s\n", (long)ua, name, body);
    re_thread_enter();
    struct pl pl_name, pl_body;
    pl_set_str(&pl_name, name);
    pl_set_str(&pl_body, body);
    int err = ua_add_custom_hdr((struct ua *)ua, &pl_name, &pl_body);
    re_thread_leave();
    if (err)
        LOGW("adding custom header to ua %ld failed with  error %d\n", (long)ua, err);
    (*env)->ReleaseStringUTFChars(env, jname, name);
    (*env)->ReleaseStringUTFChars(env, jbody, body);
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_ua_1debug(
        JNIEnv *env, jobject thiz, jlong ua)
{
    ua_debug_log((struct ua *)ua);
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_calls_1mute(
        JNIEnv *env, jobject thiz, jboolean mute)
{
    struct le *ua_le;
    struct le *call_le;
    LOGD("muting calls %d\n", mute);
    re_thread_enter();
    for (ua_le = list_head(uag_list()); ua_le != NULL; ua_le = ua_le->next) {
        const struct ua *ua = ua_le->data;
        for (call_le = list_head(ua_calls(ua)); call_le != NULL; call_le = call_le->next) {
            const struct call *call = call_le->data;
            audio_mute(call_audio(call), mute);
        }
    }
    re_thread_leave();
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_call_1connect(
        JNIEnv *env, jobject thiz, jlong call, jstring jPeer)
{
    const char *native_peer = (*env)->GetStringUTFChars(env, jPeer, 0);
    LOGD("connecting call %ld to %s\n", (long)call, native_peer);
    re_thread_enter();
    struct pl pl;
    pl_set_str(&pl, native_peer);
    int err = call_connect((struct call *)call, &pl);
    re_thread_leave();
    if (err)
        LOGW("call_connect error: %d\n", err);
    (*env)->ReleaseStringUTFChars(env, jPeer, native_peer);
    return err;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_call_1notify_1sipfrag(
        JNIEnv *env, jobject thiz, jlong call, jint code, jstring reason)
{
    const uint16_t native_code = code;
    const char *native_reason = (*env)->GetStringUTFChars(env, reason, 0);
    LOGD("notifying call %ld/%s\n", (long)call, native_reason);
    re_thread_enter();
    (void)call_notify_sipfrag((struct call *)call, native_code, native_reason);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, reason, native_reason);
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_call_1hold(
        JNIEnv *env, jobject thiz, jlong call, jboolean hold)
{
    int err;
    if (hold) {
        LOGD("holding call %ld\n", (long)call);
        re_thread_enter();
        err = call_hold((struct call *)call, true);
        re_thread_leave();
    } else {
        LOGD("resuming call %ld\n", (long)call);
        re_thread_enter();
        err = call_hold((struct call *)call, false);
        re_thread_leave();
    }
    if (err)
        LOGW("call_hold error: %d\n", err);
    return err;
}

JNIEXPORT jboolean JNICALL Java_com_tutpro_baresip_plus_Api_call_1ismuted(
        JNIEnv *env, jobject thiz, jlong call)
{
    return audio_ismuted(call_audio((struct call *)call));
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_call_1transfer(
        JNIEnv *env, jobject thiz, jlong call, jstring jPeer)
{
    const char *native_peer = (*env)->GetStringUTFChars(env, jPeer, 0);
    LOGD("transfering call %ld to %s\n", (long)call, native_peer);
    re_thread_enter();
    int err = call_transfer((struct call *)call, native_peer);
    re_thread_leave();
    if (err)
        LOGW("call_transfer error: %d\n", err);
    (*env)->ReleaseStringUTFChars(env, jPeer, native_peer);
    return err;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_call_1send_1digit(
        JNIEnv *env, jobject thiz, jlong call, jchar digit)
{
    const uint16_t native_digit = digit;
    LOGD("sending DTMF digit '%c' to call %ld\n", (char)native_digit, (long)call);
    re_thread_enter();
    int res = call_send_digit((struct call *)call, (char)native_digit);
    if (!res)
        res = call_send_digit((struct call *)call, KEYCODE_REL);
    re_thread_leave();
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_call_1audio_1codecs(
        JNIEnv *env, jobject thiz, jlong call)
{
    const struct aucodec *tx = audio_codec(call_audio((struct call *)call), true);
    const struct aucodec *rx = audio_codec(call_audio((struct call *)call), false);
    char codec_buf[256];
    char *start = &(codec_buf[0]);
    unsigned int left = sizeof codec_buf;
    int len = -1;
    if (tx && rx)
        len = re_snprintf(start, left, "%s/%u/%u,%s/%u/%u", tx->name, tx->srate, tx->ch, rx->name,
                rx->srate, rx->ch);
    if (len == -1) {
        LOGE("failed to get audio codecs of call %ld\n", (long)call);
        codec_buf[0] = '\0';
    }
    return (*env)->NewStringUTF(env, codec_buf);
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_call_1video_1codecs(
        JNIEnv *env, jobject thiz, jlong call)
{
    const struct vidcodec *tx = video_codec(call_video((struct call *)call), true);
    const struct vidcodec *rx = video_codec(call_video((struct call *)call), false);
    char codec_buf[256];
    char *start = &(codec_buf[0]);
    unsigned int left = sizeof codec_buf;
    int len = -1;
    if (tx && rx)
        len = re_snprintf(start, left, "%s,%s", tx->name, rx->name);
    if (len == -1) {
        LOGE("failed to get video codecs of call %ld\n", (long)call);
        codec_buf[0] = '\0';
    }
    return (*env)->NewStringUTF(env, codec_buf);
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_call_1duration(
        JNIEnv *env, jobject thiz, jlong call)
{
    return (jint)call_duration((struct call *)call);
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_call_1stats(
        JNIEnv *env, jobject thiz, jlong call, jstring jStream)
{
    const char *native_stream = (*env)->GetStringUTFChars(env, jStream, 0);
    const struct stream *s;
    if (strcmp(native_stream, "audio") == 0)
        s = audio_strm(call_audio((struct call *)call));
    else
        s = video_strm(call_video((struct call *)call));
    const struct rtcp_stats *stats = stream_rtcp_stats(s);
    char stats_buf[256];
    int len;
    if (stats) {
        const double tx_rate = 1.0 * stream_metric_get_tx_bitrate(s) / 1000.0;
        const double rx_rate = 1.0 * stream_metric_get_rx_bitrate(s) / 1000.0;
        const double tx_avg_rate = 1.0 * stream_metric_get_tx_avg_bitrate(s) / 1000.0;
        const double rx_avg_rate = 1.0 * stream_metric_get_rx_avg_bitrate(s) / 1000.0;
        len = re_snprintf(&(stats_buf[0]), 256, "%.1f/%.1f,%.1f/%.1f,%u/%u,%d/%d,%.1f/%.1f",
                tx_rate, rx_rate, tx_avg_rate, rx_avg_rate, stats->tx.sent, stats->rx.sent,
                stats->tx.lost, stats->rx.lost, 1.0 * stats->tx.jit / 1000,
                1.0 * stats->rx.jit / 1000);
        if (len == -1) {
            LOGE("failed to get stats of call %ld %s stream\n", (long)call, native_stream);
            stats_buf[0] = '\0';
        }
    }
    (*env)->ReleaseStringUTFChars(env, jStream, native_stream);
    return (*env)->NewStringUTF(env, stats_buf);
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_call_1state(
        JNIEnv *env, jobject thiz, jlong call)
{
    return call_state((struct call *)call);
}

JNIEXPORT jboolean JNICALL Java_com_tutpro_baresip_plus_Api_call_1has_1video(
        JNIEnv *env, jobject thiz, jlong call)
{
    return call_has_video((struct call *)call) ? true : false;
}

JNIEXPORT jboolean JNICALL Java_com_tutpro_baresip_plus_Api_call_1replaces(
        JNIEnv *env, jobject thiz, jlong call)
{
    return call_supported((struct call *)call, REPLACES) ? true : false;
}

JNIEXPORT jboolean JNICALL Java_com_tutpro_baresip_plus_Api_call_1replace_1transfer(
        JNIEnv *env, jobject thiz, jlong xferCall, jlong call)
{
    re_thread_enter();
    int res = call_replace_transfer((struct call *)xferCall, (struct call *)call);
    re_thread_leave();
    return res == 0 ? true : false;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_call_1peer_1uri(
        JNIEnv *env, jobject thiz, jlong call)
{
    const char *uri = call_peeruri((struct call *)call);
    if (uri)
        return (*env)->NewStringUTF(env, uri);
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_call_1diverter_1uri(
        JNIEnv *env, jobject thiz, jlong call)
{
    const char *uri = call_diverteruri((struct call *)call);
    if (uri)
        return (*env)->NewStringUTF(env, uri);
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_message_1send(
        JNIEnv *env, jobject thiz, jlong ua, jstring jPeer, jstring jMsg, jstring jTime)
{
    const char *native_peer = (*env)->GetStringUTFChars(env, jPeer, 0);
    const char *native_msg = (*env)->GetStringUTFChars(env, jMsg, 0);
    const char *native_time = (*env)->GetStringUTFChars(env, jTime, 0);
    LOGD("sending message from ua %ld to %s at %s\n", (long)ua, native_peer, native_time);
    re_thread_enter();
    int err = message_send(
            (struct ua *)ua, native_peer, native_msg, send_resp_handler, (void *)native_time);
    re_thread_leave();
    if (err) {
        LOGW("message_send failed with error %d\n", err);
    }
    (*env)->ReleaseStringUTFChars(env, jPeer, native_peer);
    (*env)->ReleaseStringUTFChars(env, jMsg, native_msg);
    return err;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_call_1disable_1video_1stream(
        JNIEnv *env, jobject thiz, jlong call, jboolean disable)
{
    re_thread_enter();
    sdp_media_set_disabled(stream_sdpmedia(video_strm(call_video((struct call *)call))), disable);
    re_thread_leave();
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_call_1set_1video_1direction(
        JNIEnv *env, jobject thiz, jlong call, jint dir)
{
    re_thread_enter();
    call_set_video_dir((struct call *)call, (enum sdp_dir)dir);
    re_thread_leave();
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_call_1set_1media_1direction(
        JNIEnv *env, jobject thiz, jlong call, jint adir, jint vdir)
{
    int err;
    LOGD("call set audio/video media direction of call %ld to %d/%d\n", call, adir, vdir);
    re_thread_enter();
    err = call_set_media_direction((struct call *)call, (enum sdp_dir)adir, (enum sdp_dir)vdir);
    re_thread_leave();
    return err;
}

JNIEXPORT jboolean JNICALL Java_com_tutpro_baresip_plus_Api_call_1video_1enabled(
        JNIEnv *env, jobject thiz, jlong call)
{
    int res;
    re_thread_enter();
    res = call_has_video((struct call *)call)
          && sdp_media_dir(stream_sdpmedia(video_strm(call_video((struct call *)call))))
                     != SDP_INACTIVE;
    re_thread_leave();
    return res;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_call_1start_1video_1display(
        JNIEnv *env, jobject thiz, jlong call)
{
    int err;
    re_thread_enter();
    err = video_start_display(call_video((struct call *)call), NULL);
    re_thread_leave();
    return err;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_call_1stop_1video_1display(
        JNIEnv *env, jobject thiz, jlong call)
{
    re_thread_enter();
    video_stop_display(call_video((struct call *)call));
    re_thread_leave();
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_call_1set_1video_1source(
        JNIEnv *env, jobject thiz, jlong call, jboolean front)
{
    int err;
    re_thread_enter();
    char *dev = front ? "android_camera,1" : "android_camera,0";
    err = video_set_source(call_video((struct call *)call), "avformat", dev);
    re_thread_leave();
    return err;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_call_1destroy(
        JNIEnv *env, jobject thiz, jlong call)
{
    re_thread_enter();
    mem_deref((struct call *)call);
    re_thread_leave();
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_cmd_1exec(
        JNIEnv *env, jobject thiz, jstring javaCmd)
{
    const char *native_cmd = (*env)->GetStringUTFChars(env, javaCmd, 0);
    LOGD("processing command '%s'\n", native_cmd);
    re_thread_enter();
    int res = cmd_process_long(baresip_commands(), native_cmd, strlen(native_cmd), &pf_null, NULL);
    re_thread_leave();
    (*env)->ReleaseStringUTFChars(env, javaCmd, native_cmd);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_audio_1codecs(JNIEnv *env, jobject thiz)
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

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_plus_Api_video_1codecs(JNIEnv *env, jobject thiz)
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

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_net_1add_1address_1ifname(
        JNIEnv *env, jobject thiz, jstring jAddr, jstring jIfName)
{
    const char *addr = (*env)->GetStringUTFChars(env, jAddr, 0);
    const char *name = (*env)->GetStringUTFChars(env, jIfName, 0);
    int res = 0;
    struct sa temp_sa;
    char buf[256];
    LOGD("adding address/ifname '%s/%s'\n", addr, name);
    if (0 == sa_set_str(&temp_sa, addr, 0)) {
        sa_ntop(&temp_sa, buf, 256);
        re_thread_enter();
        res = net_add_address_ifname(baresip_network(), &temp_sa, name);
        re_thread_leave();
    } else {
        LOGE("invalid ip address %s\n", addr);
        res = EAFNOSUPPORT;
    }
    (*env)->ReleaseStringUTFChars(env, jAddr, addr);
    (*env)->ReleaseStringUTFChars(env, jIfName, name);
    return res;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_net_1rm_1address(
        JNIEnv *env, jobject thiz, jstring jIp)
{
    const char *native_ip = (*env)->GetStringUTFChars(env, jIp, 0);
    int res = 0;
    struct sa temp_sa;
    char buf[256];
    LOGD("removing address '%s'\n", native_ip);
    if (str_len(native_ip) == 0) {
        (*env)->ReleaseStringUTFChars(env, jIp, native_ip);
        return 0;
    }
    if (0 == sa_set_str(&temp_sa, native_ip, 0)) {
        sa_ntop(&temp_sa, buf, 256);
        re_thread_enter();
        res = net_rm_address(baresip_network(), &temp_sa);
        re_thread_leave();
    } else {
        LOGE("invalid ip address %s\n", native_ip);
        res = EAFNOSUPPORT;
    }
    (*env)->ReleaseStringUTFChars(env, jIp, native_ip);
    return res;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_log_1level_1set(
        JNIEnv *env, jobject thiz, jint level)
{
    const enum log_level native_level = (enum log_level)level;
    LOGD("setting log level to '%u'\n", native_level);
    log_level_set(native_level);
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_net_1use_1nameserver(
        JNIEnv *env, jobject thiz, jstring javaServers)
{
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

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_uag_1reset_1transp(
        JNIEnv *env, jobject thiz, jboolean reg, jboolean reinvite)
{
    LOGD("reseting transports (%d, %d)\n", reg, reinvite);
    re_thread_enter();
    (void)uag_reset_transp(reg, reinvite);
    re_thread_leave();
}


JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_uag_1enable_1sip_1trace(
        JNIEnv *env, jobject thiz, jboolean enable)
{
    LOGD("enabling sip trace (%d)\n", enable);
    uag_enable_sip_trace(enable);
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_net_1debug(JNIEnv *env, jobject thiz)
{
    re_thread_enter();
    net_debug_log();
    re_thread_leave();
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_net_1dns_1debug(JNIEnv *env, jobject thiz)
{
    re_thread_enter();
    net_dns_debug_log();
    re_thread_leave();
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_plus_Api_module_1load(
        JNIEnv *env, jobject thiz, jstring javaModule)
{
    const char *native_module = (*env)->GetStringUTFChars(env, javaModule, 0);
    int result = module_load(".", native_module);
    (*env)->ReleaseStringUTFChars(env, javaModule, native_module);
    return result;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_plus_Api_module_1unload(
        JNIEnv *env, jobject thiz, jstring javaModule)
{
    const char *native_module = (*env)->GetStringUTFChars(env, javaModule, 0);
    module_unload(native_module);
    LOGD("unloaded module %s\n", native_module);
    (*env)->ReleaseStringUTFChars(env, javaModule, native_module);
}
