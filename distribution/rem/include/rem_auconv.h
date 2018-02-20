/**
 * @file rem_auconv.h Audio sample format conversion
 *
 * Copyright (C) 2010 Creytiv.com
 */


void auconv_from_s16(enum aufmt dst_fmt, void *dst_sampv,
		     const int16_t *src_sampv, size_t sampc);
void auconv_to_s16(int16_t *dst_sampv, enum aufmt src_fmt,
		   void *src_sampv, size_t sampc);
