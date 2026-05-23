cmake_minimum_required(VERSION 3.16)
project(JonesActivation LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Static CRT linking (/MT)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded")
if(MSVC)
    add_compile_options(/MT /O2 /Os /GL /Gy /W3 /EHsc /utf-8)
    add_compile_definitions(NOMINMAX UNICODE _UNICODE)
    add_link_options(/LTCG /OPT:REF /OPT:ICF)
endif()

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
    bcrypt
    crypt32
    advapi32
    wbemuuid
    oleaut32
    iphlpapi
    shell32
    ole32
    comctl32
)
