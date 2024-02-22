/**
 * @file rem_vidconv.h  Video colorspace conversion
 *
 * Copyright (C) 2010 Creytiv.com
 */


void vidconv(struct vidframe *dst, const struct vidframe *src,
	     struct vidrect *r);
void vidconv_aspect(struct vidframe *dst, const struct vidframe *src,
		    struct vidrect *r);
