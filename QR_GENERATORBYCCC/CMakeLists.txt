cmake_minimum_required(VERSION 3.16)
project(QRCodeTool LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Static CRT linking (/MT)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded")
if(MSVC)
    add_compile_options(/MT /O2 /W3 /EHsc /utf-8)
    add_compile_definitions(NOMINMAX)
    add_link_options(/OPT:REF /OPT:ICF)
endif()

# --- FetchContent for third-party libraries ---
include(FetchContent)
set(FETCHCONTENT_QUIET OFF)

# qrcodegen - QR code generation (Nayuki)
# Just download, then add source files manually (no CMakeLists.txt in c/ subdir)
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

# zxing-cpp - QR code decoding (C++ port of ZXing, same engine as C# version)
FetchContent_Declare(
    zxing
    GIT_REPOSITORY https://github.com/zxing-cpp/zxing-cpp.git
    GIT_TAG        v2.2.1
)
set(ZXING_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ZXING_BUILD_BLACKBOX_TESTS OFF CACHE BOOL "" FORCE)
set(ZXING_BUILD_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(ZXING_BUILD_C_API OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(zxing)

# Force zxing to use static CRT (/MT) to match our application
# ZXing::ZXing is an alias, use the real target name ZXing
set_property(TARGET ZXing PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")

# brotli - compression (has CMakeLists.txt)
FetchContent_Declare(
    brotli
    GIT_REPOSITORY https://github.com/google/brotli.git
    GIT_TAG        v1.1.0
)
set(BROTLI_BUNDLED_MODE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(brotli)

# Force brotli to use static CRT (/MT) to match our application
set_property(TARGET brotlicommon PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
set_property(TARGET brotlidec PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
set_property(TARGET brotlienc PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")

# zlib - GZip decompression (has CMakeLists.txt)
FetchContent_Declare(
    zlib
    GIT_REPOSITORY https://github.com/madler/zlib.git
    GIT_TAG        v1.3.1
)
set(ZLIB_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(zlib)

# Force zlib to use static CRT (/MT) to match our application
set_property(TARGET zlibstatic PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
if(TARGET zlib)
    set_property(TARGET zlib PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
endif()

# --- Application ---
set(APP_SOURCES
    src/main.cpp
    src/MainWindow.cpp
    src/QrGenerator.cpp
    src/QrDecoder.cpp
    src/Compressor.cpp
    src/ScreenCapture.cpp
    src/ImageProcess.cpp
    src/Base45.cpp
)

add_executable(QRCodeTool WIN32 ${APP_SOURCES} res/resource.rc)

target_include_directories(QRCodeTool PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_include_directories(QRCodeTool PRIVATE ${brotli_SOURCE_DIR}/c/include)
target_include_directories(QRCodeTool PRIVATE ${zxing_SOURCE_DIR}/core/src)

target_link_libraries(QRCodeTool PRIVATE
    qrcodegen_lib
    ZXing::ZXing
    brotlicommon
    brotlidec
    brotlienc
    zlibstatic
    gdiplus
    msimg32
    comdlg32
    comctl32
    shell32
    ole32
)

set_target_properties(QRCodeTool PROPERTIES
    WIN32_EXECUTABLE TRUE
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
)
