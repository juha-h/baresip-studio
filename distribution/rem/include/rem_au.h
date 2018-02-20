/**
 * @file rem_au.h Basic audio types
 *
 * Copyright (C) 2010 Creytiv.com
 */


/** Audio formats */
enum aufmt {
	AUFMT_S16LE,  /**< Signed 16-bit PCM */
	AUFMT_PCMA,   /**< G.711 A-law       */
	AUFMT_PCMU,   /**< G.711 U-law       */
	AUFMT_FLOAT,  /**< Float 32 bit (CPU endian)                   */
	AUFMT_S24_3LE,/**< Signed 24bit Little Endian in 3bytes format */
};

size_t      aufmt_sample_size(enum aufmt fmt);
const char *aufmt_name(enum aufmt fmt);
