using System;
using System.Drawing;
using System.IO;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace Jones.Activation
{
    public static class ActivationGuard
    {
        private static readonly object _lock = new object();
        private static ActivationVerifier _verifier;
        private static System.Threading.Timer _periodicTimer;
        private static string _lastActivationCode;
        private static string _lastDeviceId;
        private static int _checkIntervalMs = 60000;
        private static Action<string> _onExpiredCallback;

        private static readonly byte[] _encryptedKey = new byte[] {
            0x17, 0x51, 0xC8, 0xBC, 0x17, 0x3E, 0xA0, 0xD6, 0x73, 0x32,
            0xC5, 0xC1, 0x6F, 0x3E, 0xA9, 0xD8, 0x79, 0x5C, 0xAE, 0xD4,
            0x63, 0x51, 0xC8, 0xBC, 0x17, 0x51, 0xEF, 0xDC, 0x73, 0x35,
            0xA7, 0xD8, 0x50, 0x3D, 0xAB, 0xD3, 0x5D, 0x17, 0x94, 0xF9,
            0x51, 0x15, 0xA2, 0xA8, 0x4D, 0x4C, 0xA7, 0xD0, 0x6B, 0x39,
            0xA3, 0xD0, 0x7B, 0x33, 0xA6, 0xD0, 0x6B, 0x44, 0xA4, 0xDC,
            0x73, 0x35, 0xA7, 0xD2, 0x5D, 0x37, 0xA6, 0xD0, 0x6B, 0x39,
            0xA4, 0xE7, 0x0F, 0x18, 0xAD, 0xDE, 0x54, 0x4B, 0x82, 0xF9,
            0x78, 0x3B, 0x95, 0xE4, 0x7B, 0x13, 0xBC, 0xBA, 0x03, 0x2E,
            0x9D, 0x9B, 0x78, 0x0F, 0x93, 0xC8, 0x73, 0x4E, 0xA4, 0xC4,
            0x54, 0x4A, 0xB1, 0xE3, 0x72, 0x4A, 0xA7, 0xE3, 0x03, 0x1B,
            0x84, 0xE9, 0x4C, 0x4E, 0xA9, 0xE2, 0x7D, 0x26, 0x8E, 0xE3,
            0x4D, 0x29, 0xA2, 0xDB, 0x6C, 0x2D, 0xBF, 0xA9, 0x49, 0x26,
            0x9D, 0xFE, 0x53, 0x14, 0xA8, 0xA3, 0x59, 0x25, 0x92, 0xA6,
            0x6C, 0x2F, 0x95, 0xC1, 0x62, 0x29, 0x80, 0xFA, 0x60, 0x0F,
            0x97, 0xC4, 0x40, 0x37, 0xA7, 0xF4, 0x30, 0x4E, 0x84, 0xE9,
            0x54, 0x3E, 0x93, 0xA6, 0x11, 0x34, 0xD0, 0xC3, 0x63, 0x24,
            0xBC, 0xF5, 0x68, 0x38, 0xB5, 0xD6, 0x4C, 0x08, 0xA8, 0xC0,
            0x71, 0x3F, 0x81, 0xCB, 0x6B, 0x4C, 0xB2, 0xA3, 0x68, 0x34,
            0x88, 0xF9, 0x15, 0x29, 0xB0, 0xD3, 0x4D, 0x29, 0xB6, 0xD8,
            0x5F, 0x16, 0x95, 0xC8, 0x4F, 0x13, 0x89, 0xDA, 0x5D, 0x38,
            0x8A, 0xF7, 0x77, 0x1D, 0xD5, 0xDC, 0x71, 0x18, 0xB7, 0xC3,
            0x75, 0x76, 0xD1, 0xBA, 0x71, 0x4B, 0x92, 0xD0, 0x50, 0x4D,
            0xA2, 0xD6, 0x43, 0x16, 0xA9, 0xC4, 0x76, 0x4F, 0xA2, 0xEB,
            0x59, 0x31, 0x82, 0xDA, 0x4D, 0x17, 0xD7, 0xBE, 0x58, 0x0E,
            0x82, 0xE3, 0x54, 0x26, 0xB4, 0xE8, 0x5F, 0x36, 0x95, 0xA5,
            0x52, 0x3D, 0x87, 0xFA, 0x76, 0x49, 0x8D, 0xC9, 0x4D, 0x19,
            0xD2, 0xDD, 0x03, 0x11, 0xB7, 0xF5, 0x08, 0x0F, 0x9C, 0xD9,
            0x02, 0x4E, 0x87, 0xC1, 0x7E, 0x32, 0xEF, 0xDE, 0x02, 0x57,
            0xA2, 0xE9, 0x0F, 0x31, 0xA1, 0xC9, 0x0E, 0x1E, 0xAD, 0xE9,
            0x50, 0x14, 0x93, 0xDE, 0x4F, 0x04, 0x8E, 0xD2, 0x5E, 0x04,
            0xD5, 0xD2, 0x43, 0x49, 0x86, 0xEB, 0x0D, 0x39, 0x80, 0xF4,
            0x0E, 0x3D, 0xBF, 0xA2, 0x5C, 0x0A, 0xA4, 0xC1, 0x08, 0x0E,
            0xD2, 0xBA, 0x49, 0x13, 0x84, 0xA2, 0x79, 0x24, 0xB6, 0xE8,
            0x6D, 0x53, 0xD0, 0xE5, 0x4A, 0x11, 0x9F, 0xC7, 0x4F, 0x44,
            0xB3, 0x9B, 0x51, 0x29, 0x95, 0xE3, 0x09, 0x4F, 0xDC, 0xFA,
            0x4A, 0x3E, 0xD2, 0xBA, 0x0E, 0x3D, 0x83, 0xBE, 0x0A, 0x4B,
            0x87, 0xC7, 0x57, 0x26, 0x91, 0xDB, 0x0B, 0x57, 0xAA, 0xE2,
            0x4E, 0x10, 0x95, 0xD5, 0x57, 0x04, 0xB3, 0xC6, 0x7D, 0x39,
            0x92, 0xE4, 0x5D, 0x2F, 0xBF, 0xFE, 0x55, 0x15, 0xA6, 0xE0,
            0x53, 0x2B, 0xA4, 0xFE, 0x40, 0x36, 0xA8, 0xA1, 0x77, 0x1F,
            0xAD, 0xE0, 0x0A, 0x18, 0x97, 0xA7, 0x30, 0x1F, 0xB4, 0xD8,
            0x7E, 0x3D, 0xB4, 0xD0, 0x78, 0x76, 0xC8, 0xBC, 0x17, 0x51,
            0xC8, 0xD4, 0x74, 0x38, 0xC5, 0xC1, 0x6F, 0x3E, 0xA9, 0xD8,
            0x79, 0x5C, 0xAE, 0xD4, 0x63, 0x51, 0xC8, 0xBC, 0x17, 0x51
        };

        private static readonly byte[] _xorKeys = new byte[] { 0x3A, 0x7C, 0xE5, 0x91 };

        public static bool LaunchWithProtection(string initialSerial, int checkIntervalMs = 60000)
        {
            string licPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "activation.dat");

            string savedCode = SecureStorage.Load(licPath);

            if (!string.IsNullOrWhiteSpace(savedCode))
            {
                VerifyResult result = CheckWithAutoDevice(savedCode);

                if (result.Success && !result.Expired && !result.DeviceMismatch)
                {
                    StartPeriodicCheckWithAutoDevice(savedCode, checkIntervalMs, (msg) =>
                    {
                        SecureStorage.Delete(licPath);
                        ShowExpiredDialog(msg);
                    });
                    return true;
                }

                if (result.Expired || result.DeviceMismatch)
                {
                    SecureStorage.Delete(licPath);
                }
            }

            string activationCode = ShowActivationDialog(initialSerial, licPath);

            if (activationCode == null)
            {
                return false;
            }

            StartPeriodicCheckWithAutoDevice(activationCode, checkIntervalMs, (msg) =>
            {
                SecureStorage.Delete(licPath);
                ShowExpiredDialog(msg);
            });

            return true;
        }

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

            if (!result.Success || result.Expired || result.DeviceMismatch)
            {
                Environment.Exit(1001);
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

            _periodicTimer = new System.Threading.Timer(PeriodicCheckCallback, null, _checkIntervalMs, _checkIntervalMs);
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

                    if (_onExpiredCallback != null)
                    {
                        _onExpiredCallback(null);
                    }
                    else
                    {
                        Environment.Exit(1002);
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
                decrypted[i] = (byte)(_encryptedKey[i] ^ _xorKeys[i % _xorKeys.Length]);
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

        private static string ShowActivationDialog(string initialSerial, string licPath)
        {
            string serialNumber = DeviceInfo.GetSerialNumber(initialSerial);

            while (true)
            {
                using var form = new Form();
                form.Text = "软件激活";
                form.Size = new Size(500, 360);
                form.StartPosition = FormStartPosition.CenterScreen;
                form.FormBorderStyle = FormBorderStyle.FixedDialog;
                form.MaximizeBox = false;
                form.MinimizeBox = false;
                form.Font = new Font("微软雅黑", 9);

                var lblTitle = new Label
                {
                    Text = "请输入激活码",
                    Location = new Point(20, 12),
                    Size = new Size(440, 25),
                    Font = new Font("微软雅黑", 11, FontStyle.Bold)
                };
                form.Controls.Add(lblTitle);

                var lblSerial = new Label
                {
                    Text = "唯一序列号:",
                    Location = new Point(20, 45),
                    Size = new Size(100, 20)
                };
                form.Controls.Add(lblSerial);

                var txtSerial = new TextBox
                {
                    Text = serialNumber,
                    Location = new Point(20, 67),
                    Size = new Size(440, 25),
                    ReadOnly = true,
                    BackColor = Color.FromArgb(240, 240, 240),
                    Font = new Font("Consolas", 9)
                };
                form.Controls.Add(txtSerial);

                var btnCopySerial = new Button
                {
                    Text = "复制序列号",
                    Location = new Point(20, 96),
                    Size = new Size(100, 24),
                    Font = new Font("微软雅黑", 8)
                };
                btnCopySerial.Click += (_, _) =>
                {
                    Clipboard.SetText(txtSerial.Text);
                    btnCopySerial.Text = "已复制";
                };
                form.Controls.Add(btnCopySerial);

                var lblCode = new Label
                {
                    Text = "激活码:",
                    Location = new Point(20, 128),
                    Size = new Size(100, 20)
                };
                form.Controls.Add(lblCode);

                var txtCode = new TextBox
                {
                    Location = new Point(20, 150),
                    Size = new Size(440, 60),
                    Multiline = true,
                    ScrollBars = ScrollBars.Vertical,
                    Font = new Font("Consolas", 9)
                };
                form.Controls.Add(txtCode);

                var lblHint = new Label
                {
                    Text = "请将上方唯一序列号发给管理员，获取激活码后粘贴到上方输入框",
                    Location = new Point(20, 216),
                    Size = new Size(440, 20),
                    ForeColor = Color.Gray
                };
                form.Controls.Add(lblHint);

                var lblUrl = new Label
                {
                    Text = "获取激活码：",
                    Location = new Point(20, 236),
                    Size = new Size(100, 20),
                    Font = new Font("微软雅黑", 8)
                };
                form.Controls.Add(lblUrl);

                var linkUrl = new LinkLabel
                {
                    Text = "https://tools.marschat.online/activecode/index.html",
                    Location = new Point(95, 236),
                    Size = new Size(365, 20),
                    ForeColor = Color.FromArgb(0, 120, 212),
                    Font = new Font("微软雅黑", 8),
                    AutoSize = true,
                    LinkBehavior = LinkBehavior.AlwaysUnderline,
                    Padding = new Padding(0)
                };
                linkUrl.LinkClicked += (_, e) =>
                {
                    try { System.Diagnostics.Process.Start(linkUrl.Text); } catch { }
                };
                form.Controls.Add(linkUrl);

                var btnCopyUrl = new Button
                {
                    Text = "复制地址",
                    Location = new Point(20, 256),
                    Size = new Size(100, 22),
                    Font = new Font("微软雅黑", 8)
                };
                btnCopyUrl.Click += (_, _) =>
                {
                    Clipboard.SetText(linkUrl.Text);
                    btnCopyUrl.Text = "已复制";
                };
                form.Controls.Add(btnCopyUrl);

                var btnActivate = new Button
                {
                    Text = "激活",
                    Location = new Point(300, 300),
                    Size = new Size(80, 28),
                    BackColor = Color.FromArgb(0, 120, 212),
                    ForeColor = Color.White,
                    FlatStyle = FlatStyle.Flat
                };
                btnActivate.FlatAppearance.BorderSize = 0;

                var btnExit = new Button
                {
                    Text = "退出",
                    Location = new Point(390, 300),
                    Size = new Size(80, 28)
                };

                form.Controls.Add(btnActivate);
                form.Controls.Add(btnExit);

                bool activated = false;
                bool exitApp = false;
                string activatedCode = null;

                btnActivate.Click += (_, _) =>
                {
                    string code = txtCode.Text.Trim();
                    if (string.IsNullOrWhiteSpace(code))
                    {
                        MessageBox.Show("请输入激活码", "提示", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                        return;
                    }

                    VerifyResult result = ActivationGuard.CheckWithAutoDevice(code);

                    if (result.Success && !result.Expired && !result.DeviceMismatch)
                    {
                        SecureStorage.Save(licPath, code);

                        MessageBox.Show("激活成功！", "授权验证", MessageBoxButtons.OK, MessageBoxIcon.Information);
                        activated = true;
                        activatedCode = code;
                        form.Close();
                    }
                    else
                    {
                        string msg = result.DeviceMismatch
                            ? "设备不匹配，此激活码已绑定其他设备。"
                            : result.Expired
                                ? "激活码已过期，请联系管理员续期。"
                                : "激活码无效，请检查是否输入正确。";

                        MessageBox.Show(msg, "激活失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    }
                };

                btnExit.Click += (_, _) =>
                {
                    exitApp = true;
                    form.Close();
                };

                form.FormClosing += (s, e) =>
                {
                    if (!activated && !exitApp)
                    {
                        exitApp = true;
                    }
                };

                form.ShowDialog();

                if (activated)
                {
                    return activatedCode;
                }

                if (exitApp)
                {
                    return null;
                }
            }
        }

        private static void ShowExpiredDialog(string msg)
        {
            try
            {
                if (Application.OpenForms.Count > 0)
                {
                    var mainForm = Application.OpenForms[0];
                    mainForm.Invoke(new Action(() =>
                    {
                        MessageBox.Show(
                            "授权已失效，程序即将退出。",
                            "授权验证",
                            MessageBoxButtons.OK,
                            MessageBoxIcon.Warning);
                        Application.Exit();
                    }));
                }
                else
                {
                    MessageBox.Show(
                        "授权已失效，程序即将退出。",
                        "授权验证",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Warning);
                    Application.Exit();
                }
            }
            catch { }

            Environment.Exit(1002);
        }
    }
}
