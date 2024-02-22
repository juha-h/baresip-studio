/**
 * @file re_sa.h  Interface to Socket Address
 *
 * Copyright (C) 2010 Creytiv.com
 */
#if defined(WIN32)
#include <winsock2.h>
#include <ws2tcpip.h>
#if !defined(UNIX_PATH_MAX)
#define UNIX_PATH_MAX 108
typedef struct sockaddr_un {
	ADDRESS_FAMILY sun_family;
	char sun_path[UNIX_PATH_MAX];
} SOCKADDR_UN, *PSOCKADDR_UN;
#endif
#else
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/un.h>
#endif


struct pl;

/** Socket Address flags */
enum sa_flag {
	SA_ADDR      = 1<<0,
	SA_PORT      = 1<<1,
	SA_ALL       = SA_ADDR | SA_PORT
};

/** Defines a Socket Address */
struct sa {
	union {
		struct sockaddr sa;
		struct sockaddr_in in;
		struct sockaddr_in6 in6;
#if !defined(HAVE_UNIXSOCK) || HAVE_UNIXSOCK == 1
		struct sockaddr_un un;
#endif
	} u;
	socklen_t len;
};

void     sa_init(struct sa *sa, int af);
int      sa_set(struct sa *sa, const struct pl *addr, uint16_t port);
int      sa_set_str(struct sa *sa, const char *addr, uint16_t port);
void     sa_set_in(struct sa *sa, uint32_t addr, uint16_t port);
void     sa_set_in6(struct sa *sa, const uint8_t *addr, uint16_t port);
int      sa_set_sa(struct sa *sa, const struct sockaddr *s);
void     sa_set_port(struct sa *sa, uint16_t port);
int      sa_decode(struct sa *sa, const char *str, size_t len);

int      sa_af(const struct sa *sa);
uint32_t sa_in(const struct sa *sa);
void     sa_in6(const struct sa *sa, uint8_t *addr);
int      sa_addrinfo(const char *addr, struct sa *sa);

int      sa_ntop(const struct sa *sa, char *buf, int size);
int      sa_pton(const char *addr, struct sa *sa);
uint16_t sa_port(const struct sa *sa);
bool     sa_isset(const struct sa *sa, int flag);
uint32_t sa_hash(const struct sa *sa, int flag);

void     sa_cpy(struct sa *dst, const struct sa *src);
bool     sa_cmp(const struct sa *l, const struct sa *r, int flag);

bool     sa_is_linklocal(const struct sa *sa);
bool     sa_is_loopback(const struct sa *sa);
bool     sa_is_any(const struct sa *sa);

void     sa_set_scopeid(struct sa *sa, uint32_t scopeid);
uint32_t sa_scopeid(const struct sa *sa);
size_t   sa_struct_get_size(void);

struct re_printf;
int      sa_print_addr(struct re_printf *pf, const struct sa *sa);
