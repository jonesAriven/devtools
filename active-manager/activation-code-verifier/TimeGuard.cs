using System;
using System.Diagnostics;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace Jones.Activation
{
    internal static class TimeGuard
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
}
