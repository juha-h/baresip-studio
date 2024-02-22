/**
 * @file re_sha.h  Interface to SHA (Secure Hash Standard) functions
 *
 * Copyright (C) 2010 Creytiv.com
 */

/** SHA-1 Digest size in bytes */
#define SHA1_DIGEST_SIZE 20
#define SHA256_DIGEST_SIZE 32
#define SHA512_DIGEST_SIZE 64

#ifndef SHA_DIGEST_LENGTH
/** SHA-1 Digest size in bytes (OpenSSL compat) */
#define SHA_DIGEST_LENGTH SHA1_DIGEST_SIZE
#endif

#ifndef SHA256_DIGEST_LENGTH
/** SHA-256 Digest size in bytes (OpenSSL compat) */
#define SHA256_DIGEST_LENGTH SHA256_DIGEST_SIZE
#endif

#ifndef SHA512_DIGEST_LENGTH
/** SHA-512 Digest size in bytes (OpenSSL compat) */
#define SHA512_DIGEST_LENGTH SHA512_DIGEST_SIZE
#endif

void sha1(const uint8_t *d, size_t n, uint8_t *md);
void sha256(const uint8_t *d, size_t n, uint8_t *md);
int  sha256_printf(uint8_t md[32], const char *fmt, ...);
