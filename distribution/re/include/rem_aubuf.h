/**
 * @file rem_aubuf.h Audio Buffer
 *
 * Copyright (C) 2010 Creytiv.com
 */
struct aubuf;

enum aubuf_mode {
	AUBUF_FIXED,
	AUBUF_ADAPTIVE
};

int  aubuf_alloc(struct aubuf **abp, size_t min_sz, size_t max_sz);
void aubuf_set_live(struct aubuf *ab, bool live);
void aubuf_set_mode(struct aubuf *ab, enum aubuf_mode mode);
void aubuf_set_silence(struct aubuf *ab, double silence);
int  aubuf_resize(struct aubuf *ab, size_t min_sz, size_t max_sz);
int  aubuf_write_auframe(struct aubuf *ab, const struct auframe *af);
int  aubuf_append_auframe(struct aubuf *ab, struct mbuf *mb,
			  const struct auframe *af);
void aubuf_read_auframe(struct aubuf *ab, struct auframe *af);
void aubuf_sort_auframe(struct aubuf *ab);
int  aubuf_get(struct aubuf *ab, uint32_t ptime, uint8_t *p, size_t sz);
void aubuf_flush(struct aubuf *ab);
int  aubuf_debug(struct re_printf *pf, const struct aubuf *ab);
size_t aubuf_cur_size(const struct aubuf *ab);
size_t aubuf_maxsz(const struct aubuf *ab);
bool aubuf_started(const struct aubuf *ab);
void aubuf_drop_auframe(struct aubuf *ab, const struct auframe *af);


static inline int aubuf_append(struct aubuf *ab, struct mbuf *mb)
{
	return aubuf_append_auframe(ab, mb, NULL);
}


static inline int aubuf_get_samp(struct aubuf *ab, uint32_t ptime,
				 int16_t *sampv, size_t sampc)
{
	return aubuf_get(ab, ptime, (uint8_t *)sampv, sampc * 2);
}


#ifndef __cplusplus
static inline int aubuf_write(struct aubuf *ab, const uint8_t *p, size_t sz)
{
	struct auframe af = {
		.fmt = AUFMT_RAW,
		.srate = 0,
		.sampv = (uint8_t *)p,
		.sampc = sz,
		.timestamp = 0,
		.level = AULEVEL_UNDEF
	};

	return aubuf_write_auframe(ab, &af);
}


static inline int aubuf_write_samp(struct aubuf *ab, const int16_t *sampv,
				   size_t sampc)
{
	struct auframe af = {
		.fmt = AUFMT_S16LE,
		.srate = 0,
		.sampv = (uint8_t *)sampv,
		.sampc = sampc,
		.timestamp = 0,
		.level = AULEVEL_UNDEF
	};

	return aubuf_write_auframe(ab, &af);
}


static inline void aubuf_read(struct aubuf *ab, uint8_t *p, size_t sz)
{
	struct auframe af = {
		.fmt = AUFMT_RAW,
		.srate = 0,
		.sampv = p,
		.sampc = sz,
		.timestamp = 0,
		.level = AULEVEL_UNDEF
	};

	aubuf_read_auframe(ab, &af);
}


static inline void aubuf_read_samp(struct aubuf *ab, int16_t *sampv,
				   size_t sampc)
{
	struct auframe af = {
		.fmt = AUFMT_S16LE,
		.srate = 0,
		.sampv = (uint8_t *)sampv,
		.sampc = sampc,
		.timestamp = 0,
		.level = AULEVEL_UNDEF
	};

	aubuf_read_auframe(ab, &af);
}
#endif
