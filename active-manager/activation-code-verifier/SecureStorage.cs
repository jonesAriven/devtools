using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace Jones.Activation
{
    internal static class SecureStorage
    {
        private static readonly byte[] _salt = new byte[] {
            0x4A, 0x6F, 0x6E, 0x65, 0x73, 0x41, 0x63, 0x74,
            0x69, 0x76, 0x61, 0x74, 0x69, 0x6F, 0x6E, 0x4B
        };

        private static readonly byte[] _iv = new byte[] {
            0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38,
            0x39, 0x30, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46
        };

        public static void Save(string filePath, string activationCode)
        {
            try
            {
                byte[] key = DeriveKey(DeviceInfo.GetDeviceId());
                byte[] plainBytes = Encoding.UTF8.GetBytes(activationCode);

                using var aes = Aes.Create();
                aes.Key = key;
                aes.IV = _iv;
                aes.Mode = CipherMode.CBC;
                aes.Padding = PaddingMode.PKCS7;

                using var encryptor = aes.CreateEncryptor();
                byte[] encrypted = encryptor.TransformFinalBlock(plainBytes, 0, plainBytes.Length);

                byte[] result = new byte[_salt.Length + encrypted.Length];
                Buffer.BlockCopy(_salt, 0, result, 0, _salt.Length);
                Buffer.BlockCopy(encrypted, 0, result, _salt.Length, encrypted.Length);

                File.WriteAllBytes(filePath, result);

                Array.Clear(key, 0, key.Length);
                Array.Clear(plainBytes, 0, plainBytes.Length);
                Array.Clear(encrypted, 0, encrypted.Length);
            }
            catch
            {
            }
        }

        public static string Load(string filePath)
        {
            try
            {
                if (!File.Exists(filePath))
                    return null;

                byte[] fileData = File.ReadAllBytes(filePath);

                if (fileData.Length <= _salt.Length)
                    return null;

                byte[] key = DeriveKey(DeviceInfo.GetDeviceId());

                byte[] encrypted = new byte[fileData.Length - _salt.Length];
                Buffer.BlockCopy(fileData, _salt.Length, encrypted, 0, encrypted.Length);

                using var aes = Aes.Create();
                aes.Key = key;
                aes.IV = _iv;
                aes.Mode = CipherMode.CBC;
                aes.Padding = PaddingMode.PKCS7;

                using var decryptor = aes.CreateDecryptor();
                byte[] decrypted = decryptor.TransformFinalBlock(encrypted, 0, encrypted.Length);

                string result = Encoding.UTF8.GetString(decrypted);

                Array.Clear(key, 0, key.Length);
                Array.Clear(decrypted, 0, decrypted.Length);

                return result;
            }
            catch
            {
                return null;
            }
        }

        public static void Delete(string filePath)
        {
            try
            {
                if (File.Exists(filePath))
                {
                    byte[] data = File.ReadAllBytes(filePath);
                    for (int i = 0; i < data.Length; i++)
                    {
                        data[i] = 0;
                    }
                    File.WriteAllBytes(filePath, data);
                    File.Delete(filePath);
                }
            }
            catch
            {
            }
        }

        private static byte[] DeriveKey(string password)
        {
            using var deriveBytes = new Rfc2898DeriveBytes(password, _salt, 10000, HashAlgorithmName.SHA256);
            return deriveBytes.GetBytes(32);
        }
    }
}
