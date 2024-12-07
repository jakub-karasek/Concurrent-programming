cmake_minimum_required (VERSION 3.20)
project (MIMUW-PTHREADS C)

set(CMAKE_CXX_STANDARD "17")
set(CMAKE_C_STANDARD "11")
set(CMAKE_C_FLAGS "-g -Wall -Wextra -Wno-sign-compare -Wno-unused-parameter -pthread")

# Platform-independent way of using the threads library.
# Linking with the cmake Threads::Threads object target might
# add -pthread or -lpthread flags to the compiler and linker on some systems.
# On Linux with glibc>=2.34 (or musl in Alpine Linux, or OS X), it does not.
# So we added -pthread to CMAKE_C_FLAGS, because gcc and clang docs suggest
# it might be required (perhaps more so in the future).
# As of 2024 it seems that the only thing it affects are:
# 1. profiling or profiling-guided optimization (-fprofile-generate),
#    skipping -pthread may lead to corrupted profile data;
# 2. it defines -D_REENTRANT, which is an obsolete way to tell
#    glibc you need thread-safe functions. Some obscure libraries
#    or libc implementations might still need it.
# See: https://stackoverflow.com/a/77984633/12903633
# and: https://gitlab.kitware.com/cmake/cmake/-/issues/23092
set(THREADS_PREFER_PTHREAD_FLAG TRUE)
find_package(Threads REQUIRED)

function(add_example_pthread_program target_name)
  add_executable(${target_name} "${target_name}.c")
  target_link_libraries(${target_name} err)
  target_link_libraries(${target_name} Threads::Threads)
endfunction()

add_library(err err.c)

add_example_pthread_program(readers-writers)
