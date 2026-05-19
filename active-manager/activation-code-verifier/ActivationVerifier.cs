using System;
using System.Security.Cryptography;
using System.Text;

namespace Jones.Activation
{
    public class ActivationVerifier
    {
        private readonly RSA _rsa;

        public ActivationVerifier(string publicKeyPem)
        {
            _rsa = RSA.Create();
            _rsa.ImportFromPem(publicKeyPem);
        }

        public VerifyResult Verify(string activationCode)
        {
            return Verify(activationCode, null);
        }

        public VerifyResult Verify(string activationCode, string expectedDeviceId)
        {
            if (AntiDebug.IsBeingDebugged())
            {
                return VerifyResult.Fail();
            }

            byte[] payloadBytes = null;
            byte[] signatureBytes = null;

            try
            {
                if (string.IsNullOrWhiteSpace(activationCode))
                {
                    return VerifyResult.Fail();
                }

                string[] parts = activationCode.Split('.');
                if (parts.Length != 2)
                {
                    return VerifyResult.Fail();
                }

                payloadBytes = Base64UrlDecode(parts[0]);
                signatureBytes = Base64UrlDecode(parts[1]);

                string payload = Encoding.UTF8.GetString(payloadBytes);
                string[] payloadParts = payload.Split('|');

                string serialNumber;
                string deviceId;
                long expireTimestamp;

                if (payloadParts.Length == 2)
                {
                    serialNumber = payloadParts[0];
                    deviceId = "";
                    expireTimestamp = ParseExpireTime(payloadParts[1]);
                }
                else if (payloadParts.Length == 3)
                {
                    serialNumber = payloadParts[0];
                    deviceId = payloadParts[1];
                    expireTimestamp = ParseExpireTime(payloadParts[2]);
                }
                else
                {
                    return VerifyResult.Fail();
                }

                if (expireTimestamp == -1)
                {
                    return VerifyResult.Fail();
                }

                bool verified = _rsa.VerifyData(
                    payloadBytes,
                    signatureBytes,
                    HashAlgorithmName.SHA256,
                    RSASignaturePadding.Pkcs1
                );

                if (!verified)
                {
                    return VerifyResult.Fail();
                }

                if (!string.IsNullOrWhiteSpace(expectedDeviceId) &&
                    !string.IsNullOrWhiteSpace(deviceId) &&
                    !deviceId.Equals(expectedDeviceId))
                {
                    return VerifyResult.FailDeviceMismatch(serialNumber, deviceId, expireTimestamp);
                }

                long currentTimestamp = TimeGuard.GetTrustedTimestamp(serialNumber, expireTimestamp);

                if (expireTimestamp < currentTimestamp)
                {
                    return VerifyResult.FailExpired(serialNumber, deviceId, expireTimestamp);
                }

                TimeGuard.RecordActivation(serialNumber, expireTimestamp);

                return VerifyResult.Ok(serialNumber, deviceId, expireTimestamp);
            }
            catch
            {
                return VerifyResult.Fail();
            }
            finally
            {
                if (payloadBytes != null)
                {
                    Array.Clear(payloadBytes, 0, payloadBytes.Length);
                }
                if (signatureBytes != null)
                {
                    Array.Clear(signatureBytes, 0, signatureBytes.Length);
                }
            }
        }

        private static long ParseExpireTime(string timeStr)
        {
            if (long.TryParse(timeStr, out long result))
            {
                return result;
            }
            return -1;
        }

        private static byte[] Base64UrlDecode(string input)
        {
            string padded = input;
            int pad = padded.Length % 4;
            if (pad > 0)
            {
                padded += new string('=', 4 - pad);
            }
            padded = padded.Replace('-', '+').Replace('_', '/');
            return Convert.FromBase64String(padded);
        }
    }
}
