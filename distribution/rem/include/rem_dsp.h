/**
 * @file rem_dsp.h DSP routines
 *
 * Copyright (C) 2010 Creytiv.com
 */


#ifndef UINT8_MAX
#define UINT8_MAX (255U)
#endif

#define INT15_MAX 0x3fff
#define INT15_MIN (-INT15_MAX - 1)

#ifndef INT16_MAX
#define INT16_MAX 0x7fff
#endif

#ifndef INT16_MIN
#define INT16_MIN (-INT16_MAX - 1)
#endif


/* todo: check which preprocessor macros to use */


#if defined (HAVE_ARMV6) || defined (HAVE_NEON)

static inline uint8_t saturate_u8(int32_t a)
{
	uint8_t r;
	__asm__ __volatile__ ("usat %0, #8, %1" : "=r"(r) : "r"(a));
	return r;
}

static inline int16_t saturate_s15(int32_t a)
{
	__asm__ __volatile__ ("ssat %0, #15, %1  \n\t"
			      : "+r"(a)
			      : "r"(a)
			      );

	return a;
}

static inline int16_t saturate_s16(int32_t a)
{
	__asm__ __volatile__ ("ssat %0, #16, %1  \n\t"
			      : "+r"(a)
			      : "r"(a)
			      );

	return a;
}

static inline int16_t saturate_add16(int32_t a, int32_t b)
{
	__asm__ __volatile__ ("add %0, %1, %2    \n\t"
			      "ssat %0, #16, %0  \n\t"
			      :"+r"(a)
			      :"r"(a), "r"(b)
			      );
	return a;
}

static inline int16_t saturate_sub16(int32_t a, int32_t b)
{
	__asm__ __volatile__ ("sub %0, %1, %2     \n\t"
			      "ssat %0, #16, %0   \n\t"
			      :"+r"(a)
			      :"r"(a), "r"(b)
			      );
	return a;
}


#else


static inline uint8_t saturate_u8(int32_t a)
{
	return (a > (int32_t)UINT8_MAX) ? UINT8_MAX : ((a < 0) ? 0 : a);
}

static inline int16_t saturate_s15(int32_t a)
{
	if (a > INT15_MAX)
		return INT15_MAX;
	else if (a < INT15_MIN)
		return INT15_MIN;
	else
		return a;
}

static inline int16_t saturate_s16(int32_t a)
{
	if (a > INT16_MAX)
		return INT16_MAX;
	else if (a < INT16_MIN)
		return INT16_MIN;
	else
		return a;
}

static inline int16_t saturate_add16(int32_t a, int32_t b)
{
	return saturate_s16(a + b);
}


static inline int16_t saturate_sub16(int32_t a, int32_t b)
{
	return saturate_s16(a - b);
}


#endif


#ifdef HAVE_NEON
static inline int ABS32(int a)
{
	int r;
	__asm__ __volatile__ ("vmov.s32 d0[0], %1 \t\n"
			      "vabs.s32 d0, d0    \t\n"
			      "vmov.s32 %0, d0[0] \t\n"
			      : "=r"(r)
			      : "r"(a)
			      );
	return a;
}
#else
static inline int ABS32(int a)
{
	return a > 0 ? a : -a;
}
#endif
