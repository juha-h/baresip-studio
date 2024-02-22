/**
 * @file re_btrace.h  Backtrace API
 *
 */
#define BTRACE_SZ 16

struct btrace {
	void *stack[BTRACE_SZ];
	size_t len;
};

int btrace_print(struct re_printf *pf, struct btrace *bt);
int btrace_println(struct re_printf *pf, struct btrace *bt);
int btrace_print_json(struct re_printf *pf, struct btrace *bt);

#if defined(HAVE_EXECINFO) && !defined(RELEASE)
#include <execinfo.h>
static inline int btrace(struct btrace *bt)
{
	if (!bt)
		return EINVAL;

	bt->len = backtrace(bt->stack, BTRACE_SZ);

	return 0;
}
#elif defined(WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
static inline int btrace(struct btrace *bt)
{
	if (!bt)
		return EINVAL;

	bt->len = CaptureStackBackTrace(0, BTRACE_SZ, bt->stack, NULL);

	return 0;
}
#else
static inline int btrace(struct btrace *bt)
{
	(void)bt;
	return 0;
}
#endif
