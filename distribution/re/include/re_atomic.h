/**
 * @file re_atomic.h  Atomic support
 *
 * Copyright (C) 2022 Sebastian Reimers
 */

#ifndef RE_H_ATOMIC__
#define RE_H_ATOMIC__

/* C11 */
#if defined(HAVE_ATOMIC) && __STDC_VERSION__ >= 201112L &&                    \
	!defined(__STDC_NO_ATOMICS__)

#include <stdatomic.h>

#define RE_ATOMIC _Atomic

#define RE_ATOMIC_BOOL_LOCK_FREE ATOMIC_BOOL_LOCK_FREE
#define RE_ATOMIC_CHAR_LOCK_FREE ATOMIC_CHAR_LOCK_FREE
#define RE_ATOMIC_WCHAR_T_LOCK_FREE ATOMIC_WCHAR_T_LOCK_FREE
#define RE_ATOMIC_SHORT_LOCK_FREE ATOMIC_SHORT_LOCK_FREE
#define RE_ATOMIC_INT_LOCK_FREE ATOMIC_INT_LOCK_FREE
#define RE_ATOMIC_LONG_LOCK_FREE ATOMIC_LONG_LOCK_FREE
#define RE_ATOMIC_LLONG_LOCK_FREE ATOMIC_LLONG_LOCK_FREE
#define RE_ATOMIC_POINTER_LOCK_FREE ATOMIC_POINTER_LOCK_FREE

#define re_memory_order_relaxed memory_order_relaxed
#define re_memory_order_acquire memory_order_acquire
#define re_memory_order_release memory_order_release
#define re_memory_order_acq_rel memory_order_acq_rel
#define re_memory_order_seq_cst memory_order_seq_cst

#define re_atomic_store(_a, _v, _mo) \
	atomic_store_explicit(_a, _v, _mo)

#define re_atomic_load(_a, _mo) \
	atomic_load_explicit(_a, _mo)

#define re_atomic_exchange(_a, _v, _mo) \
	atomic_exchange_explicit(_a, _v, _mo)

#define re_atomic_compare_exchange_strong(\
	_a, _expected, _desired, _success_mo, _fail_mo) \
	atomic_compare_exchange_strong_explicit(\
		_a, _expected, _desired, _success_mo, _fail_mo)

#define re_atomic_compare_exchange_weak(\
	_a, _expected, _desired, _success_mo, _fail_mo) \
	atomic_compare_exchange_weak_explicit(\
		_a, _expected, _desired, _success_mo, _fail_mo)

#define re_atomic_fetch_add(_a, _v, _mo) \
	atomic_fetch_add_explicit(_a, _v, _mo)

#define re_atomic_fetch_sub(_a, _v, _mo) \
	atomic_fetch_sub_explicit(_a, _v, _mo)

#define re_atomic_fetch_or(_a, _v, _mo) \
	atomic_fetch_or_explicit(_a, _v, _mo)

#define re_atomic_fetch_xor(_a, _v, _mo) \
	atomic_fetch_xor_explicit(_a, _v, _mo)

#define re_atomic_fetch_and(_a, _v, _mo) \
	atomic_fetch_and_explicit(_a, _v, _mo)

/* gcc-style __atomic* intrinsics.
 * Note: clang-cl also supports these, even though it impersonates MSVC. */
#elif (defined(__GNUC__) || defined(__clang__)) && \
	defined(__GCC_ATOMIC_BOOL_LOCK_FREE) && \
	defined(__GCC_ATOMIC_CHAR_LOCK_FREE) && \
	defined(__GCC_ATOMIC_WCHAR_T_LOCK_FREE) && \
	defined(__GCC_ATOMIC_SHORT_LOCK_FREE) && \
	defined(__GCC_ATOMIC_INT_LOCK_FREE) && \
	defined(__GCC_ATOMIC_LONG_LOCK_FREE) && \
	defined(__GCC_ATOMIC_LLONG_LOCK_FREE) && \
	defined(__GCC_ATOMIC_POINTER_LOCK_FREE) && \
	defined(__ATOMIC_RELAXED) && defined(__ATOMIC_ACQUIRE) && \
	defined(__ATOMIC_RELEASE) && defined(__ATOMIC_ACQ_REL) && \
	defined(__ATOMIC_SEQ_CST)

#define RE_ATOMIC_BOOL_LOCK_FREE __GCC_ATOMIC_BOOL_LOCK_FREE
#define RE_ATOMIC_CHAR_LOCK_FREE __GCC_ATOMIC_CHAR_LOCK_FREE
#define RE_ATOMIC_WCHAR_T_LOCK_FREE __GCC_ATOMIC_WCHAR_T_LOCK_FREE
#define RE_ATOMIC_SHORT_LOCK_FREE __GCC_ATOMIC_SHORT_LOCK_FREE
#define RE_ATOMIC_INT_LOCK_FREE __GCC_ATOMIC_INT_LOCK_FREE
#define RE_ATOMIC_LONG_LOCK_FREE __GCC_ATOMIC_LONG_LOCK_FREE
#define RE_ATOMIC_LLONG_LOCK_FREE __GCC_ATOMIC_LLONG_LOCK_FREE
#define RE_ATOMIC_POINTER_LOCK_FREE __GCC_ATOMIC_POINTER_LOCK_FREE

#define re_memory_order_relaxed __ATOMIC_RELAXED
#define re_memory_order_acquire __ATOMIC_ACQUIRE
#define re_memory_order_release __ATOMIC_RELEASE
#define re_memory_order_acq_rel __ATOMIC_ACQ_REL
#define re_memory_order_seq_cst __ATOMIC_SEQ_CST

#define re_atomic_store(_a, _v, _mo) \
	__atomic_store_n(_a, _v, _mo)

#define re_atomic_load(_a, _mo) \
	__atomic_load_n(_a, _mo)

#define re_atomic_exchange(_a, _v, _mo) \
	__atomic_exchange_n(_a, _v, _mo)

#define re_atomic_compare_exchange_strong(\
	_a, _expected, _desired, _success_mo, _fail_mo) \
	__atomic_compare_exchange_n(\
		_a, _expected, _desired, 0, _success_mo, _fail_mo)

#define re_atomic_compare_exchange_weak(\
	_a, _expected, _desired, _success_mo, _fail_mo) \
	__atomic_compare_exchange_n(\
		_a, _expected, _desired, 1, _success_mo, _fail_mo)

#define re_atomic_fetch_add(_a, _v, _mo) \
	__atomic_fetch_add(_a, _v, _mo)

#define re_atomic_fetch_sub(_a, _v, _mo) \
	__atomic_fetch_sub(_a, _v, _mo)

#define re_atomic_fetch_or(_a, _v, _mo) \
	__atomic_fetch_or(_a, _v, _mo)

#define re_atomic_fetch_xor(_a, _v, _mo) \
	__atomic_fetch_xor(_a, _v, _mo)

#define re_atomic_fetch_and(_a, _v, _mo) \
	__atomic_fetch_and(_a, _v, _mo)

/* gcc-style __sync* intrinsics. */
#elif defined(__GNUC__) && \
	(defined(__GCC_HAVE_SYNC_COMPARE_AND_SWAP_1) || \
	defined(__GCC_HAVE_SYNC_COMPARE_AND_SWAP_2) || \
	defined(__GCC_HAVE_SYNC_COMPARE_AND_SWAP_4) || \
	defined(__GCC_HAVE_SYNC_COMPARE_AND_SWAP_8))

#if !defined(__SIZEOF_SHORT__) || !defined(__SIZEOF_INT__) || \
	!defined(__SIZEOF_LONG__) || !defined(__SIZEOF_LONG_LONG__)
#include <limits.h>
#endif
#if !defined(__SIZEOF_POINTER__)
#include <stdint.h>
#endif
#if !defined(__SIZEOF_WCHAR_T__)
#include <wchar.h>
#endif

#if defined(__GCC_HAVE_SYNC_COMPARE_AND_SWAP_1)
#define RE_ATOMIC_CHAR_LOCK_FREE 2
#endif

#if defined(__GCC_HAVE_SYNC_COMPARE_AND_SWAP_2)
#if (defined(__SIZEOF_SHORT__) && __SIZEOF_SHORT__ == 2) || \
	(defined(USHRT_MAX) && USHRT_MAX == 0xffffu)
#define RE_ATOMIC_SHORT_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_INT__) && __SIZEOF_INT__ == 2) || \
	(defined(UINT_MAX) && UINT_MAX == 0xffffu)
#define RE_ATOMIC_INT_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_LONG__) && __SIZEOF_LONG__ == 2) || \
	(defined(ULONG_MAX) && ULONG_MAX == 0xffffu)
#define RE_ATOMIC_LONG_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_LONG_LONG__) && __SIZEOF_LONG_LONG__ == 2) || \
	(defined(ULLONG_MAX) && ULLONG_MAX == 0xffffu)
#define RE_ATOMIC_LLONG_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_POINTER__) && __SIZEOF_POINTER__ == 2) || \
	(defined(UINTPTR_MAX) && UINTPTR_MAX == 0xffffu)
#define RE_ATOMIC_POINTER_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_WCHAR_T__) && __SIZEOF_WCHAR_T__ == 2) || \
	(defined(WCHAR_MAX) && (WCHAR_MAX == 0xffff || WCHAR_MAX == 0x7fff))
#define RE_ATOMIC_WCHAR_T_LOCK_FREE 2
#endif
#endif

#if defined(__GCC_HAVE_SYNC_COMPARE_AND_SWAP_4)
#if (defined(__SIZEOF_SHORT__) && __SIZEOF_SHORT__ == 4) || \
	(defined(USHRT_MAX) && USHRT_MAX == 0xffffffffu)
#define RE_ATOMIC_SHORT_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_INT__) && __SIZEOF_INT__ == 4) || \
	(defined(UINT_MAX) && UINT_MAX == 0xffffffffu)
#define RE_ATOMIC_INT_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_LONG__) && __SIZEOF_LONG__ == 4) || \
	(defined(ULONG_MAX) && ULONG_MAX == 0xffffffffu)
#define RE_ATOMIC_LONG_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_LONG_LONG__) && __SIZEOF_LONG_LONG__ == 4) || \
	(defined(ULLONG_MAX) && ULLONG_MAX == 0xffffffffu)
#define RE_ATOMIC_LLONG_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_POINTER__) && __SIZEOF_POINTER__ == 4) || \
	(defined(UINTPTR_MAX) && UINTPTR_MAX == 0xffffffffu)
#define RE_ATOMIC_POINTER_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_WCHAR_T__) && __SIZEOF_WCHAR_T__ == 4) || \
	(defined(WCHAR_MAX) && (WCHAR_MAX == 0xffffffff || \
		WCHAR_MAX == 0x7fffffff))
#define RE_ATOMIC_WCHAR_T_LOCK_FREE 2
#endif
#endif

#if defined(__GCC_HAVE_SYNC_COMPARE_AND_SWAP_8)
#if (defined(__SIZEOF_SHORT__) && __SIZEOF_SHORT__ == 8) || \
	(defined(USHRT_MAX) && USHRT_MAX == 0xffffffffffffffffu)
#define RE_ATOMIC_SHORT_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_INT__) && __SIZEOF_INT__ == 8) || \
	(defined(UINT_MAX) && UINT_MAX == 0xffffffffffffffffu)
#define RE_ATOMIC_INT_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_LONG__) && __SIZEOF_LONG__ == 8) || \
	(defined(ULONG_MAX) && ULONG_MAX == 0xffffffffffffffffu)
#define RE_ATOMIC_LONG_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_LONG_LONG__) && __SIZEOF_LONG_LONG__ == 8) || \
	(defined(ULLONG_MAX) && ULLONG_MAX == 0xffffffffffffffffu)
#define RE_ATOMIC_LLONG_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_POINTER__) && __SIZEOF_POINTER__ == 8) || \
	(defined(UINTPTR_MAX) && UINTPTR_MAX == 0xffffffffffffffffu)
#define RE_ATOMIC_POINTER_LOCK_FREE 2
#endif
#if (defined(__SIZEOF_WCHAR_T__) && __SIZEOF_WCHAR_T__ == 8) || \
	(defined(WCHAR_MAX) && (WCHAR_MAX == 0xffffffffffffffff || \
		WCHAR_MAX == 0x7fffffffffffffff))
#define RE_ATOMIC_WCHAR_T_LOCK_FREE 2
#endif
#endif

#if !defined(RE_ATOMIC_CHAR_LOCK_FREE)
#define RE_ATOMIC_CHAR_LOCK_FREE 0
#endif
#if !defined(RE_ATOMIC_SHORT_LOCK_FREE)
#define RE_ATOMIC_SHORT_LOCK_FREE 0
#endif
#if !defined(RE_ATOMIC_INT_LOCK_FREE)
#define RE_ATOMIC_INT_LOCK_FREE 0
#endif
#if !defined(RE_ATOMIC_LONG_LOCK_FREE)
#define RE_ATOMIC_LONG_LOCK_FREE 0
#endif
#if !defined(RE_ATOMIC_LLONG_LOCK_FREE)
#define RE_ATOMIC_LLONG_LOCK_FREE 0
#endif
#if !defined(RE_ATOMIC_POINTER_LOCK_FREE)
#define RE_ATOMIC_POINTER_LOCK_FREE 0
#endif
#if !defined(RE_ATOMIC_WCHAR_T_LOCK_FREE)
#define RE_ATOMIC_WCHAR_T_LOCK_FREE 0
#endif

/* Assume bool is always 1 byte. Add platform-specific exceptions,
 * if needed. */
#define RE_ATOMIC_BOOL_LOCK_FREE RE_ATOMIC_CHAR_LOCK_FREE

/* These constants match __ATOMIC_* predefined macros on
 * gcc versions that support __atomic intrinsics. */
#define re_memory_order_relaxed 0
#define re_memory_order_acquire 2
#define re_memory_order_release 3
#define re_memory_order_acq_rel 4
#define re_memory_order_seq_cst 5

#if defined(__x86_64__)

#define re_atomic_store(_a, _v, _mo) \
	__extension__\
	({\
		__typeof__(*(_a)) _val = (_v);\
		if ((_mo) != re_memory_order_seq_cst) {\
			__asm__ __volatile__ ("mov %1, %0"\
				: "=m" (*(_a))\
				: "q" (_val)\
				: "memory");\
		}\
		else {\
			__asm__ __volatile__ ("xchg %1, %0"\
				: "=m" (*(_a)), "+q" (_val)\
				: \
				: "memory");\
		}\
	})

#define re_atomic_load(_a, _mo) \
	__extension__\
	({\
		__typeof__(*(_a)) _val;\
		__asm__ __volatile__ ("mov %1, %0"\
			: "=q" (_val)\
			: "m" (*(_a))\
			: "memory");\
		_val;\
	})

#define re_atomic_exchange(_a, _v, _mo) \
	__extension__\
	({\
		__typeof__(*(_a)) _val = (_v);\
		__asm__ __volatile__ ("xchg %1, %0"\
			: "+m" (*(_a)), "+q" (_val)\
			: \
			: "memory");\
		_val;\
	})

#elif defined(__i386__)

#define re_atomic_store(_a, _v, _mo) \
	__extension__\
	({\
		__typeof__(*(_a)) _val = (_v);\
		if (sizeof(_val) < 8) {\
			if ((_mo) != re_memory_order_seq_cst) {\
				__asm__ __volatile__ ("mov %1, %0"\
					: "=m" (*(_a))\
					: "q" (_val)\
					: "memory");\
			}\
			else {\
				__asm__ __volatile__ ("xchg %1, %0"\
					: "=m" (*(_a)), "+q" (_val)\
					: \
					: "memory");\
			}\
		}\
		else {\
			__typeof__(*(_a)) _expected = *(_a);\
			while (1) {\
				__typeof__(*(_a)) _prev_val =\
					__sync_val_compare_and_swap(\
						_a, _expected, _val);\
				if (_prev_val == _expected)\
					break;\
				_expected = _prev_val;\
			}\
		}\
	})

#define re_atomic_load(_a, _mo) \
	__extension__\
	({\
		__typeof__(*(_a)) _val;\
		if (sizeof(_val) < 8) {\
			__asm__ __volatile__ ("mov %1, %0"\
				: "=q" (_val)\
				: "m" (*(_a))\
				: "memory");\
		}\
		else {\
			_val = __sync_val_compare_and_swap(\
				_a,\
				(__typeof__(*(_a)))0,\
				(__typeof__(*(_a)))0);\
		}\
		_val;\
	})

#define re_atomic_exchange(_a, _v, _mo) \
	__extension__\
	({\
		__typeof__(*(_a)) _val = (_v);\
		if (sizeof(_val) < 8) {\
			__asm__ __volatile__ ("xchg %1, %0"\
				: "+m" (*(_a)), "+q" (_val)\
				: \
				: "memory");\
		}\
		else {\
			__typeof__(*(_a)) _expected = *(_a);\
			while (1) {\
				__typeof__(*(_a)) _prev_val =\
					__sync_val_compare_and_swap(\
						_a, _expected, _val);\
				if (_prev_val == _expected)\
					break;\
				_expected = _prev_val;\
			}\
			_val = _expected;\
		}\
		_val;\
	})

#else

#define re_atomic_store(_a, _v, _mo) \
	(void)re_atomic_exchange(_a, _v, _mo)

#define re_atomic_load(_a, _mo) \
	__sync_val_compare_and_swap(\
		_a, (__typeof__(*(_a)))0, (__typeof__(*(_a)))0)

#define re_atomic_exchange(_a, _v, _mo) \
	__extension__\
	({\
		__typeof__(*(_a)) _val = (_v);\
		__typeof__(*(_a)) _expected = *(_a);\
		while (1) {\
			__typeof__(*(_a)) _prev_val =\
				__sync_val_compare_and_swap(\
					_a, _expected, _val);\
			if (_prev_val == _expected)\
				break;\
			_expected = _prev_val;\
		}\
		_expected;\
	})

#endif

#define re_atomic_compare_exchange_strong(\
	_a, _expected, _desired, _success_mo, _fail_mo) \
	__extension__\
	({\
		__typeof__(*(_a)) _exp_val = *(_expected);\
		__typeof__(*(_a)) _prev_val =\
			__sync_val_compare_and_swap(_a, _exp_val,\
				(__typeof__(*(_a)))(_desired));\
		*(_expected) = _prev_val;\
		_prev_val == _exp_val;\
	})

#define re_atomic_compare_exchange_weak(\
	_a, _expected, _desired, _success_mo, _fail_mo) \
	re_atomic_compare_exchange_strong(\
		_a, _expected, _desired, _success_mo, _fail_mo)

#define re_atomic_fetch_add(_a, _v, _mo) \
	__sync_fetch_and_add(_a, (__typeof__(*(_a)))(_v))

#define re_atomic_fetch_sub(_a, _v, _mo) \
	__sync_fetch_and_sub(_a, (__typeof__(*(_a)))(_v))

#define re_atomic_fetch_or(_a, _v, _mo) \
	__sync_fetch_and_or(_a, (__typeof__(*(_a)))(_v))

#define re_atomic_fetch_xor(_a, _v, _mo) \
	__sync_fetch_and_xor(_a, (__typeof__(*(_a)))(_v))

#define re_atomic_fetch_and(_a, _v, _mo) \
	__sync_fetch_and_and(_a, (__typeof__(*(_a)))(_v))

/* MSVC Interlocked* intrinsics. This needs to go after clang to let clang-cl
 * get handled above. */
#elif defined(_MSC_VER)

#include <assert.h>
#include <intrin.h>
#include "re_types.h"

#ifdef __cplusplus
extern "C" {
#endif

#define RE_ATOMIC_BOOL_LOCK_FREE 2
#define RE_ATOMIC_CHAR_LOCK_FREE 2
#define RE_ATOMIC_WCHAR_T_LOCK_FREE 2
#define RE_ATOMIC_SHORT_LOCK_FREE 2
#define RE_ATOMIC_INT_LOCK_FREE 2
#define RE_ATOMIC_LONG_LOCK_FREE 2
#define RE_ATOMIC_LLONG_LOCK_FREE 2
#define RE_ATOMIC_POINTER_LOCK_FREE 2

/* These constants don't matter but for consistency they match
 * values in std::memory_order from <atomic> in C++.
 * There are specialized intrinsics for ARM and ARM64
 * for different memory ordering types, but they are not used (yet) below. */
#define re_memory_order_relaxed 0
#define re_memory_order_acquire 2
#define re_memory_order_release 3
#define re_memory_order_acq_rel 4
#define re_memory_order_seq_cst 5

static unsigned __int64 _re_atomic_exchange(
	size_t size, void *a, unsigned __int64 v);

#if defined(_M_IX86) || defined(_M_AMD64)

static __forceinline void _re_atomic_store(
	size_t size, void *a, unsigned __int64 v, unsigned int mo)
{
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	if (mo != re_memory_order_seq_cst) {
		_ReadWriteBarrier();
		switch (size) {
		case 1u:
			*(volatile unsigned __int8*)a = (unsigned __int8)v;
			break;
		case 2u:
			*(volatile unsigned __int16*)a = (unsigned __int16)v;
			break;
		case 4u:
			*(volatile unsigned __int32*)a = (unsigned __int32)v;
			break;
		default:
#if defined(_M_IX86)
			{
				__int64 prev_val =
					*(const volatile __int64*)(a);
				while (1) {
					__int64 prev_val2 =
						_InterlockedCompareExchange64(
							(__int64*)a,
							(__int64)v,
							prev_val);
					if (prev_val2 == prev_val)
						break;
					prev_val = prev_val2;
				}
			}
#else
			*(volatile unsigned __int64*)a = v;
#endif
			break;
		}
		_ReadWriteBarrier();
	}
	else {
		_re_atomic_exchange(size, a, v);
	}
}

static __forceinline unsigned __int64 _re_atomic_load(
	size_t size, const void *a, unsigned int mo)
{
	unsigned __int64 v;
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	_ReadWriteBarrier();
	switch (size) {
	case 1u:
		v = *(const volatile unsigned __int8*)a;
		break;
	case 2u:
		v = *(const volatile unsigned __int16*)a;
		break;
	case 4u:
		v = *(const volatile unsigned __int32*)a;
		break;
	default:
#if defined(_M_IX86)
		v = _InterlockedCompareExchange64((__int64*)a, 0, 0);
#else
		v = *(const volatile unsigned __int64*)a;
#endif
		break;
	}
	_ReadWriteBarrier();

	return v;
}

#elif defined(_M_ARM) || defined(_M_ARM64)

static __forceinline void _re_atomic_store(
	size_t size, void *a, unsigned __int64 v, unsigned int mo)
{
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	_ReadWriteBarrier();

	if (mo >= re_memory_order_release)
		__dmb(0x0b); /* dmb ish */

	_ReadWriteBarrier();

	switch (size) {
	case 1u:
		__iso_volatile_store8((__int8*)a, (__int8)v);
		break;
	case 2u:
		__iso_volatile_store16((__int16*)a, (__int16)v);
		break;
	case 4u:
		__iso_volatile_store32((__int32*)a, (__int32)v);
		break;
	default:
		__iso_volatile_store64((__int64*)a, (__int64)v);
		break;
	}

	_ReadWriteBarrier();

	if (mo == re_memory_order_seq_cst)
		__dmb(0x0b); /* dmb ish */

	_ReadWriteBarrier();
}

static __forceinline unsigned __int64 _re_atomic_load(
	size_t size, const void *a, unsigned int mo)
{
	unsigned __int64 v;
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	_ReadWriteBarrier();

	switch (size) {
	case 1u:
		v = __iso_volatile_load8((const unsigned __int8*)a);
		break;
	case 2u:
		v = __iso_volatile_load16((const unsigned __int16*)a);
		break;
	case 4u:
		v = __iso_volatile_load32((const unsigned __int32*)a);
		break;
	default:
		v = __iso_volatile_load64((const unsigned __int64*)a);
		break;
	}

	_ReadWriteBarrier();

	if (mo != re_memory_order_relaxed && mo <= re_memory_order_acquire)
		__dmb(0x0b); /* dmb ish */

	_ReadWriteBarrier();

	return v;
}

#else

static __forceinline void _re_atomic_store(
	size_t size, void *a, unsigned __int64 v, unsigned int mo)
{
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	_ReadWriteBarrier();
	switch (size) {
	case 1u:
		{
			char prev_val = *(const volatile char*)(a);
			while (1) {
				char prev_val2 =
					_InterlockedCompareExchange8(
						(char*)a,
						(char)v,
						prev_val);
				if (prev_val2 == prev_val)
					break;
				prev_val = prev_val2;
			}
		}
		break;
	case 2u:
		{
			short prev_val = *(const volatile short*)(a);
			while (1) {
				short prev_val2 =
					_InterlockedCompareExchange16(
						(short*)a,
						(short)v,
						prev_val);
				if (prev_val2 == prev_val)
					break;
				prev_val = prev_val2;
			}
		}
		break;
	case 4u:
		{
			long prev_val = *(const volatile long*)(a);
			while (1) {
				long prev_val2 =
					_InterlockedCompareExchange(
						(long*)a,
						(long)v,
						prev_val);
				if (prev_val2 == prev_val)
					break;
				prev_val = prev_val2;
			}
		}
		break;
	default:
		{
			__int64 prev_val = *(const volatile __int64*)(a);
			while (1) {
				__int64 prev_val2 =
					_InterlockedCompareExchange64(
						(__int64*)a,
						(__int64)v,
						prev_val);
				if (prev_val2 == prev_val)
					break;
				prev_val = prev_val2;
			}
		}
		break;
	}
	_ReadWriteBarrier();
}

static __forceinline unsigned __int64 _re_atomic_load(
	size_t size, const void *a, unsigned int mo)
{
	unsigned __int64 v;
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	switch (size) {
	case 1u:
		v = _InterlockedCompareExchange8((char*)a, 0, 0);
		break;
	case 2u:
		v = _InterlockedCompareExchange16((short*)a, 0, 0);
		break;
	case 4u:
		v = _InterlockedCompareExchange((long*)a, 0, 0);
		break;
	default:
		v = _InterlockedCompareExchange64((__int64*)a, 0, 0);
		break;
	}

	return v;
}

#endif

#define re_atomic_store(_a, _v, _mo) \
	_re_atomic_store(sizeof(*(_a)), _a, _v, _mo);

#define re_atomic_load(_a, _mo) \
	_re_atomic_load(sizeof(*(_a)), _a, _mo)

static __forceinline unsigned __int64 _re_atomic_exchange(
	size_t size, void *a, unsigned __int64 v)
{
	unsigned __int64 prev_val;
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	switch (size) {
	case 1u:
		prev_val = _InterlockedExchange8((char*)a, (char)v);
		break;
	case 2u:
		prev_val = _InterlockedExchange16((short*)a, (short)v);
		break;
	case 4u:
		prev_val = _InterlockedExchange((long*)a, (long)v);
		break;
	default:
#if defined(_M_IX86)
		{
			_ReadWriteBarrier();
			prev_val = *(const volatile __int64*)(a);
			while (1) {
				__int64 prev_val2 =
					_InterlockedCompareExchange64(
						(__int64*)a,
						(__int64)v,
						(__int64)prev_val);
				if (prev_val2 == prev_val)
					break;
				prev_val = prev_val2;
			}
			_ReadWriteBarrier();
		}
#else
		prev_val = _InterlockedExchange64((__int64*)a, (__int64)v);
#endif
		break;
	}

	return prev_val;
}

#define re_atomic_exchange(_a, _v, _mo) \
	_re_atomic_exchange(sizeof(*(_a)), _a, _v)

static __forceinline bool _re_atomic_compare_exchange_strong(
	size_t size, void *a, void *expected, unsigned __int64 desired)
{
	bool res;
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	switch (size) {
	case 1u:
		{
			char expected_val = *(char*)expected;
			char prev_val =
				 _InterlockedCompareExchange8(
					(char*)a,
					(char)desired,
					expected_val);
			*(char*)expected = prev_val;
			res = prev_val == expected_val;
		}
		break;
	case 2u:
		{
			short expected_val = *(short*)expected;
			short prev_val =
				_InterlockedCompareExchange16(
					(short*)a,
					(short)desired,
					expected_val);
			*(short*)expected = prev_val;
			res = prev_val == expected_val;
		}
		break;
	case 4u:
		{
			long expected_val = *(long*)expected;
			long prev_val =
				 _InterlockedCompareExchange(
					(long*)a,
					(long)desired,
					expected_val);
			*(long*)expected = prev_val;
			res = prev_val == expected_val;
		}
		break;
	default:
		{
			__int64 expected_val = *(__int64*)expected;
			__int64 prev_val =
				_InterlockedCompareExchange64(
					(__int64*)a,
					(__int64)desired,
					expected_val);
			*(__int64*)expected = prev_val;
			res = prev_val == expected_val;
		}
		break;
	}

	return res;
}

#define re_atomic_compare_exchange_strong(\
	_a, _expected, _desired, _success_mo, _fail_mo) \
	_re_atomic_compare_exchange_strong(\
		sizeof(*(_a)), _a, _expected, _desired)

#define re_atomic_compare_exchange_weak(\
	_a, _expected, _desired, _success_mo, _fail_mo) \
	re_atomic_compare_exchange_strong(\
		_a, _expected, _desired, _success_mo, _fail_mo)

static __forceinline unsigned __int64 _re_atomic_fetch_add(
	size_t size, void *a, unsigned __int64 v)
{
	unsigned __int64 prev_val;
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	switch (size) {
	case 1u:
		prev_val = _InterlockedExchangeAdd8((char*)a, (char)v);
		break;
	case 2u:
		prev_val = _InterlockedExchangeAdd16((short*)a, (short)v);
		break;
	case 4u:
		prev_val = _InterlockedExchangeAdd((long*)a, (long)v);
		break;
	default:
#if defined(_M_IX86)
		{
			_ReadWriteBarrier();
			prev_val = *(const volatile __int64*)(a);
			while (1) {
				__int64 new_val = prev_val + v;
				__int64 prev_val2 =
					 _InterlockedCompareExchange64(
						(__int64*)a,
						(__int64)new_val,
						(__int64)prev_val);
				if (prev_val2 == prev_val)
					break;
				prev_val = prev_val2;
			}
			_ReadWriteBarrier();
		}
#else
		prev_val = _InterlockedExchangeAdd64((__int64*)a, (__int64)v);
#endif
		break;
	}

	return prev_val;
}

#define re_atomic_fetch_add(_a, _v, _mo) \
	_re_atomic_fetch_add(sizeof(*(_a)), _a, _v)

#define re_atomic_fetch_sub(_a, _v, _mo) \
	re_atomic_fetch_add(_a, -(__int64)(_v), _mo)

static __forceinline unsigned __int64 _re_atomic_fetch_or(
	size_t size, void *a, unsigned __int64 v)
{
	unsigned __int64 prev_val;
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	switch (size) {
	case 1u:
		prev_val = _InterlockedOr8((char*)a, (char)v);
		break;
	case 2u:
		prev_val = _InterlockedOr16((short*)a, (short)v);
		break;
	case 4u:
		prev_val = _InterlockedOr((long*)a, (long)v);
		break;
	default:
#if defined(_M_IX86)
		{
			_ReadWriteBarrier();
			prev_val = *(const volatile __int64*)(a);
			while (1) {
				__int64 new_val = prev_val | v;
				__int64 prev_val2 =
					_InterlockedCompareExchange64(
						(__int64*)a,
						(__int64)new_val,
						(__int64)prev_val);
				if (prev_val2 == prev_val)
					break;
				prev_val = prev_val2;
			}
			_ReadWriteBarrier();
		}
#else
		prev_val = _InterlockedOr64((__int64*)a, (__int64)v);
#endif
		break;
	}

	return prev_val;
}

#define re_atomic_fetch_or(_a, _v, _mo) \
	_re_atomic_fetch_or(sizeof(*(_a)), _a, _v)

static __forceinline unsigned __int64 _re_atomic_fetch_xor(
	size_t size, void *a, unsigned __int64 v)
{
	unsigned __int64 prev_val;
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	switch (size) {
	case 1u:
		prev_val = _InterlockedXor8((char*)a, (char)v);
		break;
	case 2u:
		prev_val = _InterlockedXor16((short*)a, (short)v);
		break;
	case 4u:
		prev_val = _InterlockedXor((long*)a, (long)v);
		break;
	default:
#if defined(_M_IX86)
		{
			_ReadWriteBarrier();
			prev_val = *(const volatile __int64*)(a);
			while (1) {
				__int64 new_val = prev_val ^ v;
				__int64 prev_val2 =
					_InterlockedCompareExchange64(
						(__int64*)a,
						(__int64)new_val,
						(__int64)prev_val);
				if (prev_val2 == prev_val)
					break;
				prev_val = prev_val2;
			}
			_ReadWriteBarrier();
		}
#else
		prev_val = _InterlockedXor64((__int64*)a, (__int64)v);
#endif
		break;
	}

	return prev_val;
}

#define re_atomic_fetch_xor(_a, _v, _mo) \
	_re_atomic_fetch_xor(sizeof(*(_a)), _a, _v)

static __forceinline unsigned __int64 _re_atomic_fetch_and(
	size_t size, void *a, unsigned __int64 v)
{
	unsigned __int64 prev_val;
	assert(size == 1u || size == 2u || size == 4u || size == 8u);
	switch (size) {
	case 1u:
		prev_val = _InterlockedAnd8((char*)a, (char)v);
		break;
	case 2u:
		prev_val = _InterlockedAnd16((short*)a, (short)v);
		break;
	case 4u:
		prev_val = _InterlockedAnd((long*)a, (long)v);
		break;
	default:
#if defined(_M_IX86)
		{
			_ReadWriteBarrier();
			prev_val = *(const volatile __int64*)(a);
			while (1) {
				__int64 new_val = prev_val & v;
				__int64 prev_val2 =
					_InterlockedCompareExchange64(
						(__int64*)a,
						(__int64)new_val,
						(__int64)prev_val);
				if (prev_val2 == prev_val)
					break;
				prev_val = prev_val2;
			}
			_ReadWriteBarrier();
		}
#else
		prev_val = _InterlockedAnd64((__int64*)a, (__int64)v);
#endif
		break;
	}

	return prev_val;
}

#define re_atomic_fetch_and(_a, _v, _mo) \
	_re_atomic_fetch_and(sizeof(*(_a)), _a, _v)

#ifdef __cplusplus
} /* extern "C" */
#endif

#else
#error "Compiler does not support atomics"
#endif /* HAVE_ATOMIC */

#ifndef RE_ATOMIC
#define RE_ATOMIC
#endif


/* --- Some short alias helpers --- */

/**
 * @def re_atomic_rlx(_a)
 *
 * Load value from an atomic object with relaxed order
 *
 * @param _a  pointer to the atomic object
 *
 * @return value of the atomic variable
 */
#define re_atomic_rlx(_a) re_atomic_load(_a, re_memory_order_relaxed)


/**
 * @def re_atomic_rlx_set(_a, _v)
 *
 * Store value in an atomic object with relaxed order
 *
 * @param _a  pointer to the atomic object
 * @param _v  new value
 */
#define re_atomic_rlx_set(_a, _v)                                            \
	re_atomic_store(_a, _v, re_memory_order_relaxed)


/**
 * @def re_atomic_rlx_add(_a, _v)
 *
 * Replace value from an atomic object with addition and relaxed order
 *
 * @param _a  pointer to the atomic object
 * @param _v  value to add
 *
 * @return value held previously by the atomic variable
 */
#define re_atomic_rlx_add(_a, _v)                                            \
	re_atomic_fetch_add(_a, _v, re_memory_order_relaxed)


/**
 * @def re_atomic_rlx_sub(_a, _v)
 *
 * Replace value from an atomic object with subtraction and relaxed order
 *
 * @param _a  pointer to the atomic object
 * @param _v  value to subtract
 *
 * @return value held previously by the atomic variable
 */
#define re_atomic_rlx_sub(_a, _v)                                            \
	re_atomic_fetch_sub(_a, _v, re_memory_order_relaxed)


/**
 * @def re_atomic_acq(_a)
 *
 * Load value from an atomic object with acquire order
 *
 * @param _a  pointer to the atomic object
 *
 * @return value of the atomic variable
 */
#define re_atomic_acq(_a) re_atomic_load(_a, re_memory_order_acquire)


/**
 * @def re_atomic_rls_set(_a, _v)
 *
 * Store value in an atomic object with release order
 *
 * @param _a  pointer to the atomic object
 * @param _v  new value
 */
#define re_atomic_rls_set(_a, _v)                                          \
	re_atomic_store(_a, _v, re_memory_order_release)


/**
 * @def re_atomic_acq_add(_a, _v)
 *
 * Replace value from an atomic object with addition and acquire-release order
 *
 * @param _a  pointer to the atomic object
 * @param _v  value to add
 *
 * @return value held previously by the atomic variable
 */
#define re_atomic_acq_add(_a, _v)                                          \
	re_atomic_fetch_add(_a, _v, re_memory_order_acq_rel)


/**
 * @def re_atomic_acq_sub(_a, _v)
 *
 * Replace value from an atomic object with subtraction and acquire-release
 * order
 *
 * @param _a  pointer to the atomic object
 * @param _v  value to subtract
 *
 * @return value held previously by the atomic variable
 */
#define re_atomic_acq_sub(_a, _v)                                          \
	re_atomic_fetch_sub(_a, _v, re_memory_order_acq_rel)


/**
 * @def re_atomic_seq(_a)
 *
 * Load value from an atomic object with sequentially-consistent order
 *
 * @param _a  pointer to the atomic object
 *
 * @return value of the atomic variable
 */
#define re_atomic_seq(_a) re_atomic_load(_a, re_memory_order_seq_cst)


/**
 * @def re_atomic_seq_set(_a, _v)
 *
 * Store value in an atomic object with sequentially-consistent order
 *
 * @param _a  pointer to the atomic object
 * @param _v  new value
 */
#define re_atomic_seq_set(_a, _v)                                            \
	re_atomic_store(_a, _v, re_memory_order_seq_cst)


/**
 * @def re_atomic_seq_add(_a, _v)
 *
 * Replace value from an atomic object with addition and
 * sequentially-consistent order
 *
 * @param _a  pointer to the atomic object
 * @param _v  value to add
 *
 * @return value held previously by the atomic variable
 */
#define re_atomic_seq_add(_a, _v)                                            \
	re_atomic_fetch_add(_a, _v, re_memory_order_seq_cst)


/**
 * @def re_atomic_seq_sub(_a, _v)
 *
 * Replace value from an atomic object with subtraction and
 * sequentially-consistent order
 *
 * @param _a  pointer to the atomic object
 * @param _v  value to subtract
 *
 * @return value held previously by the atomic variable
 */
#define re_atomic_seq_sub(_a, _v)                                            \
	re_atomic_fetch_sub(_a, _v, re_memory_order_seq_cst)


#endif /* RE_H_ATOMIC__ */
