/**
 * @file re_convert.h  Conversion helpers
 *
 * Copyright (C) 2022 Sebastian Reimers
 */
#include <limits.h>

static inline int try_into_u16_from_size(uint16_t *dest, const size_t src)
{
	*dest = 0;

	if (src > UINT16_MAX)
		return ERANGE;

	*dest = (uint16_t)src;

	return 0;
}


static inline int try_into_u16_from_int(uint16_t *dest, const int src)
{
	*dest = 0;

	if (src > UINT16_MAX)
		return ERANGE;

	if (src < 0)
		return ERANGE;

	*dest = (uint16_t)src;

	return 0;
}


static inline int try_into_int_from_size(int *dest, const size_t src)
{
	*dest = 0;

	if (src > INT_MAX)
		return ERANGE;

	*dest = (int)src;

	return 0;
}


static inline int try_into_err(void *dest, ...)
{
	(void)dest;

	return ENOTSUP;
}


#if __STDC_VERSION__ >= 201112L /* Needs C11 support */
/**
 * Try to convert safely from one type (src) into another (dest).
 * Types are auto detected.
 *
 * @param dest Destination
 * @param src  Source value
 *
 * @return 0 if success, ERANGE if value overflow and ENOTSUP if not supported
 */
#define try_into(dest, src)						      \
	_Generic((dest), 						      \
		uint16_t: _Generic((src),				      \
				size_t: try_into_u16_from_size, 	      \
				int: try_into_u16_from_int,		      \
				default: try_into_err			      \
			),						      \
		int: _Generic((src),					      \
				size_t: try_into_int_from_size,		      \
				default: try_into_err			      \
			)						      \
		)							      \
	(&(dest), (src))
#endif
