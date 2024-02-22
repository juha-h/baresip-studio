/**
 * @file re_mbuf.h  Interface to memory buffers
 *
 * Copyright (C) 2010 Creytiv.com
 */


#include <stdarg.h>


#ifndef RELEASE
#define MBUF_DEBUG 1  /**< Mbuf debugging (0 or 1) */
#endif

#if MBUF_DEBUG
/** Check that mbuf position does not exceed end */
#define MBUF_CHECK_POS(mb)						\
	if ((mb) && (mb)->pos > (mb)->end) {				\
		RE_BREAKPOINT;						\
	}
/** Check that mbuf end does not exceed size */
#define MBUF_CHECK_END(mb)						\
	if ((mb) && (mb)->end > (mb)->size) {				\
		RE_BREAKPOINT;						\
	}
#else
#define MBUF_CHECK_POS(mb)
#define MBUF_CHECK_END(mb)
#endif

/**
 * Defines a memory buffer.
 *
 * This is a dynamic and linear buffer for storing raw bytes.
 * It is designed for network protocols, and supports automatic
 * resizing of the buffer.
 *
 * - Writing to the buffer
 * - Reading from the buffer
 * - Automatic growing of buffer size
 * - Print function for formatting printing
 */
struct mbuf {
	uint8_t *buf;   /**< Buffer memory      */
	size_t size;    /**< Size of buffer     */
	size_t pos;     /**< Position in buffer */
	size_t end;     /**< End of buffer      */
};


struct pl;
struct re_printf;

struct mbuf *mbuf_alloc(size_t size);
struct mbuf *mbuf_dup(struct mbuf *mbd);
struct mbuf *mbuf_alloc_ref(struct mbuf *mbr);
void     mbuf_init(struct mbuf *mb);
void     mbuf_reset(struct mbuf *mb);
int      mbuf_resize(struct mbuf *mb, size_t size);
void     mbuf_trim(struct mbuf *mb);
int      mbuf_shift(struct mbuf *mb, ssize_t shift);
int      mbuf_write_mem(struct mbuf *mb, const uint8_t *buf, size_t size);
int      mbuf_write_ptr(struct mbuf *mb, intptr_t v);
int      mbuf_write_u8(struct mbuf *mb, uint8_t v);
int      mbuf_write_u16(struct mbuf *mb, uint16_t v);
int      mbuf_write_u32(struct mbuf *mb, uint32_t v);
int      mbuf_write_u64(struct mbuf *mb, uint64_t v);
int      mbuf_write_str(struct mbuf *mb, const char *str);
int      mbuf_write_pl(struct mbuf *mb, const struct pl *pl);
int      mbuf_read_mem(struct mbuf *mb, uint8_t *buf, size_t size);
intptr_t mbuf_read_ptr(struct mbuf *mb);
uint8_t  mbuf_read_u8(struct mbuf *mb);
uint16_t mbuf_read_u16(struct mbuf *mb);
uint32_t mbuf_read_u32(struct mbuf *mb);
uint64_t mbuf_read_u64(struct mbuf *mb);
int      mbuf_read_str(struct mbuf *mb, char *str, size_t size);
int      mbuf_strdup(struct mbuf *mb, char **strp, size_t len);
int      mbuf_vprintf(struct mbuf *mb, const char *fmt, va_list ap);

#ifdef HAVE_RE_ARG
#define mbuf_printf(mb, fmt, ...)                                             \
	_mbuf_printf_s((mb), (fmt), RE_VA_ARGS(__VA_ARGS__))
#else
#define mbuf_printf _mbuf_printf
#endif

int      _mbuf_printf(struct mbuf *mb, const char *fmt, ...);
int      _mbuf_printf_s(struct mbuf *mb, const char *fmt, ...);

int      mbuf_write_pl_skip(struct mbuf *mb, const struct pl *pl,
			    const struct pl *skip);
int      mbuf_fill(struct mbuf *mb, uint8_t c, size_t n);
void     mbuf_set_posend(struct mbuf *mb, size_t pos, size_t end);
int      mbuf_debug(struct re_printf *pf, const struct mbuf *mb);


/**
 * Get the buffer from the current position
 *
 * @param mb Memory buffer
 *
 * @return Current buffer
 */
static inline uint8_t *mbuf_buf(const struct mbuf *mb)
{
	return mb ? mb->buf + mb->pos : (uint8_t *)NULL;
}


/**
 * Get number of bytes left in a memory buffer, from current position to end
 *
 * @param mb Memory buffer
 *
 * @return Number of bytes left
 */
static inline size_t mbuf_get_left(const struct mbuf *mb)
{
	return (mb && (mb->end > mb->pos)) ? (mb->end - mb->pos) : 0;
}


/**
 * Get available space in buffer (size - pos)
 *
 * @param mb Memory buffer
 *
 * @return Number of bytes available in buffer
 */
static inline size_t mbuf_get_space(const struct mbuf *mb)
{
	return (mb && (mb->size > mb->pos)) ? (mb->size - mb->pos) : 0;
}


/**
 * Set absolute position
 *
 * @param mb  Memory buffer
 * @param pos Position
 */
static inline void mbuf_set_pos(struct mbuf *mb, size_t pos)
{
	mb->pos = pos;
	MBUF_CHECK_POS(mb);
}


/**
 * Set absolute end
 *
 * @param mb  Memory buffer
 * @param end End position
 */
static inline void mbuf_set_end(struct mbuf *mb, size_t end)
{
	mb->end = end;
	MBUF_CHECK_END(mb);
}


/**
 * Advance position +/- N bytes
 *
 * @param mb  Memory buffer
 * @param n   Number of bytes to advance
 */
static inline void mbuf_advance(struct mbuf *mb, ssize_t n)
{
	mb->pos += n;
	MBUF_CHECK_POS(mb);
}


/**
 * Rewind position and end to the beginning of buffer
 *
 * @param mb  Memory buffer
 */
static inline void mbuf_rewind(struct mbuf *mb)
{
	mb->pos = mb->end = 0;
}


/**
 * Set position to the end of the buffer
 *
 * @param mb  Memory buffer
 */
static inline void mbuf_skip_to_end(struct mbuf *mb)
{
	mb->pos = mb->end;
}


/**
 * Get the current MBUF position
 *
 * @param mb Memory buffer
 *
 * @return Current position
 */
static inline size_t mbuf_pos(const struct mbuf *mb)
{
	return mb ? mb->pos : 0;
}


/**
 * Get the current MBUF end position
 *
 * @param mb Memory buffer
 *
 * @return Current end position
 */
static inline size_t mbuf_end(const struct mbuf *mb)
{
	return mb ? mb->end : 0;
}
