using System;
using System.Text;
using System.Threading;

namespace Jones.Activation
{
    public static class ActivationGuard
    {
        private static readonly object _lock = new object();
        private static ActivationVerifier _verifier;
        private static Timer _periodicTimer;
        private static string _lastActivationCode;
        private static string _lastDeviceId;
        private static int _checkIntervalMs = 60000;
        private static Action<string> _onExpiredCallback;

        private static readonly byte[] _encryptedKey = new byte[] {
            0x77, 0x77, 0x77, 0x77, 0x77, 0x18, 0x1F, 0x1D, 0x13, 0x14,
            0x7A, 0x0A, 0x0F, 0x18, 0x16, 0x13, 0x19, 0x7A, 0x11, 0x1F,
            0x03, 0x77, 0x77, 0x77, 0x77, 0x77, 0x50, 0x17, 0x13, 0x13,
            0x18, 0x13, 0x30, 0x1B, 0x14, 0x18, 0x3D, 0x31, 0x2B, 0x32,
            0x31, 0x33, 0x1D, 0x63, 0x2D, 0x6A, 0x18, 0x1B, 0x0B, 0x1F,
            0x1C, 0x1B, 0x1B, 0x15, 0x19, 0x1B, 0x0B, 0x62, 0x1B, 0x17,
            0x13, 0x13, 0x18, 0x19, 0x3D, 0x11, 0x19, 0x1B, 0x0B, 0x1F,
            0x1B, 0x2C, 0x6F, 0x3E, 0x12, 0x15, 0x34, 0x6D, 0x3D, 0x32,
            0x18, 0x1D, 0x2A, 0x2F, 0x1B, 0x35, 0x03, 0x71, 0x63, 0x08,
            0x22, 0x50, 0x18, 0x29, 0x2C, 0x03, 0x13, 0x68, 0x1B, 0x0F,
            0x34, 0x6C, 0x0E, 0x28, 0x12, 0x6C, 0x18, 0x28, 0x63, 0x3D,
            0x3B, 0x22, 0x2C, 0x68, 0x16, 0x29, 0x1D, 0x00, 0x31, 0x28,
            0x2D, 0x0F, 0x1D, 0x10, 0x0C, 0x0B, 0x00, 0x62, 0x29, 0x00,
            0x22, 0x35, 0x33, 0x32, 0x17, 0x68, 0x39, 0x03, 0x2D, 0x6D,
            0x0C, 0x09, 0x2A, 0x0A, 0x02, 0x0F, 0x3F, 0x31, 0x00, 0x29,
            0x28, 0x0F, 0x20, 0x11, 0x18, 0x3F, 0x50, 0x68, 0x3B, 0x22,
            0x34, 0x18, 0x2C, 0x6D, 0x71, 0x12, 0x6F, 0x08, 0x03, 0x02,
            0x03, 0x3E, 0x08, 0x1E, 0x0A, 0x1D, 0x2C, 0x2E, 0x17, 0x0B,
            0x11, 0x19, 0x3E, 0x00, 0x0B, 0x6A, 0x0D, 0x68, 0x08, 0x12,
            0x37, 0x32, 0x75, 0x0F, 0x0F, 0x18, 0x2D, 0x0F, 0x09, 0x13,
            0x3F, 0x30, 0x2A, 0x03, 0x2F, 0x35, 0x36, 0x11, 0x3D, 0x1E,
            0x35, 0x3C, 0x17, 0x3B, 0x6A, 0x17, 0x11, 0x3E, 0x08, 0x08,
            0x15, 0x50, 0x6E, 0x71, 0x11, 0x6D, 0x2D, 0x1B, 0x30, 0x6B,
            0x1D, 0x1D, 0x23, 0x30, 0x16, 0x0F, 0x16, 0x69, 0x1D, 0x20,
            0x39, 0x17, 0x3D, 0x11, 0x2D, 0x31, 0x68, 0x75, 0x38, 0x28,
            0x3D, 0x28, 0x34, 0x00, 0x0B, 0x23, 0x3F, 0x10, 0x2A, 0x6E,
            0x32, 0x1B, 0x38, 0x31, 0x16, 0x6F, 0x32, 0x02, 0x2D, 0x3F,
            0x6D, 0x16, 0x63, 0x37, 0x08, 0x3E, 0x68, 0x29, 0x23, 0x12,
            0x62, 0x68, 0x38, 0x0A, 0x1E, 0x14, 0x50, 0x15, 0x62, 0x71,
            0x1D, 0x22, 0x6F, 0x17, 0x1E, 0x02, 0x6E, 0x38, 0x12, 0x22,
            0x30, 0x32, 0x2C, 0x15, 0x2F, 0x22, 0x31, 0x19, 0x3E, 0x22,
            0x6A, 0x19, 0x23, 0x6F, 0x39, 0x20, 0x6D, 0x1F, 0x3F, 0x3F,
            0x6E, 0x1B, 0x00, 0x69, 0x3C, 0x2C, 0x1B, 0x0A, 0x68, 0x28,
            0x6D, 0x71, 0x29, 0x35, 0x3B, 0x69, 0x19, 0x02, 0x09, 0x23,
            0x0D, 0x75, 0x6F, 0x2E, 0x2A, 0x37, 0x20, 0x0C, 0x2F, 0x62,
            0x0C, 0x50, 0x31, 0x0F, 0x2A, 0x28, 0x69, 0x69, 0x63, 0x31,
            0x2A, 0x18, 0x6D, 0x71, 0x6E, 0x1B, 0x3C, 0x75, 0x6A, 0x6D,
            0x38, 0x0C, 0x37, 0x00, 0x2E, 0x10, 0x6B, 0x71, 0x15, 0x29,
            0x2E, 0x36, 0x2A, 0x1E, 0x37, 0x22, 0x0C, 0x0D, 0x1D, 0x1F,
            0x2D, 0x2F, 0x3D, 0x09, 0x00, 0x35, 0x35, 0x33, 0x19, 0x2B,
            0x33, 0x0D, 0x1B, 0x35, 0x20, 0x10, 0x17, 0x6A, 0x17, 0x39,
            0x12, 0x2B, 0x6A, 0x3E, 0x28, 0x6C, 0x50, 0x39, 0x0B, 0x13,
            0x1E, 0x1B, 0x0B, 0x1B, 0x18, 0x50, 0x77, 0x77, 0x77, 0x77,
            0x77, 0x1F, 0x14, 0x1E, 0x7A, 0x0A, 0x0F, 0x18, 0x16, 0x13,
            0x19, 0x7A, 0x11, 0x1F, 0x03, 0x77, 0x77, 0x77, 0x77, 0x77,
            0x50
        };

        private const byte XOR_KEY = 0x5A;

        public static void Protect(string activationCode)
        {
            Protect(activationCode, null);
        }

        public static void Protect(string activationCode, string deviceId)
        {
            lock (_lock)
            {
                if (_verifier == null)
                {
                    _verifier = CreateVerifier();
                }
            }

            VerifyResult result = _verifier.Verify(activationCode, deviceId);

            if (!result.Success)
            {
                ShowErrorAndExit(result.Message);
            }

            if (result.Expired)
            {
                ShowErrorAndExit("激活码已过期，请联系管理员续期");
            }

            if (result.DeviceMismatch)
            {
                ShowErrorAndExit("设备不匹配，当前设备无权使用此激活码");
            }
        }

        public static void ProtectWithAutoDevice(string activationCode)
        {
            string deviceId = DeviceInfo.GetDeviceId();
            Protect(activationCode, deviceId);
        }

        public static VerifyResult Check(string activationCode)
        {
            return Check(activationCode, null);
        }

        public static VerifyResult Check(string activationCode, string deviceId)
        {
            lock (_lock)
            {
                if (_verifier == null)
                {
                    _verifier = CreateVerifier();
                }
            }

            return _verifier.Verify(activationCode, deviceId);
        }

        public static VerifyResult CheckWithAutoDevice(string activationCode)
        {
            string deviceId = DeviceInfo.GetDeviceId();
            return Check(activationCode, deviceId);
        }

        public static void StartPeriodicCheck(string activationCode, string deviceId = null, int checkIntervalMs = 60000, Action<string> onExpired = null)
        {
            _lastActivationCode = activationCode;
            _lastDeviceId = deviceId;
            _checkIntervalMs = checkIntervalMs > 0 ? checkIntervalMs : 60000;
            _onExpiredCallback = onExpired;

            StopPeriodicCheck();

            _periodicTimer = new Timer(PeriodicCheckCallback, null, _checkIntervalMs, _checkIntervalMs);
        }

        public static void StartPeriodicCheckWithAutoDevice(string activationCode, int checkIntervalMs = 60000, Action<string> onExpired = null)
        {
            string deviceId = DeviceInfo.GetDeviceId();
            StartPeriodicCheck(activationCode, deviceId, checkIntervalMs, onExpired);
        }

        public static void StopPeriodicCheck()
        {
            if (_periodicTimer != null)
            {
                _periodicTimer.Dispose();
                _periodicTimer = null;
            }
        }

        private static void PeriodicCheckCallback(object state)
        {
            if (string.IsNullOrWhiteSpace(_lastActivationCode))
            {
                return;
            }

            try
            {
                VerifyResult result = Check(_lastActivationCode, _lastDeviceId);

                if (!result.Success || result.Expired || result.DeviceMismatch)
                {
                    StopPeriodicCheck();

                    string message = result.DeviceMismatch
                        ? "设备不匹配，当前设备无权使用此激活码"
                        : result.Expired
                            ? "激活码已过期，请联系管理员续期"
                            : result.Message ?? "激活码验证失败";

                    if (_onExpiredCallback != null)
                    {
                        _onExpiredCallback(message);
                    }
                    else
                    {
                        ShowErrorAndExit(message);
                    }
                }
            }
            catch
            {
            }
        }

        private static ActivationVerifier CreateVerifier()
        {
            string publicKeyPem = DecryptPublicKey();
            ActivationVerifier verifier = new ActivationVerifier(publicKeyPem);
            ClearEncryptedKey();
            return verifier;
        }

        private static string DecryptPublicKey()
        {
            byte[] decrypted = new byte[_encryptedKey.Length];
            for (int i = 0; i < _encryptedKey.Length; i++)
            {
                decrypted[i] = (byte)(_encryptedKey[i] ^ XOR_KEY);
            }
            string key = Encoding.ASCII.GetString(decrypted);
            Array.Clear(decrypted, 0, decrypted.Length);
            return key;
        }

        private static void ClearEncryptedKey()
        {
            for (int i = 0; i < _encryptedKey.Length; i++)
            {
                _encryptedKey[i] = 0;
            }
        }

        private static void ShowErrorAndExit(string message)
        {
            Console.ForegroundColor = ConsoleColor.Red;
            Console.WriteLine();
            Console.WriteLine("========================================");
            Console.WriteLine("  授权验证失败");
            Console.WriteLine("========================================");
            Console.WriteLine("  " + message);
            Console.WriteLine("  程序即将退出...");
            Console.WriteLine("========================================");
            Console.ResetColor();
            Console.WriteLine();

            Thread.Sleep(3000);
            Environment.Exit(1001);
        }
    }
}
