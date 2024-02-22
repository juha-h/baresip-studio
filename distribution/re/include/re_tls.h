/**
 * @file re_tls.h  Interface to Transport Layer Security
 *
 * Copyright (C) 2010 Creytiv.com
 */

struct tls;
struct tls_conn;
struct tcp_conn;
struct udp_sock;


/** Defines the TLS method */
enum tls_method {
	TLS_METHOD_TLS,
	TLS_METHOD_SSLV23,    /* deprecated - fallback to TLS_METHOD_TLS */
	TLS_METHOD_DTLS,      /* DTLS 1.0 and 1.2 */
	TLS_METHOD_DTLSV1,    /* deprecated - fallback to TLS_METHOD_DTLS */
	TLS_METHOD_DTLSV1_2,  /* deprecated - fallback to TLS_METHOD_DTLS */
};

enum tls_fingerprint {
	TLS_FINGERPRINT_SHA256,
};

enum tls_keytype {
	TLS_KEYTYPE_RSA,
	TLS_KEYTYPE_EC,
};

struct tls_conn_d {
	int (*verifyh) (int ok, void *arg);
	void *arg;
};

int tls_alloc(struct tls **tlsp, enum tls_method method, const char *keyfile,
	      const char *pwd);
int tls_add_ca(struct tls *tls, const char *cafile);
int tls_add_cafile_path(struct tls *tls, const char *cafile,
	const char *capath);
int tls_add_capem(const struct tls *tls, const char *capem);
int tls_add_crlpem(const struct tls *tls, const char *pem);
int tls_set_selfsigned_rsa(struct tls *tls, const char *cn, size_t bits);
int tls_set_selfsigned_ec(struct tls *tls, const char *cn,
	const char *curve_n);
int tls_set_certificate_pem(struct tls *tls, const char *cert, size_t len_cert,
			    const char *key, size_t len_key);
int tls_set_certificate_der(struct tls *tls, enum tls_keytype keytype,
			    const uint8_t *cert, size_t len_cert,
			    const uint8_t *key, size_t len_key);
int tls_set_certificate(struct tls *tls, const char *cert, size_t len);
void tls_set_verify_client(struct tls *tls);
void tls_set_verify_client_trust_all(struct tls *tls);
int tls_set_verify_client_handler(struct tls_conn *tc, int depth,
	int (*verifyh) (int ok, void *arg), void *arg);

int tls_set_srtp(struct tls *tls, const char *suites);
int tls_fingerprint(const struct tls *tls, enum tls_fingerprint type,
		    uint8_t *md, size_t size);

int tls_peer_fingerprint(const struct tls_conn *tc, enum tls_fingerprint type,
			 uint8_t *md, size_t size);
int tls_peer_common_name(const struct tls_conn *tc, char *cn, size_t size);
int tls_set_verify_purpose(struct tls *tls, const char *purpose);
int tls_peer_verify(const struct tls_conn *tc);
int tls_srtp_keyinfo(const struct tls_conn *tc, enum srtp_suite *suite,
		     uint8_t *cli_key, size_t cli_key_size,
		     uint8_t *srv_key, size_t srv_key_size);
const char *tls_cipher_name(const struct tls_conn *tc);
int tls_set_ciphers(struct tls *tls, const char *cipherv[], size_t count);
int tls_set_verify_server(struct tls_conn *tc, const char *host);
int tls_verify_client(struct tls_conn *tc);

int tls_get_issuer(struct tls *tls, struct mbuf *mb);
int tls_get_subject(struct tls *tls, struct mbuf *mb);
void tls_disable_verify_server(struct tls *tls);
void tls_enable_verify_client(struct tls *tls, bool enable);

int tls_set_min_proto_version(struct tls *tls, int version);
int tls_set_max_proto_version(struct tls *tls, int version);

int tls_set_session_reuse(struct tls *tls, int enabled);
bool tls_get_session_reuse(const struct tls_conn *tc);
int tls_reuse_session(const struct tls_conn *tc);
bool tls_session_reused(const struct tls_conn *tc);
int tls_update_sessions(const struct tls_conn *tc);
void tls_set_posthandshake_auth(struct tls *tls, int value);

/* TCP */

int tls_conn_change_cert(struct tls_conn *tc, const char *file);
int tls_start_tcp(struct tls_conn **ptc, struct tls *tls,
		  struct tcp_conn *tcp, int layer);

int tls_verify_client_post_handshake(struct tls_conn *tc);

const struct tcp_conn *tls_get_tcp_conn(const struct tls_conn *tc);


/* UDP (DTLS) */

typedef void (dtls_conn_h)(const struct sa *peer, void *arg);
typedef void (dtls_estab_h)(void *arg);
typedef void (dtls_recv_h)(struct mbuf *mb, void *arg);
typedef void (dtls_close_h)(int err, void *arg);

struct dtls_sock;

int dtls_listen(struct dtls_sock **sockp, const struct sa *laddr,
		struct udp_sock *us, uint32_t htsize, int layer,
		dtls_conn_h *connh, void *arg);
struct udp_sock *dtls_udp_sock(struct dtls_sock *sock);
void dtls_set_mtu(struct dtls_sock *sock, size_t mtu);
int dtls_connect(struct tls_conn **ptc, struct tls *tls,
		 struct dtls_sock *sock, const struct sa *peer,
		 dtls_estab_h *estabh, dtls_recv_h *recvh,
		 dtls_close_h *closeh, void *arg);
int dtls_accept(struct tls_conn **ptc, struct tls *tls,
		struct dtls_sock *sock,
		dtls_estab_h *estabh, dtls_recv_h *recvh,
		dtls_close_h *closeh, void *arg);
int dtls_send(struct tls_conn *tc, struct mbuf *mb);
void dtls_set_handlers(struct tls_conn *tc, dtls_estab_h *estabh,
		       dtls_recv_h *recvh, dtls_close_h *closeh, void *arg);
const struct sa *dtls_peer(const struct tls_conn *tc);
void dtls_set_peer(struct tls_conn *tc, const struct sa *peer);
void dtls_recv_packet(struct dtls_sock *sock, const struct sa *src,
		      struct mbuf *mb);
void dtls_set_single(struct dtls_sock *sock, bool single);


struct x509_st;
struct evp_pkey_st;

int tls_set_certificate_openssl(struct tls *tls, struct x509_st *cert,
				struct evp_pkey_st *pkey, bool up_ref);
int tls_add_certf(struct tls *tls, const char *certf, const char *host);
