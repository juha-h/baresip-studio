/**
 * @file re_pcp.h  PCP - Port Control Protocol (RFC 6887)
 *
 * Copyright (C) 2010 - 2014 Alfred E. Heggestad
 */


/*
 * The following specifications are implemented:
 *
 *   RFC 6887
 *   draft-ietf-pcp-description-option-02
 *   draft-cheshire-pcp-unsupp-family
 *
 */


/** PCP Version numbers */
enum {
	PCP_VERSION = 2,
};

/* PCP port numbers */
enum {
	PCP_PORT_CLI   = 5350,  /* for ANNOUNCE notifications */
	PCP_PORT_SRV   = 5351,
};

/** PCP Protocol sizes */
enum {
	PCP_HDR_SZ   = 24,
	PCP_NONCE_SZ = 12,
	PCP_MAP_SZ   = 36,
	PCP_PEER_SZ  = 56,

	PCP_MIN_PACKET =   24,
	PCP_MAX_PACKET = 1100
};

enum pcp_opcode {
	PCP_ANNOUNCE = 0,
	PCP_MAP      = 1,
	PCP_PEER     = 2,
};

enum pcp_result {
	PCP_SUCCESS                 =  0,
	PCP_UNSUPP_VERSION          =  1,
	PCP_NOT_AUTHORIZED          =  2,
	PCP_MALFORMED_REQUEST       =  3,
	PCP_UNSUPP_OPCODE           =  4,
	PCP_UNSUPP_OPTION           =  5,
	PCP_MALFORMED_OPTION        =  6,
	PCP_NETWORK_FAILURE         =  7,
	PCP_NO_RESOURCES            =  8,
	PCP_UNSUPP_PROTOCOL         =  9,
	PCP_USER_EX_QUOTA           = 10,
	PCP_CANNOT_PROVIDE_EXTERNAL = 11,
	PCP_ADDRESS_MISMATCH        = 12,
	PCP_EXCESSIVE_REMOTE_PEERS  = 13,
};

enum pcp_option_code {
	PCP_OPTION_THIRD_PARTY    =   1,
	PCP_OPTION_PREFER_FAILURE =   2,
	PCP_OPTION_FILTER         =   3,
	PCP_OPTION_DESCRIPTION    = 128,  /* RFC 7220 */
};

/* forward declarations */
struct udp_sock;

/** Defines a PCP option */
struct pcp_option {
	struct le le;
	enum pcp_option_code code;
	union {
		struct sa third_party;          /* Internal IP-address */
		struct pcp_option_filter {
			uint8_t prefix_length;
			struct sa remote_peer;
		} filter;
		char *description;
	} u;
};

/**
 * Defines a complete and decoded PCP request/response.
 *
 * A PCP message consist of a header, and optional payload and options:
 *
 *     [      Header    ]
 *     ( Opcode Payload )
 *     (   PCP Options  )
 *
 */
struct pcp_msg {

	/** PCP Common Header */
	struct pcp_hdr {
		uint8_t version;        /**< PCP Protocol version 2        */
		unsigned resp:1;        /**< R-bit; 0=Request, 1=Response  */
		uint8_t opcode;         /**< A 7-bit opcode                */
		uint32_t lifetime;      /**< Lifetime in [seconds]         */

		/* request: */
		struct sa cli_addr;     /**< Client's IP Address (SA_ADDR) */

		/* response: */
		enum pcp_result result; /**< Result code for this response */
		uint32_t epoch;         /**< Server's Epoch Time [seconds] */
	} hdr;

	/** PCP Opcode-specific payload */
	union pcp_payload {
		struct pcp_map {
			uint8_t nonce[PCP_NONCE_SZ]; /**< Mapping Nonce    */
			uint8_t proto;               /**< IANA protocol    */
			uint16_t int_port;           /**< Internal Port    */
			struct sa ext_addr;          /**< External Address */
		} map;
		struct pcp_peer {
			struct pcp_map map;          /**< Common with MAP  */
			struct sa remote_addr;       /**< Remote address   */
		} peer;
	} pld;

	/** List of PCP Options (struct pcp_option) */
	struct list optionl;
};

/** PCP request configuration */
struct pcp_conf {
	uint32_t irt;  /**< Initial retransmission time [seconds]     */
	uint32_t mrc;  /**< Maximum retransmission count              */
	uint32_t mrt;  /**< Maximum retransmission time [seconds]     */
	uint32_t mrd;  /**< Maximum retransmission duration [seconds] */
};


/* request */

struct pcp_request;

typedef void (pcp_resp_h)(int err, struct pcp_msg *msg, void *arg);

int pcp_request(struct pcp_request **reqp, const struct pcp_conf *conf,
		const struct sa *pcp_server, enum pcp_opcode opcode,
		uint32_t lifetime, const void *payload,
		pcp_resp_h *resph, void *arg, uint32_t optionc, ...);
void pcp_force_refresh(struct pcp_request *req);


/* reply */

int pcp_reply(struct udp_sock *us, const struct sa *dst, struct mbuf *req,
	      enum pcp_opcode opcode, enum pcp_result result,
	      uint32_t lifetime, uint32_t epoch_time, const void *payload);


/* msg */

typedef bool (pcp_option_h)(const struct pcp_option *opt, void *arg);

int pcp_msg_decode(struct pcp_msg **msgp, struct mbuf *mb);
int pcp_msg_printhdr(struct re_printf *pf, const struct pcp_msg *msg);
int pcp_msg_print(struct re_printf *pf, const struct pcp_msg *msg);
struct pcp_option *pcp_msg_option(const struct pcp_msg *msg,
				  enum pcp_option_code code);
struct pcp_option *pcp_msg_option_apply(const struct pcp_msg *msg,
					pcp_option_h *h, void *arg);
const void *pcp_msg_payload(const struct pcp_msg *msg);


/* option */

int pcp_option_encode(struct mbuf *mb, enum pcp_option_code code,
		      const void *v);
int pcp_option_decode(struct pcp_option **optp, struct mbuf *mb);
int pcp_option_print(struct re_printf *pf, const struct pcp_option *opt);


/* encode */

int pcp_msg_req_vencode(struct mbuf *mb, enum pcp_opcode opcode,
			uint32_t lifetime, const struct sa *cli_addr,
			const void *payload, uint32_t optionc, va_list ap);
int pcp_msg_req_encode(struct mbuf *mb, enum pcp_opcode opcode,
		       uint32_t lifetime, const struct sa *cli_addr,
		       const void *payload, uint32_t optionc, ...);


/* pcp */

int pcp_ipaddr_encode(struct mbuf *mb, const struct sa *sa);
int pcp_ipaddr_decode(struct mbuf *mb, struct sa *sa);
const char *pcp_result_name(enum pcp_result result);
const char *pcp_opcode_name(enum pcp_opcode opcode);
const char *pcp_proto_name(int proto);
