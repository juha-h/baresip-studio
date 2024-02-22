/**
 * @file re_odict.h  Interface to Ordered Dictionary
 *
 * Copyright (C) 2010 - 2015 Creytiv.com
 */

enum odict_type {
	ODICT_ERR = -1,
	ODICT_OBJECT,
	ODICT_ARRAY,
	ODICT_STRING,
	ODICT_INT,
	ODICT_DOUBLE,
	ODICT_BOOL,
	ODICT_NULL
};

struct odict {
	struct list lst;
	struct hash *ht;
};

struct odict_entry;

int odict_alloc(struct odict **op, uint32_t hash_size);
const struct odict_entry *odict_lookup(const struct odict *o, const char *key);
size_t odict_count(const struct odict *o, bool nested);
int odict_debug(struct re_printf *pf, const struct odict *o);

int odict_entry_add(struct odict *o, const char *key,
		    int type, ...);
void odict_entry_del(struct odict *o, const char *key);
int odict_entry_debug(struct re_printf *pf, const struct odict_entry *e);
bool odict_compare(const struct odict *dict1, const struct odict *dict2,
	bool ignore_order);

bool odict_type_iscontainer(enum odict_type type);
bool odict_type_isreal(enum odict_type type);
const char *odict_type_name(enum odict_type type);


/* Odict Helpers */

const struct odict_entry *odict_get_type(const struct odict *o,
					enum odict_type type, const char *key);
const char *odict_string(const struct odict *o, const char *key);
bool odict_get_number(const struct odict *o, uint64_t *num, const char *key);
bool odict_get_boolean(const struct odict *o, bool *value, const char *key);
struct odict *odict_get_object(const struct odict *o, const char *key);
struct odict *odict_get_array(const struct odict *o, const char *key);


/* Entry Helpers */

enum odict_type odict_entry_type(const struct odict_entry *e);
const char *odict_entry_key(const struct odict_entry *e);
struct odict *odict_entry_object(const struct odict_entry *e);
struct odict *odict_entry_array(const struct odict_entry *e);
char *odict_entry_str(const struct odict_entry *e);
int64_t odict_entry_int(const struct odict_entry *e);
double odict_entry_dbl(const struct odict_entry *e);
bool odict_entry_boolean(const struct odict_entry *e);
bool odict_value_compare(const struct odict_entry *e1,
	const struct odict_entry *e2, bool ignore_order);
