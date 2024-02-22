/**
 * @file re_sipsess.h  SIP Session
 *
 * Copyright (C) 2010 Creytiv.com
 */

struct sipsess_sock;
struct sipsess;

/** SDP Negotiation state */
enum sdp_neg_state {
	SDP_NEG_NONE = 0,
	SDP_NEG_LOCAL_OFFER,		/**< SDP offer sent */
	SDP_NEG_REMOTE_OFFER,		/**< SDP offer received */
	SDP_NEG_PREVIEW_ANSWER,		/**< SDP preview answer sent */
	SDP_NEG_DONE			/**< SDP negotiation done */
};


typedef void (sipsess_conn_h)(const struct sip_msg *msg, void *arg);
typedef int  (sipsess_desc_h)(struct mbuf **descp, const struct sa *src,
			      const struct sa *dst, void *arg);
typedef int  (sipsess_offer_h)(struct mbuf **descp, const struct sip_msg *msg,
			       void *arg);
typedef int  (sipsess_answer_h)(const struct sip_msg *msg, void *arg);
typedef void (sipsess_progr_h)(const struct sip_msg *msg, void *arg);
typedef void (sipsess_estab_h)(const struct sip_msg *msg, void *arg);
typedef void (sipsess_info_h)(struct sip *sip, const struct sip_msg *msg,
			      void *arg);
typedef void (sipsess_refer_h)(struct sip *sip, const struct sip_msg *msg,
			       void *arg);
typedef void (sipsess_close_h)(int err, const struct sip_msg *msg, void *arg);

typedef void (sipsess_redirect_h)(const struct sip_msg *msg,
				  const char *uri, void *arg);
typedef void (sipsess_prack_h)(const struct sip_msg *msg, void *arg);

int  sipsess_listen(struct sipsess_sock **sockp, struct sip *sip,
		    int htsize, sipsess_conn_h *connh, void *arg);

int  sipsess_connect(struct sipsess **sessp, struct sipsess_sock *sock,
		     const char *to_uri, const char *from_name,
		     const char *from_uri, const char *cuser,
		     const char *routev[], uint32_t routec,
		     const char *ctype,
		     sip_auth_h *authh, void *aarg, bool aref,
		     const char *callid,
		     sipsess_desc_h *desch,
		     sipsess_offer_h *offerh, sipsess_answer_h *answerh,
		     sipsess_progr_h *progrh, sipsess_estab_h *estabh,
		     sipsess_info_h *infoh, sipsess_refer_h *referh,
		     sipsess_close_h *closeh, void *arg, const char *fmt, ...);

int  sipsess_accept(struct sipsess **sessp, struct sipsess_sock *sock,
		    const struct sip_msg *msg, uint16_t scode,
		    const char *reason, enum rel100_mode rel100,
		    const char *cuser, const char *ctype,
		    struct mbuf *desc, sip_auth_h *authh, void *aarg,
		    bool aref, sipsess_offer_h *offerh,
		    sipsess_answer_h *answerh, sipsess_estab_h *estabh,
		    sipsess_info_h *infoh, sipsess_refer_h *referh,
		    sipsess_close_h *closeh, void *arg,
		    const char *fmt, ...);

int  sipsess_set_redirect_handler(struct sipsess *sess,
				  sipsess_redirect_h *redirecth);
int  sipsess_set_prack_handler(struct sipsess *sess, sipsess_prack_h *prackh);

int  sipsess_progress(struct sipsess *sess, uint16_t scode,
		      const char *reason, enum rel100_mode rel100,
		      struct mbuf *desc, const char *fmt, ...);
int  sipsess_answer(struct sipsess *sess, uint16_t scode, const char *reason,
		    struct mbuf *desc, const char *fmt, ...);
int  sipsess_reject(struct sipsess *sess, uint16_t scode, const char *reason,
		    const char *fmt, ...);
int  sipsess_modify(struct sipsess *sess, struct mbuf *desc);
int  sipsess_info(struct sipsess *sess, const char *ctype, struct mbuf *body,
		  sip_resp_h *resph, void *arg);
int  sipsess_set_close_headers(struct sipsess *sess, const char *hdrs, ...);
bool sipsess_awaiting_prack(const struct sipsess *sess);
bool sipsess_refresh_allowed(const struct sipsess *sess);
void sipsess_close_all(struct sipsess_sock *sock);
struct sip_dialog *sipsess_dialog(const struct sipsess *sess);
void sipsess_abort(struct sipsess *sess);
bool sipsess_ack_pending(const struct sipsess *sess);
enum sdp_neg_state sipsess_sdp_neg_state(const struct sipsess *sess);
