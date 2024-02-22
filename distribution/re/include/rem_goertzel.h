/**
 * @file rem_goertzel.h  Goertzel algorithm
 *
 * Copyright (C) 2010 Creytiv.com
 */

/** Defines the goertzel algorithm state */
struct goertzel {
	double q1;   /**< current state */
	double q2;   /**< previous state */
	double coef; /**< coefficient */
};


void  goertzel_init(struct goertzel *g, double freq, unsigned srate);
void  goertzel_reset(struct goertzel *g);
double goertzel_result(struct goertzel *g);


/**
 * Process sample
 *
 * @param g    Goertzel state
 * @param samp Sample value
 */
static inline void goertzel_update(struct goertzel *g, int16_t samp)
{
	double q0 = g->coef*g->q1 - g->q2 + (double)samp;

	g->q2 = g->q1;
	g->q1 = q0;
}
