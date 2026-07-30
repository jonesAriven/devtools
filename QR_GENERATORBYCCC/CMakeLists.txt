cmake_minimum_required(VERSION 3.16)
project(QRCodeTool LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Win7兼容：全局声明最低支持系统版本，避免任何目标隐式链接Win8+ API
add_compile_definitions(_WIN32_WINNT=0x0601 WINVER=0x0601 NTDDI_VERSION=0x06010000)

# Static CRT linking (/MT)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded")
if(MSVC)
    # /GL = Whole Program Optimization, /Gy = Enable Function-Level Linking
    # /O2 = Max speed, /Os = Favor small code
    add_compile_options(/MT /O2 /Os /GL /Gy /W3 /EHsc /utf-8)
    add_compile_definitions(NOMINMAX)
    # /LTCG = Link-time Code Generation, /OPT:REF = Remove unused functions, /OPT:ICF = COMDAT folding
    add_link_options(/LTCG /OPT:REF /OPT:ICF)
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
# Only compile QR reader sources to minimize binary size
FetchContent_Declare(
    zxing
    GIT_REPOSITORY https://github.com/zxing-cpp/zxing-cpp.git
    GIT_TAG        v2.2.1
)
# Only download, don't build the full library
FetchContent_GetProperties(zxing)
if(NOT zxing_POPULATED)
    FetchContent_Populate(zxing)
endif()

# Create our own minimal ZXing static library with only QR reader support
set(_ZXING_QR_ONLY_SOURCES
    # Common files (required)
    ${zxing_SOURCE_DIR}/core/src/BarcodeFormat.cpp
    ${zxing_SOURCE_DIR}/core/src/BinaryBitmap.cpp
    ${zxing_SOURCE_DIR}/core/src/BitArray.cpp
    ${zxing_SOURCE_DIR}/core/src/BitMatrix.cpp
    ${zxing_SOURCE_DIR}/core/src/BitMatrixIO.cpp
    ${zxing_SOURCE_DIR}/core/src/BitSource.cpp
    ${zxing_SOURCE_DIR}/core/src/CharacterSet.cpp
    ${zxing_SOURCE_DIR}/core/src/ConcentricFinder.cpp
    ${zxing_SOURCE_DIR}/core/src/Content.cpp
    ${zxing_SOURCE_DIR}/core/src/DecodeHints.cpp
    ${zxing_SOURCE_DIR}/core/src/ECI.cpp
    ${zxing_SOURCE_DIR}/core/src/GenericGF.cpp
    ${zxing_SOURCE_DIR}/core/src/GenericGFPoly.cpp
    ${zxing_SOURCE_DIR}/core/src/GlobalHistogramBinarizer.cpp
    ${zxing_SOURCE_DIR}/core/src/GridSampler.cpp
    ${zxing_SOURCE_DIR}/core/src/GTIN.cpp
    ${zxing_SOURCE_DIR}/core/src/HRI.cpp
    ${zxing_SOURCE_DIR}/core/src/HybridBinarizer.cpp
    ${zxing_SOURCE_DIR}/core/src/PerspectiveTransform.cpp
    ${zxing_SOURCE_DIR}/core/src/ReedSolomonDecoder.cpp
    ${zxing_SOURCE_DIR}/core/src/Result.cpp
    ${zxing_SOURCE_DIR}/core/src/ResultPoint.cpp
    ${zxing_SOURCE_DIR}/core/src/TextDecoder.cpp
    ${zxing_SOURCE_DIR}/core/src/TextUtfEncoding.cpp
    ${zxing_SOURCE_DIR}/core/src/Utf.cpp
    ${zxing_SOURCE_DIR}/core/src/WhiteRectDetector.cpp
    # QR reader files
    ${zxing_SOURCE_DIR}/core/src/qrcode/QRBitMatrixParser.cpp
    ${zxing_SOURCE_DIR}/core/src/qrcode/QRCodecMode.cpp
    ${zxing_SOURCE_DIR}/core/src/qrcode/QRDataBlock.cpp
    ${zxing_SOURCE_DIR}/core/src/qrcode/QRDecoder.cpp
    ${zxing_SOURCE_DIR}/core/src/qrcode/QRDetector.cpp
    ${zxing_SOURCE_DIR}/core/src/qrcode/QRErrorCorrectionLevel.cpp
    ${zxing_SOURCE_DIR}/core/src/qrcode/QRFormatInformation.cpp
    ${zxing_SOURCE_DIR}/core/src/qrcode/QRReader.cpp
    ${zxing_SOURCE_DIR}/core/src/qrcode/QRVersion.cpp
    # libzueci
    ${zxing_SOURCE_DIR}/core/src/libzueci/zueci.c
)
add_library(zxing_qr STATIC ${_ZXING_QR_ONLY_SOURCES})
target_include_directories(zxing_qr PUBLIC ${zxing_SOURCE_DIR}/core/src)
target_compile_features(zxing_qr PUBLIC cxx_std_17)
target_compile_options(zxing_qr PRIVATE /MT /O2 /Os /GL /Gy /EHsc /utf-8)
target_compile_definitions(zxing_qr PRIVATE NOMINMAX UNICODE _UNICODE _CRT_SECURE_NO_WARNINGS)
set_property(TARGET zxing_qr PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
find_package(Threads REQUIRED)
target_link_libraries(zxing_qr PRIVATE Threads::Threads)

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

# --- Jones.Activation verifier library (C++ version) ---
set(JONES_ACTIVATION_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../active-manager/activation-code-verifier/cpp")
set(JONES_ACTIVATION_INCLUDE "${JONES_ACTIVATION_DIR}/include")
set(JONES_ACTIVATION_LIB "${JONES_ACTIVATION_DIR}/build/Release/JonesActivation.lib")

# --- Application ---

# Generate app.ico from gen_icon.ps1 if it doesn't exist or is outdated
set(APP_ICO "${CMAKE_CURRENT_SOURCE_DIR}/res/app.ico")
set(GEN_ICON_SCRIPT "${CMAKE_CURRENT_SOURCE_DIR}/gen_icon.ps1")
add_custom_command(
    OUTPUT "${APP_ICO}"
    COMMAND powershell -ExecutionPolicy Bypass -File "${GEN_ICON_SCRIPT}" -OutputPath "${APP_ICO}"
    DEPENDS "${GEN_ICON_SCRIPT}"
    COMMENT "Generating app.ico from gen_icon.ps1"
)
add_custom_target(generate_icon ALL DEPENDS "${APP_ICO}")

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
add_dependencies(QRCodeTool generate_icon)

target_include_directories(QRCodeTool PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_include_directories(QRCodeTool PRIVATE ${brotli_SOURCE_DIR}/c/include)
target_include_directories(QRCodeTool PRIVATE ${zxing_SOURCE_DIR}/core/src)
# Jones.Activation verifier headers
target_include_directories(QRCodeTool PRIVATE ${JONES_ACTIVATION_INCLUDE})

target_link_libraries(QRCodeTool PRIVATE
    qrcodegen_lib
    zxing_qr
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
    # Jones.Activation verifier library
    ${JONES_ACTIVATION_LIB}
    bcrypt
    crypt32
    advapi32
    wbemuuid
    oleaut32
    iphlpapi
)

# ==================== 版本配置 ====================
# VERSION_MODE: "auto" = 自动时间戳(默认，每次编译生成新版本号)
#              "manual" = 手动维护版本号
set(VERSION_MODE "auto")
set(MANUAL_VERSION "3.1.0")

if(VERSION_MODE STREQUAL "auto")
    string(TIMESTAMP APP_VERSION "V%Y%m%d%H%M")
else()
    set(APP_VERSION "${MANUAL_VERSION}")
endif()

string(TIMESTAMP BUILD_DATE "%Y-%m-%d %H:%M")

# Generate timestamp suffix for output filename (保持exe文件名不变)
string(TIMESTAMP BUILD_TIMESTAMP "%Y%m%d%H%M")

# 将版本信息通过生成头文件注入代码（比 compile_definitions 更可靠，尤其处理带空格的值）
# 输出到 src/ 目录（与源码同目录），避免额外的 include 路径问题
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/src/version.h.in"
    "${CMAKE_CURRENT_SOURCE_DIR}/src/version.h"
)

# 生成 resource.rc（注入版本号到 VERSIONINFO 资源）
# FILEVERSION 需要逗号分隔的数字: 年,月日,时,分 → 例如 2026,715,22,17
if(VERSION_MODE STREQUAL "auto")
    string(TIMESTAMP _YEAR "%Y")
    string(TIMESTAMP _MD "%m%d")
    string(TIMESTAMP _HM "%H%M")
    set(APP_VERSION_COMMA "${_YEAR},${_MD},${_HM}")
else()
    # 手动模式: 将 x.y.z 转为 x,y,z,0
    string(REPLACE "." "," APP_VERSION_COMMA "${MANUAL_VERSION},0")
endif()

configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/res/resource.rc.in"
    "${CMAKE_CURRENT_SOURCE_DIR}/res/resource.rc"
)

message(STATUS "版本信息: ${APP_VERSION} (构建于 ${BUILD_DATE})")

set_target_properties(QRCodeTool PROPERTIES
    WIN32_EXECUTABLE TRUE
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
    OUTPUT_NAME "QRCodeTool-${BUILD_TIMESTAMP}"
)
