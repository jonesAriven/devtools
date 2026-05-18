using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;

namespace Jones.Activation
{
    public class ActivationVerifier
    {
        private readonly RSA _rsa;
        private const byte ENCRYPT_KEY = 0x5A;

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
                return VerifyResult.Fail("验证失败");
            }

            byte[] payloadBytes = null;
            byte[] signatureBytes = null;

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
                    return VerifyResult.Fail("激活码载荷格式无效");
                }

                if (expireTimestamp == -1)
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

                if (!string.IsNullOrWhiteSpace(expectedDeviceId) &&
                    !string.IsNullOrWhiteSpace(deviceId) &&
                    !deviceId.Equals(expectedDeviceId))
                {
                    return VerifyResult.Fail("设备不匹配", serialNumber, deviceId, expireTimestamp, true);
                }

                bool expired = expireTimestamp < DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                if (expired)
                {
                    return VerifyResult.Fail("激活码已过期", serialNumber, deviceId, expireTimestamp, false);
                }

                return VerifyResult.Ok(serialNumber, deviceId, expireTimestamp);
            }
            catch (Exception)
            {
                return VerifyResult.Fail("验证失败");
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

    public static class AntiDebug
    {
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CheckRemoteDebuggerPresent(IntPtr hProcess, ref bool isDebuggerPresent);

        [DllImport("kernel32.dll")]
        private static extern uint GetTickCount();

        public static bool IsBeingDebugged()
        {
            if (Debugger.IsAttached)
                return true;

            bool isDebuggerPresent = false;
            CheckRemoteDebuggerPresent(Process.GetCurrentProcess().Handle, ref isDebuggerPresent);
            if (isDebuggerPresent)
                return true;

            uint start = GetTickCount();
            for (int i = 0; i < 100000000; i++) { }
            uint end = GetTickCount();

            if (end - start < 10)
                return true;

            return false;
        }
    }

    public class VerifyResult
    {
        public bool Success { get; }
        public string Message { get; }
        public string SerialNumber { get; }
        public string DeviceId { get; }
        public long ExpireTimestamp { get; }
        public bool Expired { get; }
        public bool DeviceMismatch { get; }

        private VerifyResult(bool success, string message, string serialNumber, 
                            string deviceId, long expireTimestamp, bool expired, bool deviceMismatch)
        {
            Success = success;
            Message = message;
            SerialNumber = serialNumber;
            DeviceId = deviceId;
            ExpireTimestamp = expireTimestamp;
            Expired = expired;
            DeviceMismatch = deviceMismatch;
        }

        public static VerifyResult Ok(string serialNumber, string deviceId, long expireTimestamp)
        {
            return new VerifyResult(true, "验证成功", serialNumber, deviceId, expireTimestamp, false, false);
        }

        public static VerifyResult Fail(string message)
        {
            return new VerifyResult(false, message, null, null, 0, false, false);
        }

        public static VerifyResult Fail(string message, string serialNumber, string deviceId, 
                                       long expireTimestamp, bool deviceMismatch)
        {
            return new VerifyResult(false, message, serialNumber, deviceId, expireTimestamp, !deviceMismatch, deviceMismatch);
        }
    }
}