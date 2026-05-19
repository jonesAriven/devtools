using System.Drawing;
using System.IO;
using System.Text;
using System.Windows.Forms;
using Jones.Activation;

namespace QRCodeTool;

static class Program
{
    [STAThread]
    static void Main()
    {
        ApplicationConfiguration.Initialize();

        string licPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "activation.lic");
        string activationCode = VerifyActivation(licPath);

        if (activationCode == null)
        {
            return;
        }

        ActivationGuard.StartPeriodicCheckWithAutoDevice(activationCode, 60000, (msg) =>
        {
            try
            {
                File.Delete(licPath);
            }
            catch { }

            MessageBox.Show(
                msg + "\n\n程序即将退出。",
                "授权已失效",
                MessageBoxButtons.OK,
                MessageBoxIcon.Warning);

            Environment.Exit(1002);
        });

        Application.Run(new Form1());
        ActivationGuard.StopPeriodicCheck();
    }

    private static string VerifyActivation(string licPath)
    {
        try
        {
            string savedCode = "";

            if (File.Exists(licPath))
            {
                savedCode = File.ReadAllText(licPath, Encoding.UTF8).Trim();
            }

            if (!string.IsNullOrWhiteSpace(savedCode))
            {
                VerifyResult result = ActivationGuard.CheckWithAutoDevice(savedCode);

                if (result.Success && !result.Expired && !result.DeviceMismatch)
                {
                    return savedCode;
                }

                if (result.Expired)
                {
                    MessageBox.Show(
                        "激活码已过期，请重新输入激活码。",
                        "授权验证",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Warning);
                }
                else if (result.DeviceMismatch)
                {
                    MessageBox.Show(
                        "设备不匹配，此激活码已绑定其他设备。\n请重新输入本机的激活码。",
                        "授权验证",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Warning);
                }
            }

            return ShowActivationDialog(licPath);
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                $"授权验证异常！\n\n{ex.Message}",
                "授权验证",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
            return null;
        }
    }

    private static string ShowActivationDialog(string licPath)
    {
        string serialNumber = DeviceInfo.GetSerialNumber("QRTOOL");

        while (true)
        {
            using var form = new Form();
            form.Text = "软件激活";
            form.Size = new Size(500, 300);
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

            var btnActivate = new Button
            {
                Text = "激活",
                Location = new Point(300, 245),
                Size = new Size(80, 28),
                BackColor = Color.FromArgb(0, 120, 212),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat
            };
            btnActivate.FlatAppearance.BorderSize = 0;

            var btnExit = new Button
            {
                Text = "退出",
                Location = new Point(390, 245),
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
                    try
                    {
                        File.WriteAllText(licPath, code, Encoding.UTF8);
                    }
                    catch { }

                    MessageBox.Show("激活成功！", "授权验证", MessageBoxButtons.OK, MessageBoxIcon.Information);
                    activated = true;
                    activatedCode = code;
                    form.Close();
                }
                else
                {
                    string msg = result.DeviceMismatch
                        ? "设备不匹配！此激活码已绑定其他设备。"
                        : result.Expired
                            ? "激活码已过期！请联系管理员续期。"
                            : "激活码无效！请检查是否输入正确。";

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
}
