#pragma once
#include "ActivationVerifyResult.h"
#include <string>

// RSA SHA256withRSA PKCS1 signature verification
class ActivationVerifier {
public:
    explicit ActivationVerifier(const std::string& publicKeyPem);
    ~ActivationVerifier();

    ActivationVerifyResult Verify(const std::string& activationCode);
    ActivationVerifyResult Verify(const std::string& activationCode, const std::string& expectedDeviceId);

private:
    std::string m_publicKeyPem;
};
