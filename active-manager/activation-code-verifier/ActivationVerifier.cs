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
            try
            {
                if (string.IsNullOrWhiteSpace(activationCode))
                {
                    return VerifyResult.Fail("激活码不能为空");
                }

                string[] parts = activationCode.Split('.');
                if (parts.Length != 2)
                {
                    return VerifyResult.Fail("激活码格式无效");
                }

                byte[] payloadBytes = Base64UrlDecode(parts[0]);
                byte[] signatureBytes = Base64UrlDecode(parts[1]);

                string payload = Encoding.UTF8.GetString(payloadBytes);
                string[] payloadParts = payload.Split('|');
                if (payloadParts.Length != 2)
                {
                    return VerifyResult.Fail("激活码载荷格式无效");
                }

                string serialNumber = payloadParts[0];
                long expireTimestamp;
                if (!long.TryParse(payloadParts[1], out expireTimestamp))
                {
                    return VerifyResult.Fail("激活码过期时间格式无效");
                }

                bool verified = _rsa.VerifyData(
                    payloadBytes,
                    signatureBytes,
                    HashAlgorithmName.SHA256,
                    RSASignaturePadding.Pkcs1
                );

                if (!verified)
                {
                    return VerifyResult.Fail("激活码签名验证失败");
                }

                bool expired = expireTimestamp < DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                if (expired)
                {
                    return VerifyResult.Fail("激活码已过期", serialNumber, expireTimestamp, true);
                }

                return VerifyResult.Ok(serialNumber, expireTimestamp);
            }
            catch (Exception ex)
            {
                return VerifyResult.Fail("验证激活码异常: " + ex.Message);
            }
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

    public class VerifyResult
    {
        public bool Success { get; }
        public string Message { get; }
        public string SerialNumber { get; }
        public long ExpireTimestamp { get; }
        public bool Expired { get; }

        private VerifyResult(bool success, string message, string serialNumber, long expireTimestamp, bool expired)
        {
            Success = success;
            Message = message;
            SerialNumber = serialNumber;
            ExpireTimestamp = expireTimestamp;
            Expired = expired;
        }

        public static VerifyResult Ok(string serialNumber, long expireTimestamp)
        {
            return new VerifyResult(true, "验证成功", serialNumber, expireTimestamp, false);
        }

        public static VerifyResult Fail(string message, string serialNumber = null, long expireTimestamp = 0, bool expired = false)
        {
            return new VerifyResult(false, message, serialNumber, expireTimestamp, expired);
        }
    }
}
