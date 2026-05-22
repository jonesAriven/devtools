#include "Compressor.h"
#include "Base45.h"
#include <brotli/encode.h>
#include <brotli/decode.h>
#include <zlib.h>
#include <stdexcept>
#include <fstream>
#include <ctime>
#include <sstream>

namespace qr {

static void logComp(const std::string& msg) {
    std::ofstream f("qr_debug.log", std::ios::app);
    if (f.is_open()) {
        time_t now = time(nullptr);
        struct tm t;
        localtime_s(&t, &now);
        char ts[32];
        strftime(ts, sizeof(ts), "%H:%M:%S", &t);
        f << "[" << ts << "][CMP] " << msg << std::endl;
    }
}

static std::vector<uint8_t> base64Decode(const std::string& encoded) {
    static const std::string base64_chars =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::vector<uint8_t> result;
    std::vector<int> T(256, -1);
    for (int i = 0; i < 64; i++) T[static_cast<unsigned char>(base64_chars[i])] = i;
    int val = 0, valb = -8;
    for (unsigned char c : encoded) {
        if (T[c] == -1) break;
        val = (val << 6) + T[c];
        valb += 6;
        if (valb >= 0) {
            result.push_back(static_cast<uint8_t>((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return result;
}

std::vector<uint8_t> brotliCompress(const std::string& data) {
    if (data.empty()) return {};

    size_t encoded_size = 0;
    BrotliEncoderCompress(
        BROTLI_MAX_QUALITY,
        22,
        BROTLI_MODE_GENERIC,
        data.size(),
        reinterpret_cast<const uint8_t*>(data.data()),
        &encoded_size,
        nullptr);

    // If size query didn't work, use a reasonable upper bound
    if (encoded_size == 0) {
        encoded_size = data.size() + (data.size() >> 2) + 1024;
    }

    std::vector<uint8_t> output(encoded_size);
    size_t actual_size = encoded_size;

    BROTLI_BOOL ok = BrotliEncoderCompress(
        BROTLI_MAX_QUALITY,
        22,
        BROTLI_MODE_GENERIC,
        data.size(),
        reinterpret_cast<const uint8_t*>(data.data()),
        &actual_size,
        output.data());

    if (!ok) {
        throw std::runtime_error("Brotli compression failed");
    }

    output.resize(actual_size);
    return output;
}

std::string brotliDecompress(const uint8_t* data, size_t len) {
    if (len == 0) return {};

    size_t decoded_size = 0;
    BrotliDecoderDecompress(len, data, &decoded_size, nullptr);

    if (decoded_size == 0) {
        // Use a reasonable initial size and grow as needed
        decoded_size = len * 10;
    }

    std::string output(decoded_size, '\0');
    size_t actual_size = decoded_size;

    BrotliDecoderResult result = BrotliDecoderDecompress(
        len, data, &actual_size, reinterpret_cast<uint8_t*>(output.data()));

    if (result != BROTLI_DECODER_RESULT_SUCCESS) {
        // Try streaming decompression as fallback
        BrotliDecoderState* state = BrotliDecoderCreateInstance(nullptr, nullptr, nullptr);
        if (!state) {
            throw std::runtime_error("Brotli decompression failed: cannot create decoder");
        }

        std::string out;
        const uint8_t* next_in = data;
        size_t avail_in = len;
        uint8_t buffer[8192];

        while (true) {
            uint8_t* next_out = buffer;
            size_t avail_out = sizeof(buffer);
            result = BrotliDecoderDecompressStream(state, &avail_in, &next_in, &avail_out, &next_out, nullptr);

            size_t written = sizeof(buffer) - avail_out;
            out.append(reinterpret_cast<char*>(buffer), written);

            if (result == BROTLI_DECODER_RESULT_SUCCESS) break;
            if (result == BROTLI_DECODER_RESULT_ERROR) {
                BrotliDecoderDestroyInstance(state);
                throw std::runtime_error("Brotli decompression failed");
            }
        }

        BrotliDecoderDestroyInstance(state);
        return out;
    }

    output.resize(actual_size);
    return output;
}

std::string gzipDecompress(const uint8_t* data, size_t len) {
    if (len == 0) return {};

    z_stream strm = {};
    // 15 + 16 for gzip decoding
    int ret = inflateInit2(&strm, 15 + 16);
    if (ret != Z_OK) {
        throw std::runtime_error("GZip decompression init failed");
    }

    strm.next_in = const_cast<uint8_t*>(data);
    strm.avail_in = static_cast<uInt>(len);

    std::string output;
    uint8_t buffer[8192];

    do {
        strm.next_out = buffer;
        strm.avail_out = sizeof(buffer);
        ret = inflate(&strm, Z_NO_FLUSH);

        if (ret == Z_STREAM_ERROR || ret == Z_DATA_ERROR || ret == Z_MEM_ERROR) {
            inflateEnd(&strm);
            throw std::runtime_error("GZip decompression failed");
        }

        size_t have = sizeof(buffer) - strm.avail_out;
        output.append(reinterpret_cast<char*>(buffer), have);
    } while (ret != Z_STREAM_END);

    inflateEnd(&strm);
    return output;
}

std::string compressText(const std::string& text, bool compress) {
    if (!compress) return text;

    // UTF-8 encode -> Brotli compress -> Base45 encode -> "B5:" prefix
    std::vector<uint8_t> compressed = brotliCompress(text);
    std::string encoded = base45Encode(compressed);
    return "B5:" + encoded;
}

std::string decompressText(const std::string& text) {
    const std::string B5_PREFIX = "B5:";
    const std::string GZ_PREFIX = "GZ:";

    logComp("decompressText: input_len=" + std::to_string(text.size()));

    if (text.compare(0, B5_PREFIX.size(), B5_PREFIX) == 0) {
        logComp("Detected B5: prefix (Brotli+Base45)");
        std::string encoded = text.substr(B5_PREFIX.size());
        logComp("Base45 encoded len=" + std::to_string(encoded.size()) + ", first 100: " + encoded.substr(0, 100));

        std::vector<uint8_t> decoded;
        try {
            decoded = base45Decode(encoded);
            logComp("Base45 decode OK, binary len=" + std::to_string(decoded.size()));
        } catch (const std::exception& e) {
            logComp(std::string("Base45 decode FAILED: ") + e.what());
            throw;
        }

        try {
            std::string result = brotliDecompress(decoded.data(), decoded.size());
            logComp("Brotli decompress OK, result len=" + std::to_string(result.size()));
            return result;
        } catch (const std::exception& e) {
            logComp(std::string("Brotli decompress FAILED: ") + e.what());
            throw;
        }
    }

    if (text.compare(0, GZ_PREFIX.size(), GZ_PREFIX) == 0) {
        logComp("Detected GZ: prefix (GZip+Base64)");
        std::string encoded = text.substr(GZ_PREFIX.size());
        std::vector<uint8_t> decoded = base64Decode(encoded);
        return gzipDecompress(decoded.data(), decoded.size());
    }

    logComp("No compression prefix, returning raw text");
    return text;
}

} // namespace qr
