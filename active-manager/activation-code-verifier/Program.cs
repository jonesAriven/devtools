using System;
using System.Diagnostics;
using System.IO;
using System.Management;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace ActivationCodeVerifier
{
    class Program
    {
        static void Main(string[] args)
        {
            try
            {
                if (args.Length == 0)
                {
                    PrintUsage();
                    return;
                }

                string command = args[0].ToLower();

                switch (command)
                {
                    case "verify":
                        HandleVerify(args);
                        break;
                    case "deviceid":
                        HandleGetDeviceId();
                        break;
                    case "generate":
                        Console.WriteLine("生成功能仅在服务端可用");
                        break;
                    default:
                        PrintUsage();
                        break;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine("{\"success\":false,\"message\":\"验证失败\"}");
                Environment.Exit(1);
            }
        }

        static void PrintUsage()
        {
            Console.WriteLine("激活码验证工具 v1.0");
            Console.WriteLine("用法:");
            Console.WriteLine("  verify <激活码>              - 验证激活码（不含设备绑定）");
            Console.WriteLine("  verify <激活码> <设备ID>    - 验证激活码（带设备绑定）");
            Console.WriteLine("  deviceid                    - 获取当前设备ID");
            Console.WriteLine();
            Console.WriteLine("示例:");
            Console.WriteLine("  ActivationCodeVerifier.exe verify \"payload.signature\"");
            Console.WriteLine("  ActivationCodeVerifier.exe verify \"payload.signature\" \"ABC123\"");
        }

        static void HandleVerify(string[] args)
        {
            if (args.Length < 2)
            {
                Console.WriteLine("{\"success\":false,\"message\":\"请提供激活码\"}");
                Environment.Exit(1);
            }

            string activationCode = args[1];
            string deviceId = args.Length > 2 ? args[2] : null;

            VerifyResult result = ActivationVerifier.Verify(activationCode, deviceId);

            Console.WriteLine(JsonSerializer.Serialize(new
            {
                success = result.Success,
                message = result.Message,
                serialNumber = result.SerialNumber,
                deviceId = result.DeviceId,
                expireTimestamp = result.ExpireTimestamp,
                expired = result.Expired,
                deviceMismatch = result.DeviceMismatch
            }));
        }

        static void HandleGetDeviceId()
        {
            string deviceId = DeviceInfo.GetDeviceId();
            Console.WriteLine(JsonSerializer.Serialize(new
            {
                success = true,
                deviceId = deviceId
            }));
        }
    }

    public class ActivationVerifier
    {
        private static readonly RSA _rsa;
        private const byte ENCRYPT_KEY = 0x5A;

        static ActivationVerifier()
        {
            string publicKeyPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "rsa_keys", "public_key.pem");
            
            if (!File.Exists(publicKeyPath))
            {
                publicKeyPath = Path.Combine(Directory.GetCurrentDirectory(), "rsa_keys", "public_key.pem");
            }

            if (!File.Exists(publicKeyPath))
            {
                throw new FileNotFoundException("未找到公钥文件: " + publicKeyPath);
            }

            string publicKeyPem = File.ReadAllText(publicKeyPath);
            _rsa = RSA.Create();
            _rsa.ImportFromPem(publicKeyPem);
        }

        public static VerifyResult Verify(string activationCode)
        {
            return Verify(activationCode, null);
        }

        public static VerifyResult Verify(string activationCode, string expectedDeviceId)
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

                long currentTimestamp = TimeGuard.GetTrustedTimestamp(serialNumber, expireTimestamp);
                
                if (expireTimestamp < currentTimestamp)
                {
                    return VerifyResult.Fail("激活码已过期", serialNumber, deviceId, expireTimestamp, false);
                }

                TimeGuard.RecordActivation(serialNumber, expireTimestamp);

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

    public static class TimeGuard
    {
        private static readonly Stopwatch _monotonicStopwatch = Stopwatch.StartNew();
        private const string CacheDirectory = "activation_cache";
        private const string CacheFilePrefix = "activation_";

        public static long GetTrustedTimestamp(string serialNumber, long expireTimestamp)
        {
            string cachePath = GetCachePath(serialNumber);
            
            if (!File.Exists(cachePath))
            {
                return DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            }

            try
            {
                string content = File.ReadAllText(cachePath);
                var cache = JsonSerializer.Deserialize<ActivationCache>(content);
                
                if (cache == null || cache.SerialNumber != serialNumber)
                {
                    return DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                }

                long elapsedMs = _monotonicStopwatch.ElapsedMilliseconds;
                
                if (elapsedMs < cache.LastMonotonicMs)
                {
                    return DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                }

                long msSinceLast = elapsedMs - cache.LastMonotonicMs;
                long calculatedTime = cache.LastSystemTime + msSinceLast;

                long systemTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                
                if (Math.Abs(calculatedTime - systemTime) > 86400000)
                {
                    if (calculatedTime > expireTimestamp)
                    {
                        return calculatedTime;
                    }
                }

                return systemTime;
            }
            catch
            {
                return DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            }
        }

        public static void RecordActivation(string serialNumber, long expireTimestamp)
        {
            try
            {
                string cacheDir = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, CacheDirectory);
                if (!Directory.Exists(cacheDir))
                {
                    Directory.CreateDirectory(cacheDir);
                }

                var cache = new ActivationCache
                {
                    SerialNumber = serialNumber,
                    LastSystemTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                    LastMonotonicMs = _monotonicStopwatch.ElapsedMilliseconds,
                    ExpireTimestamp = expireTimestamp
                };

                string json = JsonSerializer.Serialize(cache);
                string cachePath = GetCachePath(serialNumber);
                File.WriteAllText(cachePath, json);
            }
            catch
            {
            }
        }

        private static string GetCachePath(string serialNumber)
        {
            string hash = BitConverter.ToString(SHA256.HashData(Encoding.UTF8.GetBytes(serialNumber))).Replace("-", "");
            string cacheDir = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, CacheDirectory);
            return Path.Combine(cacheDir, CacheFilePrefix + hash + ".dat");
        }

        private class ActivationCache
        {
            public string SerialNumber { get; set; }
            public long LastSystemTime { get; set; }
            public long LastMonotonicMs { get; set; }
            public long ExpireTimestamp { get; set; }
        }
    }

    public static class DeviceInfo
    {
        public static string GetDeviceId()
        {
            if (!RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                return GetFallbackDeviceId();
            }

            string cpuId = GetCPUId();
            string motherboardId = GetMotherboardId();
            string diskId = GetDiskId();

            string combined = cpuId + motherboardId + diskId;
            using (SHA256 sha256 = SHA256.Create())
            {
                byte[] hash = sha256.ComputeHash(Encoding.UTF8.GetBytes(combined));
                return BitConverter.ToString(hash).Replace("-", "").Substring(0, 32);
            }
        }

        private static string GetFallbackDeviceId()
        {
            string machineName = Environment.MachineName;
            string userName = Environment.UserName;
            string processorCount = Environment.ProcessorCount.ToString();
            
            using (SHA256 sha256 = SHA256.Create())
            {
                byte[] hash = sha256.ComputeHash(Encoding.UTF8.GetBytes($"{machineName}{userName}{processorCount}"));
                return BitConverter.ToString(hash).Replace("-", "").Substring(0, 32);
            }
        }

        private static string GetCPUId()
        {
#if WINDOWS
            try
            {
                using (ManagementObjectSearcher searcher = new ManagementObjectSearcher("SELECT ProcessorId FROM Win32_Processor"))
                {
                    foreach (ManagementObject obj in searcher.Get())
                    {
                        return obj["ProcessorId"]?.ToString() ?? "";
                    }
                }
            }
            catch { }
#endif
            return "CPU_UNKNOWN";
        }

        private static string GetMotherboardId()
        {
#if WINDOWS
            try
            {
                using (ManagementObjectSearcher searcher = new ManagementObjectSearcher("SELECT SerialNumber FROM Win32_BaseBoard"))
                {
                    foreach (ManagementObject obj in searcher.Get())
                    {
                        return obj["SerialNumber"]?.ToString() ?? "";
                    }
                }
            }
            catch { }
#endif
            return "BOARD_UNKNOWN";
        }

        private static string GetDiskId()
        {
#if WINDOWS
            try
            {
                using (ManagementObjectSearcher searcher = new ManagementObjectSearcher("SELECT VolumeSerialNumber FROM Win32_LogicalDisk WHERE DriveType=3"))
                {
                    foreach (ManagementObject obj in searcher.Get())
                    {
                        return obj["VolumeSerialNumber"]?.ToString() ?? "";
                    }
                }
            }
            catch { }
#endif
            return "DISK_UNKNOWN";
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
