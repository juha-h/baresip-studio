/**
 * @file rem_vidmix.h  Video Mixer
 *
 * Copyright (C) 2010 Creytiv.com
 */


struct vidmix;
struct vidmix_source;

/**
 * Video mixer frame handler
 *
 * @param ts    Timestamp
 * @param frame Video frame
 * @param arg   Handler argument
 */
typedef void (vidmix_frame_h)(uint32_t ts, const struct vidframe *frame,
			      void *arg);

int  vidmix_alloc(struct vidmix **mixp);
int  vidmix_source_alloc(struct vidmix_source **srcp, struct vidmix *mix,
			 const struct vidsz *sz, unsigned fps, bool content,
			 vidmix_frame_h *fh, void *arg);
bool vidmix_source_isenabled(const struct vidmix_source *src);
bool vidmix_source_isrunning(const struct vidmix_source *src);
void *vidmix_source_get_focus(const struct vidmix_source *src);
void vidmix_source_enable(struct vidmix_source *src, bool enable);
int  vidmix_source_start(struct vidmix_source *src);
void vidmix_source_stop(struct vidmix_source *src);
int  vidmix_source_set_size(struct vidmix_source *src, const struct vidsz *sz);
void vidmix_source_set_rate(struct vidmix_source *src, unsigned fps);
void vidmix_source_set_content_hide(struct vidmix_source *src, bool hide);
void vidmix_source_toggle_selfview(struct vidmix_source *src);
void vidmix_source_set_focus(struct vidmix_source *src,
			     const struct vidmix_source *focus_src,
			     bool focus_full);
void vidmix_source_set_focus_idx(struct vidmix_source *src, unsigned pidx);
void vidmix_source_put(struct vidmix_source *src,
		       const struct vidframe *frame);
