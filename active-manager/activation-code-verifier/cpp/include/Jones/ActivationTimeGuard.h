#pragma once
#include <string>
#include <cstdint>

// Time tampering detection using monotonic clock + file cache
namespace ActivationTimeGuard {

// Get a trusted timestamp, detecting clock rollback
int64_t GetTrustedTimestamp(const std::string& serialNumber, int64_t expireTimestamp);

// Record activation time for future tampering detection
void RecordActivation(const std::string& serialNumber, int64_t expireTimestamp);

} // namespace ActivationTimeGuard
