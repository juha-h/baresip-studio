/**
 * @file rem_aumix.h Audio Mixer
 *
 * Copyright (C) 2010 Creytiv.com
 */

struct aumix;
struct aumix_source;

/**
 * Audio mixer frame handler
 *
 * @param sampv Buffer with audio samples
 * @param sampc Number of samples
 * @param arg   Handler argument
 */
typedef void (aumix_frame_h)(const int16_t *sampv, size_t sampc, void *arg);
typedef void (aumix_record_h)(struct auframe *af);
typedef void (aumix_read_h)(struct auframe *af, void *arg);

int aumix_alloc(struct aumix **mixp, uint32_t srate,
		uint8_t ch, uint32_t ptime);
void aumix_recordh(struct aumix *mix, aumix_record_h *recordh);
void aumix_record_sumh(struct aumix *mix, aumix_record_h *recordh);
int aumix_playfile(struct aumix *mix, const char *filepath);
uint32_t aumix_source_count(const struct aumix *mix);
int aumix_source_alloc(struct aumix_source **srcp, struct aumix *mix,
		       aumix_frame_h *fh, void *arg);
void aumix_source_set_id(struct aumix_source *src, uint16_t id);
void aumix_source_enable(struct aumix_source *src, bool enable);
void aumix_source_mute(struct aumix_source *src, bool mute);
int  aumix_source_put(struct aumix_source *src, const int16_t *sampv,
		      size_t sampc);
void aumix_source_readh(struct aumix_source *src, aumix_read_h *readh);
void aumix_source_flush(struct aumix_source *src);
int aumix_debug(struct re_printf *pf, struct aumix *mix);
