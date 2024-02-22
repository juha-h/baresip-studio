/**
 * @file re_async.h async
 *
 * Copyright (C) 2022 Sebastian Reimers
 */

#ifndef RE_H_ASYNC__
#define RE_H_ASYNC__
struct re_async;

typedef int(re_async_work_h)(void *arg);
typedef void(re_async_h)(int err, void *arg);

int re_async_alloc(struct re_async **asyncp, uint16_t workers);
int re_async(struct re_async *a, intptr_t id, re_async_work_h *workh,
	     re_async_h *cb, void *arg);
void re_async_cancel(struct re_async *async, intptr_t id);

#endif
