using System;
using System.Management;
using System.Net;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;

namespace Jones.Activation
{
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
            string macAddress = GetMacAddress();

            string combined = cpuId + motherboardId + diskId + macAddress;
            using (SHA256 sha256 = SHA256.Create())
            {
                byte[] hash = sha256.ComputeHash(Encoding.UTF8.GetBytes(combined));
                return BitConverter.ToString(hash).Replace("-", "").Substring(0, 32);
            }
        }

        public static string GetMacAddress()
        {
            try
            {
                NetworkInterface[] interfaces = NetworkInterface.GetAllNetworkInterfaces();
                foreach (NetworkInterface ni in interfaces)
                {
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet ||
                        ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211)
                    {
                        if (ni.OperationalStatus == OperationalStatus.Up)
                        {
                            PhysicalAddress pa = ni.GetPhysicalAddress();
                            if (pa != null && pa.ToString().Length > 0)
                            {
                                return pa.ToString();
                            }
                        }
                    }
                }
            }
            catch { }
            return GetMacAddressWmi();
        }

        private static string GetMacAddressWmi()
        {
#if WINDOWS
            try
            {
                using (ManagementObjectSearcher searcher = new ManagementObjectSearcher("SELECT MACAddress FROM Win32_NetworkAdapter WHERE NetConnectionStatus=2"))
                {
                    foreach (ManagementObject obj in searcher.Get())
                    {
                        string mac = obj["MACAddress"]?.ToString();
                        if (!string.IsNullOrEmpty(mac))
                        {
                            return mac.Replace(":", "");
                        }
                    }
                }
            }
            catch { }
#endif
            return "MAC_UNKNOWN";
        }

        public static string GetMacAddressFormatted()
        {
            string mac = GetMacAddress();
            if (string.IsNullOrEmpty(mac) || mac == "MAC_UNKNOWN")
            {
                return mac;
            }
            if (mac.Contains(":") || mac.Contains("-"))
            {
                return mac.Replace(":", "-").Replace("-", "-").ToUpper();
            }
            if (mac.Length >= 12)
            {
                return string.Format("{0}-{1}-{2}-{3}-{4}-{5}",
                    mac.Substring(0, 2).ToUpper(),
                    mac.Substring(2, 2).ToUpper(),
                    mac.Substring(4, 2).ToUpper(),
                    mac.Substring(6, 2).ToUpper(),
                    mac.Substring(8, 2).ToUpper(),
                    mac.Substring(10, 2).ToUpper());
            }
            return mac.ToUpper();
        }

        public static string GetMachineCode()
        {
            return GetMacAddressFormatted();
        }

        private const byte SERIAL_XOR_KEY = 0x5A;

        public static string GetSerialNumber(string initialSerial)
        {
            return GetSerialNumber(initialSerial, null);
        }

        public static string GetSerialNumber(string initialSerial, string version)
        {
            string deviceId = GetDeviceId();
            string machineCode = GetMachineCode();

            // 格式: initialSerial|deviceId|machineCode|version
            // version 可能为空（兼容老版本客户端），服务端据此判断是否传了版本号
            string plainText = string.IsNullOrEmpty(version)
                ? initialSerial + "|" + deviceId + "|" + machineCode
                : initialSerial + "|" + deviceId + "|" + machineCode + "|" + version;
            byte[] plainBytes = Encoding.UTF8.GetBytes(plainText);

            byte[] encrypted = new byte[plainBytes.Length];
            for (int i = 0; i < plainBytes.Length; i++)
            {
                encrypted[i] = (byte)(plainBytes[i] ^ SERIAL_XOR_KEY);
            }

            return Convert.ToBase64String(encrypted);
        }

        public static SerialNumberInfo ParseSerialNumber(string encryptedSerialNumber)
        {
            try
            {
                byte[] encrypted = Convert.FromBase64String(encryptedSerialNumber);
                byte[] decrypted = new byte[encrypted.Length];
                for (int i = 0; i < encrypted.Length; i++)
                {
                    decrypted[i] = (byte)(encrypted[i] ^ SERIAL_XOR_KEY);
                }

                string plainText = Encoding.UTF8.GetString(decrypted);
                string[] parts = plainText.Split('|');

                if (parts.Length >= 3)
                {
                    return new SerialNumberInfo
                    {
                        InitialSerial = parts[0],
                        DeviceId = parts[1],
                        MachineCode = parts[2],
                        Version = parts.Length >= 4 ? parts[3] : null
                    };
                }
            }
            catch { }

            return null;
        }

        public class SerialNumberInfo
        {
            public string InitialSerial { get; set; }
            public string DeviceId { get; set; }
            public string MachineCode { get; set; }
            public string Version { get; set; }
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
}
