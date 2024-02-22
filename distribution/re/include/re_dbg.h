/**
 * @file re_dbg.h  Interface to debugging module
 *
 * Copyright (C) 2010 Creytiv.com
 */

#ifdef __cplusplus
extern "C" {
#endif

/** Debug levels */
enum {
	DBG_EMERG       = 0,       /**< System is unusable               */
	DBG_ALERT       = 1,       /**< Action must be taken immediately */
	DBG_CRIT        = 2,       /**< Critical conditions              */
	DBG_ERR         = 3,       /**< Error conditions                 */
	DBG_WARNING     = 4,       /**< Warning conditions               */
	DBG_NOTICE      = 5,       /**< Normal but significant condition */
	DBG_INFO        = 6,       /**< Informational                    */
	DBG_DEBUG       = 7        /**< Debug-level messages             */
};


/**
 * @def DEBUG_MODULE
 *
 * Module name defined by application
 */

/**
 * @def DEBUG_LEVEL
 *
 * Debug level defined by application
 */

#ifndef DEBUG_MODULE
# warning "DEBUG_MODULE is not defined"
#define DEBUG_MODULE "?"
#endif

#ifndef DEBUG_LEVEL
# warning "DEBUG_LEVEL is not defined"
#define DEBUG_LEVEL 7
#endif


/**
 * @def DEBUG_WARNING(...)
 *
 * Print warning message
 */
#if (DEBUG_LEVEL >= 4)
#define DEBUG_WARNING(...) \
	dbg_printf(DBG_WARNING, DEBUG_MODULE ": " __VA_ARGS__)
#else
#define DEBUG_WARNING(...)
#endif

/**
 * @def DEBUG_NOTICE(...)
 *
 * Print notice message
 */
#if (DEBUG_LEVEL >= 5)
#define DEBUG_NOTICE(...) \
	dbg_printf(DBG_NOTICE, DEBUG_MODULE ": " __VA_ARGS__)
#else
#define DEBUG_NOTICE(...)
#endif

/**
 * @def DEBUG_INFO(...)
 *
 * Print info message
 */
#if (DEBUG_LEVEL >= 6)
#define DEBUG_INFO(...) \
	dbg_printf(DBG_INFO, DEBUG_MODULE ": " __VA_ARGS__)
#else
#define DEBUG_INFO(...)
#endif

/**
 * @def DEBUG_PRINTF(...)
 *
 * Print debug message
 */
#if (DEBUG_LEVEL >= 7)
#define DEBUG_PRINTF(...) \
	dbg_printf(DBG_DEBUG, DEBUG_MODULE ": " __VA_ARGS__)
#else
#define DEBUG_PRINTF(...)
#endif


/** Debug flags */
enum dbg_flags {
	DBG_NONE = 0,                 /**< No debug flags         */
	DBG_TIME = 1<<0,              /**< Print timestamp flag   */
	DBG_ANSI = 1<<1,              /**< Print ANSI color codes */
	DBG_ALL  = DBG_TIME|DBG_ANSI  /**< All flags enabled      */
};


/**
 * Defines the debug print handler
 *
 * @param level Debug level
 * @param p     Debug string
 * @param len   String length
 * @param arg   Handler argument
 */
typedef void (dbg_print_h)(int level, const char *p, size_t len, void *arg);

void dbg_init(int level, enum dbg_flags flags);
void dbg_close(void);
int  dbg_logfile_set(const char *name);
void dbg_handler_set(dbg_print_h *ph, void *arg);
void dbg_printf(int level, const char *fmt, ...);
const char *dbg_level_str(int level);

#ifdef __cplusplus
}
#endif
