cmake_minimum_required(VERSION 3.22.1)
project(xora_libretro)

# In-process mGBA Game Link (MPL-2.0). LIBMGBA_ONLY skips Qt/SDL/deps.
# Do not use FetchContent URL here: the Android NDK toolchain rewrites it
# into a local path and ExternalProject fails.
set(LIBMGBA_ONLY ON CACHE BOOL "Build only libmgba" FORCE)
set(M_CORE_GBA ON CACHE BOOL "" FORCE)
set(M_CORE_GB OFF CACHE BOOL "" FORCE)
set(ENABLE_DEBUGGERS OFF CACHE BOOL "" FORCE)
set(ENABLE_SCRIPTING OFF CACHE BOOL "" FORCE)
set(BUILD_QT OFF CACHE BOOL "" FORCE)
set(BUILD_SDL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(DISABLE_FRONTENDS ON CACHE BOOL "" FORCE)
set(DISABLE_DEPS ON CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(MGBA_SRC "${CMAKE_CURRENT_SOURCE_DIR}/third_party/mgba")
if(NOT EXISTS "${MGBA_SRC}/CMakeLists.txt")
    message(STATUS "Cloning mGBA into ${MGBA_SRC}")
    execute_process(
        COMMAND git clone --depth 1 https://github.com/mgba-emu/mgba.git "${MGBA_SRC}"
        RESULT_VARIABLE MGBA_CLONE
    )
    if(NOT MGBA_CLONE EQUAL 0)
        message(FATAL_ERROR "Could not clone mGBA for GBA Game Link")
    endif()
endif()
add_subdirectory("${MGBA_SRC}" "${CMAKE_CURRENT_BINARY_DIR}/mgba" EXCLUDE_FROM_ALL)

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
    xora_gba_link.cpp
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
    ${MGBA_SRC}/include
    ${MGBA_SRC}/src
    ${CMAKE_CURRENT_BINARY_DIR}/mgba/include
)

target_compile_definitions(xora_libretro PRIVATE
    RC_DISABLE_LUA=1
    BUILD_STATIC
    M_CORE_GBA
    ENABLE_VFS
)

target_link_libraries(xora_libretro
    ${log-lib}
    ${android-lib}
    ${EGL-lib}
    ${GLESv2-lib}
    ${z-lib}
    dl
    mgba
)

target_compile_features(xora_libretro PUBLIC cxx_std_20)
# rcheevos is C; keep warnings softer there.
target_compile_options(xora_libretro PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-Wall -Wextra -fno-exceptions -fno-rtti>
    $<$<COMPILE_LANGUAGE:C>:-Wno-unused-parameter>
)
