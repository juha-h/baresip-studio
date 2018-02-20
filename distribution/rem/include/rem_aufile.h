/**
 * @file rem_aufile.h Audio File interface
 *
 * Copyright (C) 2010 Creytiv.com
 */


/** Audio file mode */
enum aufile_mode {
	AUFILE_READ,
	AUFILE_WRITE,
};

/** Audio file parameters */
struct aufile_prm {
	uint32_t srate;
	uint8_t channels;
	enum aufmt fmt;
};

struct aufile;

int aufile_open(struct aufile **afp, struct aufile_prm *prm,
		const char *filename, enum aufile_mode mode);
int aufile_read(struct aufile *af, uint8_t *p, size_t *sz);
int aufile_write(struct aufile *af, const uint8_t *p, size_t sz);
