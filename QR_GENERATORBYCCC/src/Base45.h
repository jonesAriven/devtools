#pragma once
#include <string>
#include <vector>
#include <cstdint>

namespace qr {

// Base45 encode (RFC 9285)
std::string base45Encode(const std::vector<uint8_t>& data);

// Base45 decode (RFC 9285)
std::vector<uint8_t> base45Decode(const std::string& str);

} // namespace qr
