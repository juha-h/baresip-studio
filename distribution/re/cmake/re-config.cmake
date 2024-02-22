include(CheckIncludeFile)
include(CheckFunctionExists)
include(CheckSymbolExists)
include(CheckTypeSize)
include(CheckCXXSourceCompiles)

option(USE_MBEDTLS "Enable MbedTLS" OFF)

find_package(Backtrace)
find_package(Threads REQUIRED)
find_package(ZLIB)

if (USE_MBEDTLS)
find_package(MBEDTLS)
else()
find_package(OpenSSL "1.1.1")
endif()

option(USE_OPENSSL "Enable OpenSSL" ${OPENSSL_FOUND})
option(USE_UNIXSOCK "Enable Unix Domain Sockets" ON)
option(USE_TRACE "Enable Tracing helpers" OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    message(STATUS "Setting build type to 'Debug' as none was specified.")
    set(CMAKE_BUILD_TYPE "Debug" CACHE STRING "Choose the type of build."
        FORCE)
endif()
    
check_symbol_exists("arc4random" "stdlib.h" HAVE_ARC4RANDOM)
if(HAVE_ARC4RANDOM)
  list(APPEND RE_DEFINITIONS HAVE_ARC4RANDOM)
endif()

if(ZLIB_FOUND)
  list(APPEND RE_DEFINITIONS USE_ZLIB)
endif()

check_include_file(syslog.h HAVE_SYSLOG_H)
if(HAVE_SYSLOG_H)
  list(APPEND RE_DEFINITIONS HAVE_SYSLOG)
endif()

check_include_file(getopt.h HAVE_GETOPT_H)
if(HAVE_GETOPT_H)
  list(APPEND RE_DEFINITIONS HAVE_GETOPT)
endif()

check_include_file(unistd.h HAVE_UNISTD_H)
if(HAVE_UNISTD_H)
  list(APPEND RE_DEFINITIONS HAVE_UNISTD_H)
endif()

if(${CMAKE_SYSTEM_NAME} MATCHES "OpenBSD")
  check_symbol_exists(res_init resolv.h HAVE_RESOLV)
else()
  check_symbol_exists(res_ninit resolv.h HAVE_RESOLV)
endif()
if(HAVE_RESOLV)
  set(RESOLV_LIBRARY resolv)
  list(APPEND RE_DEFINITIONS HAVE_RESOLV)
else()
  set(RESOLV_LIBRARY)
endif()

if(Backtrace_FOUND)
  list(APPEND RE_DEFINITIONS HAVE_EXECINFO)
else()
  set(Backtrace_LIBRARIES)
endif()

check_function_exists(thrd_create HAVE_THREADS_FUN)
check_include_file(threads.h HAVE_THREADS_H)
if(HAVE_THREADS_FUN AND HAVE_THREADS_H)
  set(HAVE_THREADS CACHE BOOL true)
endif()
if(HAVE_THREADS)
  list(APPEND RE_DEFINITIONS HAVE_THREADS)
endif()

check_function_exists(accept4 HAVE_ACCEPT4)
if(HAVE_ACCEPT4)
  list(APPEND RE_DEFINITIONS HAVE_ACCEPT4)
endif()

if(CMAKE_USE_PTHREADS_INIT)
  list(APPEND RE_DEFINITIONS HAVE_PTHREAD)
  set(HAVE_PTHREAD ON)
endif()

if(UNIX)
  check_symbol_exists(epoll_create "sys/epoll.h" HAVE_EPOLL)
  if(HAVE_EPOLL)
    list(APPEND RE_DEFINITIONS HAVE_EPOLL)
  endif()
  check_symbol_exists(kqueue "sys/types.h;sys/event.h" HAVE_KQUEUE)
  if(HAVE_KQUEUE)
    list(APPEND RE_DEFINITIONS HAVE_KQUEUE)
  endif()
endif()

check_include_file(sys/prctl.h HAVE_PRCTL)
if(HAVE_PRCTL)
  list(APPEND RE_DEFINITIONS HAVE_PRCTL)
endif()


list(APPEND RE_DEFINITIONS
  HAVE_ATOMIC
  HAVE_INET6
  HAVE_SELECT
  )

if(UNIX)
  list(APPEND RE_DEFINITIONS
    HAVE_PWD_H
    HAVE_ROUTE_LIST
    HAVE_SETRLIMIT
    HAVE_STRERROR_R
    HAVE_STRINGS_H
    HAVE_SYS_TIME_H
    HAVE_UNAME
    HAVE_SELECT_H
    HAVE_SIGNAL
    HAVE_FORK
    )
  if(NOT ANDROID)
    list(APPEND RE_DEFINITIONS HAVE_GETIFADDRS)
  endif()
endif()


if(MSVC)
  list(APPEND RE_DEFINITIONS
    HAVE_IO_H
    _CRT_SECURE_NO_WARNINGS
  )
endif()

if(WIN32)
  list(APPEND RE_DEFINITIONS
    WIN32 
    _WIN32_WINNT=0x0600
  )

  unset(CMAKE_EXTRA_INCLUDE_FILES)
  set(CMAKE_EXTRA_INCLUDE_FILES "winsock2.h;qos2.h")
  check_type_size("QOS_FLOWID" HAVE_QOS_FLOWID BUILTIN_TYPES_ONLY)
  check_type_size("PQOS_FLOWID" HAVE_PQOS_FLOWID BUILTIN_TYPES_ONLY)
  unset(CMAKE_EXTRA_INCLUDE_FILES)

  if(HAVE_QOS_FLOWID)
    list(APPEND RE_DEFINITIONS HAVE_QOS_FLOWID)
  endif()

  if(HAVE_PQOS_FLOWID)
    list(APPEND RE_DEFINITIONS HAVE_PQOS_FLOWID)
  endif()
endif()

if(USE_OPENSSL)
  list(APPEND RE_DEFINITIONS
    USE_DTLS
    USE_OPENSSL
    USE_OPENSSL_AES
    USE_OPENSSL_HMAC
    USE_OPENSSL_SRTP
    USE_TLS
  )
endif()

if(USE_MBEDTLS)
  list(APPEND RE_DEFINITIONS
    USE_MBEDTLS
  )
endif()

if(USE_UNIXSOCK)
  list(APPEND RE_DEFINITIONS
    HAVE_UNIXSOCK=1
  )
else()
  list(APPEND RE_DEFINITIONS
    HAVE_UNIXSOCK=0
  )
endif()

if(USE_TRACE)
  list(APPEND RE_DEFINITIONS
    RE_TRACE_ENABLED
  )
endif()

if(${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
  list(APPEND RE_DEFINITIONS DARWIN)
  include_directories(/opt/local/include)
elseif(${CMAKE_SYSTEM_NAME} MATCHES "iOS")
  list(APPEND RE_DEFINITIONS DARWIN)
elseif(${CMAKE_SYSTEM_NAME} MATCHES "FreeBSD")
  list(APPEND RE_DEFINITIONS FREEBSD)
elseif(${CMAKE_SYSTEM_NAME} MATCHES "OpenBSD")
  list(APPEND RE_DEFINITIONS OPENBSD)
elseif(${CMAKE_SYSTEM_NAME} MATCHES "Linux")
  list(APPEND RE_DEFINITIONS LINUX)
endif()


list(APPEND RE_DEFINITIONS
  ARCH="${CMAKE_SYSTEM_PROCESSOR}"
  OS="${CMAKE_SYSTEM_NAME}"
  $<$<NOT:$<CONFIG:DEBUG>>:RELEASE>
)

if(NOT ${CMAKE_BUILD_TYPE} MATCHES "[Rr]el")
  if(Backtrace_FOUND)
    set(CMAKE_ENABLE_EXPORTS ON)
  endif()
endif()


##############################################################################
#
# Linking LIBS
#

set(RE_LIBS Threads::Threads ${RESOLV_LIBRARY})

if(BACKTRACE_FOUND)
  list(APPEND RE_LIBS ${Backtrace_LIBRARIES})
endif()

if(ZLIB_FOUND)
  list(APPEND RE_LIBS ZLIB::ZLIB)
endif()

if(USE_OPENSSL)
  list(APPEND RE_LIBS OpenSSL::SSL OpenSSL::Crypto)
endif()

if(${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
  list(APPEND RE_LIBS
    "-framework SystemConfiguration" "-framework CoreFoundation"
  )
endif()

if(WIN32)
  list(APPEND RE_LIBS
    qwave
    iphlpapi
    wsock32
    ws2_32
    dbghelp
  )
else()
  list(APPEND RE_LIBS m)
endif()

if(UNIX)
  list(APPEND RE_LIBS
    ${CMAKE_DL_LIBS}
  )
endif()


##############################################################################
#
# Testing Atomic
#

enable_language(CXX)

set(ATOMIC_TEST_CODE "
     #include <atomic>
     #include <cstdint>
     std::atomic<uint8_t> n8 (0); // riscv64
     std::atomic<uint64_t> n64 (0); // armel, mipsel, powerpc
     int main() {
       ++n8;
       ++n64;
       return 0;
  }")

check_cxx_source_compiles("${ATOMIC_TEST_CODE}" atomic_test)

if(NOT atomic_test)
  set(CMAKE_REQUIRED_LIBRARIES ${CMAKE_REQUIRED_LIBRARIES} atomic)
  check_cxx_source_compiles("${ATOMIC_TEST_CODE}" atomic_test_lib)
  if(NOT atomic_test_lib)
    message(FATAL_ERROR "No builtin or libatomic support")
  else()
    list(APPEND RE_LIBS atomic)
  endif()
endif()
