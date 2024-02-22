/**
 * @file rem_aac.h Advanced Audio Coding
 *
 * Copyright (C) 2010 Creytiv.com
 */


/** Defines the AAC header */
struct aac_header {
	unsigned sample_rate;  /**< Audio sample rate in [Hz]    */
	unsigned channels;     /**< Number of audio channels     */
	unsigned frame_size;   /**< Frame size, 960 or 1024 bits */
};

int aac_header_decode(struct aac_header *hdr, const uint8_t *p, size_t len);
