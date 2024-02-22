/**
 * @file re_http.h  Hypertext Transfer Protocol
 *
 * Copyright (C) 2010 Creytiv.com
 */

/* forward declarations */
struct tls;

/** HTTP Header ID (perfect hash value) */
enum http_hdrid {
	HTTP_HDR_ACCEPT                   = 3186,
	HTTP_HDR_ACCEPT_CHARSET           =   24,
	HTTP_HDR_ACCEPT_ENCODING          =  708,
	HTTP_HDR_ACCEPT_LANGUAGE          = 2867,
	HTTP_HDR_ACCEPT_RANGES            = 3027,
	HTTP_HDR_AGE                      =  742,
	HTTP_HDR_ALLOW                    = 2429,
	HTTP_HDR_AUTHORIZATION            = 2503,
	HTTP_HDR_CACHE_CONTROL            = 2530,
	HTTP_HDR_CONNECTION               =  865,
	HTTP_HDR_CONTENT_ENCODING         =  580,
	HTTP_HDR_CONTENT_LANGUAGE         = 3371,
	HTTP_HDR_CONTENT_LENGTH           = 3861,
	HTTP_HDR_CONTENT_LOCATION         = 3927,
	HTTP_HDR_CONTENT_MD5              =  406,
	HTTP_HDR_CONTENT_RANGE            = 2846,
	HTTP_HDR_CONTENT_TYPE             =  809,
	HTTP_HDR_DATE                     = 1027,
	HTTP_HDR_ETAG                     = 2392,
	HTTP_HDR_EXPECT                   = 1550,
	HTTP_HDR_EXPIRES                  = 1983,
	HTTP_HDR_FROM                     = 1963,
	HTTP_HDR_HOST                     = 3191,
	HTTP_HDR_IF_MATCH                 = 2684,
	HTTP_HDR_IF_MODIFIED_SINCE        = 2187,
	HTTP_HDR_IF_NONE_MATCH            = 4030,
	HTTP_HDR_IF_RANGE                 = 2220,
	HTTP_HDR_IF_UNMODIFIED_SINCE      =  962,
	HTTP_HDR_LAST_MODIFIED            = 2946,
	HTTP_HDR_LOCATION                 = 2514,
	HTTP_HDR_MAX_FORWARDS             = 3549,
	HTTP_HDR_PRAGMA                   = 1673,
	HTTP_HDR_PROXY_AUTHENTICATE       =  116,
	HTTP_HDR_PROXY_AUTHORIZATION      = 2363,
	HTTP_HDR_RANGE                    = 4004,
	HTTP_HDR_REFERER                  = 2991,
	HTTP_HDR_RETRY_AFTER              =  409,
	HTTP_HDR_SEC_WEBSOCKET_ACCEPT     = 2959,
	HTTP_HDR_SEC_WEBSOCKET_EXTENSIONS = 2937,
	HTTP_HDR_SEC_WEBSOCKET_KEY        =  746,
	HTTP_HDR_SEC_WEBSOCKET_PROTOCOL   = 2076,
	HTTP_HDR_SEC_WEBSOCKET_VERSION    = 3158,
	HTTP_HDR_SERVER                   =  973,
	HTTP_HDR_TE                       = 2035,
	HTTP_HDR_TRAILER                  = 2577,
	HTTP_HDR_TRANSFER_ENCODING        = 2115,
	HTTP_HDR_UPGRADE                  =  717,
	HTTP_HDR_USER_AGENT               = 4064,
	HTTP_HDR_VARY                     = 3076,
	HTTP_HDR_VIA                      = 3961,
	HTTP_HDR_WARNING                  = 2108,
	HTTP_HDR_WWW_AUTHENTICATE         = 2763,

	HTTP_HDR_NONE = -1
};


/** HTTP Header */
struct http_hdr {
	struct le le;          /**< Linked-list element     */
	struct pl name;        /**< HTTP Header name        */
	struct pl val;         /**< HTTP Header value       */
	enum http_hdrid id;    /**< HTTP Header id (unique) */
};

/** HTTP Message */
struct http_msg {
	struct pl ver;         /**< HTTP Version number                    */
	struct pl met;         /**< Request Method                         */
	struct pl path;        /**< Request path/resource                  */
	struct pl prm;         /**< Request parameters                     */
	uint16_t scode;        /**< Response Status code                   */
	struct pl reason;      /**< Response Reason phrase                 */
	struct list hdrl;      /**< List of HTTP headers (struct http_hdr) */
	struct msg_ctype ctyp; /**< Content-type                           */
	struct mbuf *_mb;      /**< Buffer containing the HTTP message     */
	struct mbuf *mb;       /**< Buffer containing the HTTP body        */
	uint32_t clen;         /**< Content length                         */
};


struct http_uri {
	struct pl scheme;
	struct pl host;
	struct pl port;
	struct pl path;
};

int http_uri_decode(struct http_uri *hu, const struct pl *uri);


/** Http Client configuration */
struct http_conf {
	uint32_t conn_timeout;  /* in [ms] */
	uint32_t recv_timeout;  /* in [ms] */
	uint32_t idle_timeout;  /* in [ms] */
};


typedef bool(http_hdr_h)(const struct http_hdr *hdr, void *arg);

int  http_msg_decode(struct http_msg **msgp, struct mbuf *mb, bool req);


const struct http_hdr *http_msg_hdr(const struct http_msg *msg,
				    enum http_hdrid id);
const struct http_hdr *http_msg_hdr_apply(const struct http_msg *msg,
					  bool fwd, enum http_hdrid id,
					  http_hdr_h *h, void *arg);
const struct http_hdr *http_msg_xhdr(const struct http_msg *msg,
				     const char *name);
const struct http_hdr *http_msg_xhdr_apply(const struct http_msg *msg,
					   bool fwd, const char *name,
					   http_hdr_h *h, void *arg);
uint32_t http_msg_hdr_count(const struct http_msg *msg, enum http_hdrid id);
uint32_t http_msg_xhdr_count(const struct http_msg *msg, const char *name);
bool http_msg_hdr_has_value(const struct http_msg *msg, enum http_hdrid id,
			    const char *value);
bool http_msg_xhdr_has_value(const struct http_msg *msg, const char *name,
			     const char *value);
int  http_msg_print(struct re_printf *pf, const struct http_msg *msg);


/* Client */
struct http_cli;
struct http_req;
struct dnsc;
struct tcp_conn;
struct tls_conn;

typedef void (http_resp_h)(int err, const struct http_msg *msg, void *arg);
typedef int  (http_data_h)(const uint8_t *buf, size_t size,
			   const struct http_msg *msg, void *arg);
typedef void (http_conn_h)(struct tcp_conn *tc, struct tls_conn *sc,
			   void *arg);
typedef size_t (http_bodyh)(struct mbuf *mb, void *arg);

int http_client_alloc(struct http_cli **clip, struct dnsc *dnsc);
int http_client_set_config(struct http_cli *cli, struct http_conf *conf);
int http_request(struct http_req **reqp, struct http_cli *cli, const char *met,
		 const char *uri, http_resp_h *resph, http_data_h *datah,
		 http_bodyh *bodyh, void *arg, const char *fmt, ...);
void http_req_set_conn_handler(struct http_req *req, http_conn_h *connh);
void http_client_set_laddr(struct http_cli *cli, const struct sa *addr);
void http_client_set_laddr6(struct http_cli *cli, const struct sa *addr);
void http_client_set_bufsize_max(struct http_cli *cli, size_t max_size);
size_t http_client_get_bufsize_max(struct http_cli *cli);

#ifdef USE_TLS
int http_client_set_tls(struct http_cli *cli, struct tls *tls);
int http_client_get_tls(struct http_cli *cli, struct tls **tls);
int http_client_add_ca(struct http_cli *cli, const char *tls_ca);
int http_client_add_capem(struct http_cli *cli, const char *capem);
int http_client_add_crlpem(struct http_cli *cli, const char *pem);
int http_client_set_tls_hostname(struct http_cli *cli,
				 const struct pl *hostname);
int http_client_set_cert(struct http_cli *cli, const char *path);
int http_client_set_certpem(struct http_cli *cli, const char *pem);
int http_client_set_key(struct http_cli *cli, const char *path);
int http_client_set_keypem(struct http_cli *cli, const char *pem);

int http_client_set_session_reuse(struct http_cli *cli, bool enabled);
int http_client_set_tls_min_version(struct http_cli *cli, int version);
int http_client_set_tls_max_version(struct http_cli *cli, int version);
#endif

/* Server */
struct http_sock;
struct http_conn;

enum re_https_verify_msg {
	HTTPS_MSG_OK = 0,
	HTTPS_MSG_REQUEST_CERT = 1,
	HTTPS_MSG_IGNORE = 2,
};

typedef void (http_req_h)(struct http_conn *conn, const struct http_msg *msg,
			  void *arg);
typedef enum re_https_verify_msg (https_verify_msg_h)(struct http_conn *conn,
	const struct http_msg *msg, void *arg);

int http_listen_fd(struct http_sock **sockp, re_sock_t fd, http_req_h *reqh,
		   void *arg);
int  http_listen(struct http_sock **sockp, const struct sa *laddr,
		 http_req_h *reqh, void *arg);
int  https_listen(struct http_sock **sockp, const struct sa *laddr,
		  const char *cert, http_req_h *reqh, void *arg);
int  https_set_verify_msgh(struct http_sock *sock,
			   https_verify_msg_h *verifyh);
struct tcp_sock *http_sock_tcp(struct http_sock *sock);
struct tls *http_sock_tls(const struct http_sock *conn);
const struct sa *http_conn_peer(const struct http_conn *conn);
struct tcp_conn *http_conn_tcp(struct http_conn *conn);
struct tls_conn *http_conn_tls(struct http_conn *conn);

void http_conn_reset_timeout(struct http_conn *conn);
void http_conn_close(struct http_conn *conn);
int  http_reply(struct http_conn *conn, uint16_t scode, const char *reason,
		const char *fmt, ...);
int  http_creply(struct http_conn *conn, uint16_t scode, const char *reason,
		 const char *ctype, const char *fmt, ...);
int  http_ereply(struct http_conn *conn, uint16_t scode, const char *reason);


/* Authentication */
struct http_auth {
	const char *realm;
	bool stale;
};

typedef int (http_auth_h)(const struct pl *username, uint8_t *ha1, void *arg);

int  http_auth_print_challenge(struct re_printf *pf,
			       const struct http_auth *auth);
bool http_auth_check(const struct pl *hval, const struct pl *method,
		     struct http_auth *auth, http_auth_h *authh, void *arg);
bool http_auth_check_request(const struct http_msg *msg,
			     struct http_auth *auth,
			     http_auth_h *authh, void *arg);

/* http_reqconn - HTTP request connection */
struct http_reqconn;
int http_reqconn_alloc(struct http_reqconn **pconn,
		struct http_cli *client,
		http_resp_h *resph, http_data_h *datah, void* arg);
int http_reqconn_set_auth(struct http_reqconn *conn, const struct pl *user,
		const struct pl *pass);
int http_reqconn_set_bearer(struct http_reqconn *conn,
		const struct pl *bearer);
int http_reqconn_set_authtoken(struct http_reqconn *conn,
		const struct pl *token);
int http_reqconn_set_tokentype(struct http_reqconn *conn,
		const struct pl *tokentype);
int http_reqconn_set_method(struct http_reqconn *conn, const struct pl *met);
int http_reqconn_set_body(struct http_reqconn *conn, struct mbuf *body);
int http_reqconn_set_ctype(struct http_reqconn *conn, const struct pl *ctype);
int http_reqconn_add_header(struct http_reqconn *conn,
		const struct pl *header);
int http_reqconn_clr_header(struct http_reqconn *conn);
int http_reqconn_send(struct http_reqconn *conn, const struct pl *uri);
#ifdef USE_TLS
int http_reqconn_set_tls_hostname(struct http_reqconn *conn,
		const struct pl *hostname);
#endif

int http_reqconn_set_req_bodyh(struct http_reqconn *conn,
		http_bodyh cb, uint64_t len);
