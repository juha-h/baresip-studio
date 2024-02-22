/**
 * @file re_httpauth.h  Interface to HTTP Authentication
 *
 * Copyright (C) 2010 Creytiv.com
 */


/** HTTP digest request challenge */
struct httpauth_digest_chall_req {
	char *realm;
	char *domain;
	char *nonce;
	char *opaque;
	bool stale;
	char *algorithm;
	char *qop;

	/* optional */
	char *charset;
	bool userhash;
};

/** HTTP Digest Challenge */
struct httpauth_digest_chall {
	struct pl realm;
	struct pl nonce;

	/* optional */
	struct pl opaque;
	struct pl stale;
	struct pl algorithm;
	struct pl qop;
	struct pl domain;
	struct pl charset;
	struct pl userhash;
};

struct httpauth_digest_enc_resp {
	char *realm;
	char *nonce;
	char *opaque;
	char *algorithm;
	char *qop;

	/* response specific */
	char *response;
	char *username;
	char *username_star;
	char *uri;
	uint32_t cnonce;
	uint32_t nc;

	/* optional */
	char *charset;
	bool userhash;
	void (*hashh)(const uint8_t *, size_t, uint8_t *);
	size_t hash_length;
};

/** HTTP Digest response */
struct httpauth_digest_resp {
	struct pl realm;
	struct pl nonce;
	struct pl response;
	struct pl username;
	struct pl uri;

	/* optional */
	struct pl nc;
	struct pl cnonce;
	struct pl qop;

	struct pl algorithm;
	struct pl charset;
	struct pl userhash;
	void (*hashh)(const uint8_t *, size_t, uint8_t *);
	size_t hash_length;

	struct mbuf *mb;
};


/** HTTP Basic */
struct httpauth_basic {
	struct mbuf *mb;
	struct pl realm;
	struct pl auth;
};

struct httpauth_basic_req {
	char *realm;

	/* optional */
	char *charset;
};


int httpauth_digest_challenge_decode(struct httpauth_digest_chall *chall,
				     const struct pl *hval);
int httpauth_digest_response_decode(struct httpauth_digest_resp *resp,
				    const struct pl *hval);
int httpauth_digest_response_auth(const struct httpauth_digest_resp *resp,
				  const struct pl *method, const uint8_t *ha1);
int httpauth_digest_make_response(struct httpauth_digest_resp **resp,
		const struct httpauth_digest_chall *chall,
		const char *path, const char *method, const char *user,
		const char *pwd, struct mbuf *body);
int httpauth_digest_response_encode(const struct httpauth_digest_resp *resp,
				  struct mbuf *mb);


int httpauth_digest_response_print(struct re_printf *pf,
	const struct httpauth_digest_enc_resp *resp);
int httpauth_digest_response_set_cnonce(struct httpauth_digest_enc_resp *resp,
	const struct httpauth_digest_chall *chall, const struct pl *method,
	const char *user,	const char *passwd, const char *entitybody,
	const uint32_t cnonce, const uint32_t nc_);
int httpauth_digest_response(struct httpauth_digest_enc_resp **presp,
	const struct httpauth_digest_chall *chall, const struct pl *method,
	const char *uri, const char *user, const char *passwd, const char *qop,
	const char *entitybody);
int httpauth_digest_response_full(struct httpauth_digest_enc_resp **presp,
	const struct httpauth_digest_chall *chall, const struct pl *method,
	const char *uri, const char *user, const char *passwd, const char *qop,
	const char *entitybody, const char *charset, const bool userhash);
int httpauth_digest_verify(struct httpauth_digest_chall_req *req,
	const struct pl *hval, const struct pl *method, const char *etag,
	const char *user, const char *passwd, const char *entitybody);

int httpauth_digest_chall_req_print(struct re_printf *pf,
	const struct httpauth_digest_chall_req *req);
int httpauth_digest_chall_request(struct httpauth_digest_chall_req **preq,
	const char *realm, const char *etag, const char *qop);
int httpauth_digest_chall_request_full(struct httpauth_digest_chall_req **preq,
	const char *real, const char *domain, const char *etag,
	const char *opaque, const bool stale, const char *algo,
	const char *qop, const char *charset, const bool userhash);

struct httpauth_basic *httpauth_basic_alloc(void);
int httpauth_basic_decode(struct httpauth_basic *basic,
		const struct pl *hval);
int httpauth_basic_make_response(struct httpauth_basic *basic,
		const char *user, const char *pwd);
int httpauth_basic_encode(const struct httpauth_basic *basic, struct mbuf *mb);


int httpauth_basic_request_print(struct re_printf *pf,
	const struct httpauth_basic_req *req);
int httpauth_basic_verify(const struct pl *hval, const char *user,
	const char *passwd);
int httpauth_basic_request(struct httpauth_basic_req **preq,
	const char *realm, const char *charset);
