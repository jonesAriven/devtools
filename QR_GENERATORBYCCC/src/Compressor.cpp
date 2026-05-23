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
        decoded_size = len * 10;
    }

    std::string output(decoded_size, '\0');
    size_t actual_size = decoded_size;

    BrotliDecoderResult result = BrotliDecoderDecompress(
        len, data, &actual_size, reinterpret_cast<uint8_t*>(output.data()));

    if (result != BROTLI_DECODER_RESULT_SUCCESS) {
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

    std::vector<uint8_t> compressed = brotliCompress(text);
    std::string encoded = base45Encode(compressed);
    return "B5:" + encoded;
}

std::string decompressText(const std::string& text) {
    const std::string B5_PREFIX = "B5:";
    const std::string GZ_PREFIX = "GZ:";
    const std::string M5_PREFIX = "M5:";

    logComp("decompressText: input_len=" + std::to_string(text.size()));

    // Multi-page: single page of a multi-page QR set
    if (text.compare(0, M5_PREFIX.size(), M5_PREFIX) == 0) {
        logComp("Detected M5: prefix (multi-page Brotli+Base45)");
        int page = 0, total = 0;
        std::string chunk;
        if (MultiPageAssembler::parseM5Header(text, page, total, chunk)) {
            logComp("M5 page " + std::to_string(page) + "/" + std::to_string(total) + ", chunk_len=" + std::to_string(chunk.size()));
            // For single-page case (total=1), decompress directly
            if (total == 1) {
                std::vector<uint8_t> decoded = base45Decode(chunk);
                return brotliDecompress(decoded.data(), decoded.size());
            }
            // Multi-page: return info message, caller should use MultiPageAssembler
            throw std::runtime_error("Multi-page QR detected (" + std::to_string(page) + "/" + std::to_string(total) + "), please scan all pages");
        }
        throw std::runtime_error("Invalid M5: format");
    }

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

// === MultiPageAssembler ===

bool MultiPageAssembler::parseM5Header(const std::string& text, int& outPage, int& outTotal, std::string& outChunk) {
    const std::string M5_PREFIX = "M5:";
    if (text.size() < M5_PREFIX.size() + 4) return false;  // minimum: "M5:1/1/X"
    if (text.compare(0, M5_PREFIX.size(), M5_PREFIX) != 0) return false;

    std::string rest = text.substr(M5_PREFIX.size());

    // Find first '/' (page/total separator)
    size_t slash1 = rest.find('/');
    if (slash1 == std::string::npos) return false;

    // Find second '/' (total/chunk separator)
    size_t slash2 = rest.find('/', slash1 + 1);
    if (slash2 == std::string::npos) return false;

    std::string pageStr = rest.substr(0, slash1);
    std::string totalStr = rest.substr(slash1 + 1, slash2 - slash1 - 1);

    try {
        outPage = std::stoi(pageStr);
        outTotal = std::stoi(totalStr);
    } catch (...) {
        return false;
    }

    if (outPage < 1 || outTotal < 1 || outPage > outTotal) return false;

    outChunk = rest.substr(slash2 + 1);
    return true;
}

bool MultiPageAssembler::addPage(const std::string& text) {
    int page = 0, total = 0;
    std::string chunk;
    if (!parseM5Header(text, page, total, chunk)) return false;

    isMultiPage = true;
    if (totalPages == 0) {
        totalPages = total;
    } else if (totalPages != total) {
        // Page from a different set, reject
        return false;
    }

    int pageIndex = page - 1;  // convert to 0-based
    if (pages.find(pageIndex) != pages.end()) {
        return false;  // already have this page
    }

    pages[pageIndex] = chunk;
    return true;
}

bool MultiPageAssembler::isComplete() const {
    if (!isMultiPage || totalPages == 0) return false;
    return static_cast<int>(pages.size()) == totalPages;
}

std::string MultiPageAssembler::assemble() const {
    if (!isComplete()) return "";

    // Concatenate all chunks in order
    std::string base45Data;
    for (int i = 0; i < totalPages; i++) {
        auto it = pages.find(i);
        if (it == pages.end()) return "";
        base45Data += it->second;
    }

    // Base45 decode -> Brotli decompress
    try {
        std::vector<uint8_t> decoded = base45Decode(base45Data);
        return brotliDecompress(decoded.data(), decoded.size());
    } catch (...) {
        return "";
    }
}

void MultiPageAssembler::reset() {
    totalPages = 0;
    pages.clear();
    isMultiPage = false;
}

std::vector<int> MultiPageAssembler::getMissingPages() const {
    std::vector<int> missing;
    if (!isMultiPage || totalPages == 0) return missing;
    for (int i = 0; i < totalPages; i++) {
        if (pages.find(i) == pages.end()) {
            missing.push_back(i + 1);  // return 1-based page numbers
        }
    }
    return missing;
}

} // namespace qr
