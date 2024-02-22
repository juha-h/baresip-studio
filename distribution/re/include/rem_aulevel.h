

/*
 * Audio-level
 */


#define AULEVEL_UNDEF (-128.0)
#define AULEVEL_MIN    (-96.0)
#define AULEVEL_MAX      (0.0)


double aulevel_calc_dbov(int fmt, const void *sampv, size_t sampc);
