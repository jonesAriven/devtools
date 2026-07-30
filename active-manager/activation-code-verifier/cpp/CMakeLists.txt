cmake_minimum_required(VERSION 3.16)
project(JonesActivation LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Static CRT linking (/MT)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded")
if(MSVC)
    add_compile_options(/MT /O2 /Os /GL /Gy /W3 /EHsc /utf-8)
    add_compile_definitions(NOMINMAX UNICODE _UNICODE)
    # Win7兼容：显式声明最小支持系统版本，避免隐式链接Win8+ API
    add_compile_definitions(_WIN32_WINNT=0x0601 WINVER=0x0601 NTDDI_VERSION=0x06010000)
    add_link_options(/LTCG /OPT:REF /OPT:ICF)
endif()

# --- FetchContent for qrcodegen ---
include(FetchContent)
set(FETCHCONTENT_QUIET OFF)

FetchContent_Declare(
    qrcodegen
    GIT_REPOSITORY https://github.com/nayuki/QR-Code-generator.git
    GIT_TAG        v1.8.0
)
FetchContent_GetProperties(qrcodegen)
if(NOT qrcodegen_POPULATED)
    FetchContent_Populate(qrcodegen)
endif()
add_library(qrcodegen_lib STATIC ${qrcodegen_SOURCE_DIR}/c/qrcodegen.c)
target_include_directories(qrcodegen_lib PUBLIC ${qrcodegen_SOURCE_DIR}/c)
target_compile_options(qrcodegen_lib PRIVATE /MT /O2 /Os /GL /Gy /EHsc)
set_property(TARGET qrcodegen_lib PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")

# Jones.Activation static library
set(VERIFIER_SOURCES
    src/ActivationCrypto.cpp
    src/ActivationAntiDebug.cpp
    src/ActivationDeviceInfo.cpp
    src/ActivationTimeGuard.cpp
    src/ActivationSecureStorage.cpp
    src/ActivationVerifier.cpp
    src/ActivationGuard.cpp
)

add_library(JonesActivation STATIC ${VERIFIER_SOURCES})

target_include_directories(JonesActivation PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(JonesActivation PUBLIC
    qrcodegen_lib
    bcrypt
    crypt32
    advapi32
    wbemuuid
    oleaut32
    iphlpapi
    shell32
    ole32
    comctl32
    gdiplus
)
