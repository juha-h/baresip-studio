/**
 * @file rem_auresamp.h Audio Resampling
 *
 * Copyright (C) 2010 Creytiv.com
 */

/**
 * Defines the audio resampler handler
 *
 * @param outv  Output samples
 * @param inv   Input samples
 * @param inc   Number of input samples
 * @param ratio Resample ratio
 */
typedef void (auresamp_h)(int16_t *outv, const int16_t *inv,
			  size_t inc, unsigned ratio);

/** Defines the resampler state */
struct auresamp {
	struct fir fir;        /**< FIR filter state */
	auresamp_h *resample;  /**< Resample handler */
	const int16_t *tapv;   /**< FIR filter taps */
	size_t tapc;           /**< FIR filter tap count */
	uint32_t orate, irate; /**< Input/output sample rate */
	unsigned och, ich;     /**< Input/output channel count */
	unsigned ratio;        /**< Resample ratio */
	bool up;               /**< Up/down sample flag */
};

void auresamp_init(struct auresamp *rs);
int  auresamp_setup(struct auresamp *rs, uint32_t irate, unsigned ich,
		    uint32_t orate, unsigned och);
int  auresamp(struct auresamp *rs, int16_t *outv, size_t *outc,
	      const int16_t *inv, size_t inc);
