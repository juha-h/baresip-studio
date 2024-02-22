/**
 * @file re_main.h  Interface to main polling routine
 *
 * Copyright (C) 2010 Creytiv.com
 */

#include "re_async.h"

struct re;
struct re_fhs;

enum {
#ifndef FD_READ
	FD_READ   = 1<<0,
#endif
#ifndef FD_WRITE
	FD_WRITE  = 1<<1,
#endif
	FD_EXCEPT = 1<<2
};


/**
 * File descriptor event handler
 *
 * @param flags  Event flags
 * @param arg    Handler argument
 */
typedef void (fd_h)(int flags, void *arg);

/**
 * Thread-safe signal handler
 *
 * @param sig Signal number
 */
typedef void (re_signal_h)(int sig);


int   fd_listen(struct re_fhs **fhs, re_sock_t fd, int flags, fd_h *fh,
		void *arg);
struct re_fhs *fd_close(struct re_fhs *fhs);
int   fd_setsize(int maxfds);

int   libre_init(void);
void  libre_close(void);
void  libre_exception_btrace(bool enable);

int   re_main(re_signal_h *signalh);
void  re_cancel(void);
int   re_debug(struct re_printf *pf, void *unused);
int   re_nfds(void);

int  re_alloc(struct re **rep);
int  re_thread_attach(struct re *re);
void re_thread_detach(void);

int  re_thread_init(void);
void re_thread_close(void);
void re_thread_enter(void);
void re_thread_leave(void);
int  re_thread_check(bool debug);
int  re_thread_async_init(uint16_t workers);
void re_thread_async_close(void);
int  re_thread_async(re_async_work_h *work, re_async_h *cb, void *arg);
int  re_thread_async_main(re_async_work_h *work, re_async_h *cb, void *arg);
int  re_thread_async_id(intptr_t id, re_async_work_h *work, re_async_h *cb,
		       void *arg);
int re_thread_async_main_id(intptr_t id, re_async_work_h *work, re_async_h *cb,
			    void *arg);
void re_thread_async_cancel(intptr_t id);
void re_thread_async_main_cancel(intptr_t id);

void re_set_mutex(void *mutexp);

struct tmrl *re_tmrl_get(void);

/** Polling methods */
enum poll_method {
	METHOD_NULL = 0,
	METHOD_SELECT,
	METHOD_EPOLL,
	METHOD_KQUEUE,
	/* sep */
	METHOD_MAX
};

int              poll_method_set(enum poll_method method);
enum poll_method poll_method_get(void);
enum poll_method poll_method_best(void);
const char      *poll_method_name(enum poll_method method);
int poll_method_type(enum poll_method *method, const struct pl *name);
