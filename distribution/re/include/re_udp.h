/**
 * @file re_udp.h  Interface to User Datagram Protocol
 *
 * Copyright (C) 2010 Creytiv.com
 */


struct sa;
struct udp_sock;

typedef int (udp_send_h)(const struct sa *dst,
			 struct mbuf *mb, void *arg);

/**
 * Defines the UDP Receive handler
 *
 * @param src Source address
 * @param mb  Datagram buffer
 * @param arg Handler argument
 */
typedef void (udp_recv_h)(const struct sa *src, struct mbuf *mb, void *arg);
typedef void (udp_error_h)(int err, void *arg);


int  udp_listen(struct udp_sock **usp, const struct sa *local,
		udp_recv_h *rh, void *arg);
int  udp_alloc_sockless(struct udp_sock **usp,
			udp_send_h *sendh, udp_recv_h *recvh, void *arg);
int  udp_alloc_fd(struct udp_sock **usp, re_sock_t fd,
		   udp_recv_h *recvh, void *arg);
int  udp_connect(struct udp_sock *us, const struct sa *peer);
int  udp_open(struct udp_sock **usp, int af);
int  udp_send(struct udp_sock *us, const struct sa *dst, struct mbuf *mb);
int  udp_local_get(const struct udp_sock *us, struct sa *local);
int  udp_setsockopt(struct udp_sock *us, int level, int optname,
		    const void *optval, uint32_t optlen);
int  udp_sockbuf_set(struct udp_sock *us, int size);
void udp_rxsz_set(struct udp_sock *us, size_t rxsz);
void udp_rxbuf_presz_set(struct udp_sock *us, size_t rx_presz);
void udp_handler_set(struct udp_sock *us, udp_recv_h *rh, void *arg);
void udp_error_handler_set(struct udp_sock *us, udp_error_h *eh);
int  udp_thread_attach(struct udp_sock *us);
void udp_thread_detach(struct udp_sock *us);
re_sock_t udp_sock_fd(const struct udp_sock *us, int af);

int  udp_multicast_join(struct udp_sock *us, const struct sa *group);
int  udp_multicast_leave(struct udp_sock *us, const struct sa *group);
int  udp_settos(struct udp_sock *us, uint8_t tos);
void udp_flush(const struct udp_sock *us);
void udp_recv_packet(struct udp_sock *us, const struct sa *src,
		struct mbuf *mb);


/* Helper API */
typedef bool (udp_helper_send_h)(int *err, struct sa *dst,
				 struct mbuf *mb, void *arg);
typedef bool (udp_helper_recv_h)(struct sa *src,
				 struct mbuf *mb, void *arg);

struct udp_helper;


int udp_register_helper(struct udp_helper **uhp, struct udp_sock *us,
			int layer,
			udp_helper_send_h *sh, udp_helper_recv_h *rh,
			void *arg);
int udp_send_helper(struct udp_sock *us, const struct sa *dst,
		    struct mbuf *mb, struct udp_helper *uh);
void udp_recv_helper(struct udp_sock *us, const struct sa *src,
		     struct mbuf *mb, struct udp_helper *uh);
struct udp_helper *udp_helper_find(const struct udp_sock *us, int layer);
