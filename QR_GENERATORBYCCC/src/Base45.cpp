#include "Base45.h"
#include <stdexcept>

namespace qr {

static const char* BASE45_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

static int base45CharToValue(char c) {
    for (int i = 0; i < 45; i++) {
        if (BASE45_ALPHABET[i] == c) return i;
    }
    return -1;
}

std::string base45Encode(const std::vector<uint8_t>& data) {
    std::string result;
    size_t i = 0;
    size_t len = data.size();

    while (i + 1 < len) {
        uint32_t n = static_cast<uint32_t>(data[i]) * 256 + static_cast<uint32_t>(data[i + 1]);
        result += BASE45_ALPHABET[n % 45];
        result += BASE45_ALPHABET[(n / 45) % 45];
        result += BASE45_ALPHABET[(n / 45 / 45) % 45];
        i += 2;
    }

    if (i < len) {
        uint32_t n = static_cast<uint32_t>(data[i]);
        result += BASE45_ALPHABET[n % 45];
        result += BASE45_ALPHABET[n / 45];
    }

    return result;
}

std::vector<uint8_t> base45Decode(const std::string& str) {
    std::vector<uint8_t> result;
    size_t i = 0;
    size_t len = str.size();

    while (i + 2 < len) {
        int c1 = base45CharToValue(str[i]);
        int c2 = base45CharToValue(str[i + 1]);
        int c3 = base45CharToValue(str[i + 2]);
        if (c1 < 0 || c2 < 0 || c3 < 0) {
            throw std::invalid_argument("Invalid Base45 character");
        }
        uint32_t n = static_cast<uint32_t>(c1) + static_cast<uint32_t>(c2) * 45 + static_cast<uint32_t>(c3) * 45 * 45;
        if (n > 65535) {
            throw std::invalid_argument("Base45 decode value out of range");
        }
        result.push_back(static_cast<uint8_t>(n / 256));
        result.push_back(static_cast<uint8_t>(n % 256));
        i += 3;
    }

    if (i + 2 == len) {
        int c1 = base45CharToValue(str[i]);
        int c2 = base45CharToValue(str[i + 1]);
        if (c1 < 0 || c2 < 0) {
            throw std::invalid_argument("Invalid Base45 character");
        }
        uint32_t n = static_cast<uint32_t>(c1) + static_cast<uint32_t>(c2) * 45;
        if (n >= 256) {
            throw std::invalid_argument("Base45 decode value out of range for trailing pair");
        }
        result.push_back(static_cast<uint8_t>(n));
        i += 2;
    } else if (i < len) {
        throw std::invalid_argument("Invalid Base45 string length");
    }

    return result;
}

} // namespace qr
