#pragma once
#include <string>
#include <cstdint>

struct ActivationVerifyResult {
    bool success = false;
    std::string serialNumber;
    std::string deviceId;
    int64_t expireTimestamp = 0;
    bool expired = false;
    bool deviceMismatch = false;

    static ActivationVerifyResult Ok(const std::string& sn, const std::string& did, int64_t exp) {
        return { true, sn, did, exp, false, false };
    }
    static ActivationVerifyResult Fail() {
        return { false, "", "", 0, false, false };
    }
    static ActivationVerifyResult FailExpired(const std::string& sn, const std::string& did, int64_t exp) {
        return { false, sn, did, exp, true, false };
    }
    static ActivationVerifyResult FailDeviceMismatch(const std::string& sn, const std::string& did, int64_t exp) {
        return { false, sn, did, exp, false, true };
    }
};
