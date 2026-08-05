cmake_minimum_required(VERSION 3.22.1)
project(xora_libretro)

set(RCHEEVOS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/rcheevos)

set(RCHEEVOS_SOURCES
    ${RCHEEVOS_DIR}/src/rc_client.c
    ${RCHEEVOS_DIR}/src/rc_compat.c
    ${RCHEEVOS_DIR}/src/rc_libretro.c
    ${RCHEEVOS_DIR}/src/rc_util.c
    ${RCHEEVOS_DIR}/src/rc_version.c
    ${RCHEEVOS_DIR}/src/rapi/rc_api_common.c
    ${RCHEEVOS_DIR}/src/rapi/rc_api_editor.c
    ${RCHEEVOS_DIR}/src/rapi/rc_api_info.c
    ${RCHEEVOS_DIR}/src/rapi/rc_api_runtime.c
    ${RCHEEVOS_DIR}/src/rapi/rc_api_user.c
    ${RCHEEVOS_DIR}/src/rcheevos/alloc.c
    ${RCHEEVOS_DIR}/src/rcheevos/condition.c
    ${RCHEEVOS_DIR}/src/rcheevos/condset.c
    ${RCHEEVOS_DIR}/src/rcheevos/consoleinfo.c
    ${RCHEEVOS_DIR}/src/rcheevos/format.c
    ${RCHEEVOS_DIR}/src/rcheevos/lboard.c
    ${RCHEEVOS_DIR}/src/rcheevos/memref.c
    ${RCHEEVOS_DIR}/src/rcheevos/operand.c
    ${RCHEEVOS_DIR}/src/rcheevos/richpresence.c
    ${RCHEEVOS_DIR}/src/rcheevos/runtime.c
    ${RCHEEVOS_DIR}/src/rcheevos/runtime_progress.c
    ${RCHEEVOS_DIR}/src/rcheevos/rc_validate.c
    ${RCHEEVOS_DIR}/src/rcheevos/trigger.c
    ${RCHEEVOS_DIR}/src/rcheevos/value.c
    ${RCHEEVOS_DIR}/src/rhash/aes.c
    ${RCHEEVOS_DIR}/src/rhash/cdreader.c
    ${RCHEEVOS_DIR}/src/rhash/hash.c
    ${RCHEEVOS_DIR}/src/rhash/md5.c
    ${RCHEEVOS_DIR}/src/rurl/url.c
)

add_library(xora_libretro SHARED
    xora_libretro_host.cpp
    xora_hw_gl.cpp
    xora_ra_client.cpp
    ${RCHEEVOS_SOURCES}
)

find_library(log-lib log)
find_library(android-lib android)
find_library(EGL-lib EGL)
find_library(GLESv2-lib GLESv2)
find_library(z-lib z)

target_include_directories(xora_libretro PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${RCHEEVOS_DIR}/include
    ${RCHEEVOS_DIR}/src
    ${RCHEEVOS_DIR}/src/rcheevos
)

target_compile_definitions(xora_libretro PRIVATE
    RC_DISABLE_LUA=1
)

target_link_libraries(xora_libretro
    ${log-lib}
    ${android-lib}
    ${EGL-lib}
    ${GLESv2-lib}
    ${z-lib}
    dl
)

target_compile_features(xora_libretro PUBLIC cxx_std_20)
# rcheevos is C; keep warnings softer there.
target_compile_options(xora_libretro PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-Wall -Wextra -fno-exceptions -fno-rtti>
    $<$<COMPILE_LANGUAGE:C>:-Wno-unused-parameter>
)
