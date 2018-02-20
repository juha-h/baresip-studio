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
 * @param buf Buffer with audio samples
 * @param sz  Number of bytes
 * @param arg Handler argument
 */
typedef void (aumix_frame_h)(const int16_t *sampv, size_t sampc, void *arg);

int aumix_alloc(struct aumix **mixp, uint32_t srate,
		uint8_t ch, uint32_t ptime);
int aumix_playfile(struct aumix *mix, const char *filepath);
uint32_t aumix_source_count(const struct aumix *mix);
int aumix_source_alloc(struct aumix_source **srcp, struct aumix *mix,
		       aumix_frame_h *fh, void *arg);
void aumix_source_enable(struct aumix_source *src, bool enable);
int  aumix_source_put(struct aumix_source *src, const int16_t *sampv,
		      size_t sampc);
void aumix_source_flush(struct aumix_source *src);
