/**
 * @file re_fmt.h  Interface to formatted text functions
 *
 * Copyright (C) 2010 Creytiv.com
 */


#include <stdarg.h>
#include <stdio.h>

enum {
	ITOA_BUFSZ = 34,
};

struct mbuf;


/** Defines a pointer-length string type */
struct pl {
	const char *p;  /**< Pointer to string */
	size_t l;       /**< Length of string  */
};

/** Initialise a pointer-length object from a constant string */
#define PL(s) {(s), sizeof((s))-1}

/** Pointer-length Initializer */
#define PL_INIT {NULL, 0}

extern const struct pl pl_null;

struct pl *pl_alloc_str(const char *str);
void     pl_set_str(struct pl *pl, const char *str);
void     pl_set_mbuf(struct pl *pl, const struct mbuf *mb);
int32_t  pl_i32(const struct pl *pl);
int64_t  pl_i64(const struct pl *pl);
uint32_t pl_u32(const struct pl *pl);
uint32_t pl_x32(const struct pl *pl);
uint64_t pl_u64(const struct pl *pl);
uint64_t pl_x64(const struct pl *pl);
double   pl_float(const struct pl *pl);
int      pl_bool(bool *val, const struct pl *pl);
int      pl_hex(const struct pl *pl, uint8_t *hex, size_t len);
bool     pl_isset(const struct pl *pl);
int      pl_strcpy(const struct pl *pl, char *str, size_t size);
int      pl_strdup(char **dst, const struct pl *src);
int      pl_dup(struct pl *dst, const struct pl *src);
int      pl_strcmp(const struct pl *pl, const char *str);
int      pl_strcasecmp(const struct pl *pl, const char *str);
int      pl_cmp(const struct pl *pl1, const struct pl *pl2);
int      pl_casecmp(const struct pl *pl1, const struct pl *pl2);
const char *pl_strchr(const struct pl *pl, char c);
const char *pl_strrchr(const struct pl *pl, char c);
const char *pl_strstr(const struct pl *pl, const char *str);
int      pl_trim(struct pl *pl);
int      pl_ltrim(struct pl *pl);
int      pl_rtrim(struct pl *pl);

/** Advance pl position/length by +/- N bytes */
static inline void pl_advance(struct pl *pl, ssize_t n)
{
	pl->p += n;
	pl->l -= n;
}


/* Formatted printing */

/**
 * Defines the re_vhprintf() print handler
 *
 * @param p    String to print
 * @param size Size of string to print
 * @param arg  Handler argument
 *
 * @return 0 for success, otherwise errorcode
 */
typedef int(re_vprintf_h)(const char *p, size_t size, void *arg);

/** Defines a print backend */
struct re_printf {
	re_vprintf_h *vph; /**< Print handler   */
	void *arg;         /**< Handler argument */
};

/**
 * Defines the %H print handler
 *
 * @param pf  Print backend
 * @param arg Handler argument
 *
 * @return 0 for success, otherwise errorcode
 */
typedef int(re_printf_h)(struct re_printf *pf, void *arg);

int re_vhprintf(const char *fmt, va_list ap, re_vprintf_h *vph, void *arg);
int re_vfprintf(FILE *stream, const char *fmt, va_list ap);
int re_vprintf(const char *fmt, va_list ap);
int re_vsnprintf(char *re_restrict str, size_t size,
		 const char *re_restrict fmt, va_list ap);
int re_vsdprintf(char **strp, const char *fmt, va_list ap);

/* Secure va_list print */
int re_vhprintf_s(const char *fmt, va_list ap, re_vprintf_h *vph, void *arg);
int re_vfprintf_s(FILE *stream, const char *fmt, va_list ap);
int re_vprintf_s(const char *fmt, va_list ap);
int re_vsnprintf_s(char *re_restrict str, size_t size,
		 const char *re_restrict fmt, va_list ap);
int re_vsdprintf_s(char **strp, const char *fmt, va_list ap);

#ifdef HAVE_RE_ARG
#define re_printf(fmt, ...) _re_printf_s((fmt), RE_VA_ARGS(__VA_ARGS__))
#define re_hprintf(pf, fmt, ...)                                              \
	_re_hprintf_s((pf), (fmt), RE_VA_ARGS(__VA_ARGS__))
#define re_fprintf(stream, fmt, ...)                                          \
	_re_fprintf_s((stream), (fmt), RE_VA_ARGS(__VA_ARGS__))
#define re_snprintf(str, size, fmt, ...)                                      \
	_re_snprintf_s((str), (size), (fmt), RE_VA_ARGS(__VA_ARGS__))
#define re_sdprintf(strp, fmt, ...)                                           \
	_re_sdprintf_s((strp), (fmt), RE_VA_ARGS(__VA_ARGS__))
#else
#define re_printf(...) _re_printf(__VA_ARGS__)
#define re_hprintf _re_hprintf
#define re_fprintf _re_fprintf
#define re_snprintf _re_snprintf
#define re_sdprintf _re_sdprintf
#endif

int _re_printf(const char *fmt, ...);
int _re_hprintf(struct re_printf *pf, const char *fmt, ...);
int _re_fprintf(FILE *stream, const char *fmt, ...);
int _re_snprintf(char *re_restrict str, size_t size,
		 const char *re_restrict fmt, ...);
int _re_sdprintf(char **strp, const char *fmt, ...);

int _re_printf_s(const char *fmt, ...);
int _re_hprintf_s(struct re_printf *pf, const char *fmt, ...);
int _re_fprintf_s(FILE *stream, const char *fmt, ...);
int _re_snprintf_s(char *re_restrict str, size_t size,
		   const char *re_restrict fmt, ...);
int _re_sdprintf_s(char **strp, const char *fmt, ...);


/* Regular expressions */
int re_regex(const char *ptr, size_t len, const char *expr, ...);


/* Character functions */
uint8_t ch_hex(char ch);


/* String functions */
int  str_bool(bool *val, const char *str);
int  str_hex(uint8_t *hex, size_t len, const char *str);
void str_ncpy(char *dst, const char *src, size_t n);
int  str_dup(char **dst, const char *src);
int  str_x64dup(char **dst, uint64_t val);
int  str_cmp(const char *s1, const char *s2);
int  str_ncmp(const char *s1, const char *s2, size_t n);
const char *str_str(const char *s1, const char *s2);
int  str_casecmp(const char *s1, const char *s2);
size_t str_len(const char *s);
const char *str_error(int errnum, char *buf, size_t sz);
char *str_itoa(uint32_t val, char *buf, int base);
wchar_t *str_wchar(const char *c);


/**
 * Check if string is set
 *
 * @param s Zero-terminated string
 *
 * @return true if set, false if not set
 */
static inline bool str_isset(const char *s)
{
	return s && s[0] != '\0';
}


/* time */
int fmt_gmtime(struct re_printf *pf, void *ts);
int fmt_timestamp(struct re_printf *pf, void *ts);
int fmt_timestamp_us(struct re_printf *pf, void *arg);
int fmt_human_time(struct re_printf *pf, const uint32_t *seconds);


void hexdump(FILE *f, const void *p, size_t len);


/* param */
typedef void (fmt_param_h)(const struct pl *name, const struct pl *val,
			   void *arg);

bool fmt_param_exists(const struct pl *pl, const char *pname);
bool fmt_param_sep_get(const struct pl *pl, const char *pname, char sep,
		struct pl *val);
bool fmt_param_get(const struct pl *pl, const char *pname, struct pl *val);
void fmt_param_apply(const struct pl *pl, fmt_param_h *ph, void *arg);


/* unicode */
int utf8_encode(struct re_printf *pf, const char *str);
int utf8_decode(struct re_printf *pf, const struct pl *pl);
size_t utf8_byteseq(char u[4], unsigned cp);


/* text2pcap */
struct re_text2pcap {
	bool in;
	const struct mbuf *mb;
	const char *id;
};

int re_text2pcap(struct re_printf *pf, struct re_text2pcap *pcap);
void re_text2pcap_trace(const char *name, const char *id, bool in,
			const struct mbuf *mb);
