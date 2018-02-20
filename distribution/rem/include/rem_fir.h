/**
 * @file rem_fir.h  Finite Impulse Response (FIR) functions
 *
 * Copyright (C) 2010 Creytiv.com
 */

/** Defines the fir filter state */
struct fir {
	int16_t history[256];  /**< Previous samples */
	unsigned index;        /**< Sample index */
};

void fir_reset(struct fir *fir);
void fir_filter(struct fir *fir, int16_t *outv, const int16_t *inv, size_t inc,
		unsigned ch, const int16_t *tapv, size_t tapc);
