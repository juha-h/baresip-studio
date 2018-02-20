/**
 * @file rem_dtmf.h  DTMF Decoder
 *
 * Copyright (C) 2010 Creytiv.com
 */

struct dtmf_dec;

/**
 * Defines the DTMF decode handler
 *
 * @param digit Decoded DTMF digit
 * @param arg   Handler argument
 */
typedef void (dtmf_dec_h)(char digit, void *arg);


int  dtmf_dec_alloc(struct dtmf_dec **decp, unsigned srate, unsigned ch,
		    dtmf_dec_h *dech, void *arg);
void dtmf_dec_reset(struct dtmf_dec *dec, unsigned srate, unsigned ch);
void dtmf_dec_probe(struct dtmf_dec *dec, const int16_t *sampv, size_t sampc);
