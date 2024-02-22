/**
 * @file re_tmr.h  Interface to timer implementation
 *
 * Copyright (C) 2010 Creytiv.com
 */


#include "re_thread.h"
#include "re_atomic.h"

/**
 * Defines the timeout handler
 *
 * @param arg Handler argument
 */
typedef void (tmr_h)(void *arg);

struct tmrl;

/** Defines a timer */
struct tmr {
	struct le le;       /**< Linked list element */
	RE_ATOMIC bool active; /**< Timer is active  */
	mtx_t *llock;       /**< List Mutex lock     */
	tmr_h *th;          /**< Timeout handler     */
	void *arg;          /**< Handler argument    */
	uint64_t jfs;       /**< Jiffies for timeout */
	const char *file;
	int line;
};

int      tmrl_alloc(struct tmrl **tmrl);
void     tmr_poll(struct tmrl *tmrl);
uint64_t tmr_jiffies_usec(void);
uint64_t tmr_jiffies(void);
uint64_t tmr_jiffies_rt_usec(void);
int      tmr_timespec_get(struct timespec *tp, uint64_t offset);
uint64_t tmr_next_timeout(struct tmrl *tmrl);
void     tmr_debug(void);
int      tmr_status(struct re_printf *pf, void *unused);

void     tmr_init(struct tmr *tmr);
void     tmr_start_dbg(struct tmr *tmr, uint64_t delay, tmr_h *th, void *arg,
		   const char *file, int line);
void     tmr_continue_dbg(struct tmr *tmr, uint64_t delay,
                   tmr_h *th, void *arg,
		   const char *file, int line);
uint32_t tmrl_count(struct tmrl *tmrl);


/**
 * @def tmr_start(tmr, delay, th, arg)
 *
 * Start a timer
 *
 * @param tmr   Timer to start
 * @param delay Timer delay in [ms]
 * @param th    Timeout handler
 * @param arg   Handler argument
 */
#define tmr_start(tmr, delay, th, arg)                                        \
	tmr_start_dbg(tmr, delay, th, arg, __FILE__, __LINE__)

/**
 * @def tmr_continue(tmr, delay, th, arg)
 *
 * Continue a previously started timer with exactly added delay
 *
 * @param tmr   Timer to start
 * @param delay Timer delay in [ms]
 * @param th    Timeout handler
 * @param arg   Handler argument
 */
#define tmr_continue(tmr, delay, th, arg)                                     \
	tmr_continue_dbg(tmr, delay, th, arg, __FILE__, __LINE__)

void     tmr_cancel(struct tmr *tmr);
uint64_t tmr_get_expire(const struct tmr *tmr);


/**
 * Check if the timer is running
 *
 * @param tmr Timer to check
 *
 * @return true if running, false if not running
 */
static inline bool tmr_isrunning(const struct tmr *tmr)
{
	return tmr ? NULL != tmr->th : false;
}
