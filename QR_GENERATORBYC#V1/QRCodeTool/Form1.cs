using System.Drawing;
using System.IO;
using System.Text;
using ZXing;
using ZXing.Common;
using ZXing.QrCode;
using ZXing.QrCode.Internal;

namespace QRCodeTool;

public partial class Form1 : Form
{
    private readonly PictureBox _picQr = new();
    private readonly TextBox _txtContent = new();
    private Point _startPoint;
    private bool _isSelecting;
    private Form? _maskForm;
    private readonly string _logPath;
    private CancellationTokenSource? _cancelToken;

    public Form1()
    {
        InitializeComponent();
        _logPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "qrcode_tool.log");
        Log("程序启动");
        BuildUI();
    }

    private void Log(string message)
    {
        try
        {
            var logEntry = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {message}\n";
            File.AppendAllText(_logPath, logEntry, Encoding.UTF8);
        }
        catch
        {
            // 忽略日志写入错误
        }
    }

    private void BuildUI()
    {
        try
        {
            Text = @"二维码工具（长文本增强版）";
            Size = new Size(560, 720);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;

            var groupBox = new GroupBox
            {
                Text = @"二维码预览",
                Location = new Point(20, 20),
                Size = new Size(500, 310)
            };
            Controls.Add(groupBox);

            _picQr.Size = new Size(280, 280);
            _picQr.Location = new Point(110, 20);
            _picQr.SizeMode = PictureBoxSizeMode.Zoom;
            groupBox.Controls.Add(_picQr);

            var btnCapture = new Button
            {
                Text = @"截图识别",
                Location = new Point(130, 350),
                Size = new Size(120, 40)
            };
            btnCapture.Click += (_, _) => BeginCapture();
            Controls.Add(btnCapture);

            var btnUpload = new Button
            {
                Text = @"上传图片识别",
                Location = new Point(290, 350),
                Size = new Size(120, 40)
            };
            btnUpload.Click += (_, _) => UploadImage();
            Controls.Add(btnUpload);

            var lblStatus = new Label
            {
                Text = @"就绪",
                Location = new Point(20, 395),
                Size = new Size(500, 20),
                ForeColor = Color.Gray,
                Font = new Font("微软雅黑", 9)
            };
            Controls.Add(lblStatus);

            _txtContent.Multiline = true;
            _txtContent.ScrollBars = ScrollBars.Vertical;
            _txtContent.Location = new Point(20, 410);
            _txtContent.Size = new Size(500, 250);
            _txtContent.Font = new Font("微软雅黑", 10);
            _txtContent.TextChanged += (_, _) => GenerateQr(_txtContent.Text.Trim());
            Controls.Add(_txtContent);
            Log("UI构建完成");
        }
        catch (Exception ex)
        {
            Log($"UI构建失败: {ex.Message}");
            throw;
        }
    }

    private void GenerateQr(string content)
    {
        try
        {
            Log($"生成二维码: {content.Length} 字符");
            if (string.IsNullOrWhiteSpace(content))
            {
                _picQr.Image = null;
                return;
            }

            var writer = new BarcodeWriter<Bitmap>
            {
                Format = BarcodeFormat.QR_CODE,
                Options = new QrCodeEncodingOptions
                {
                    Width = 600,
                    Height = 600,
                    Margin = 0,
                    ErrorCorrection = ErrorCorrectionLevel.L,
                    CharacterSet = "UTF-8"
                },
                Renderer = new ZXing.Windows.Compatibility.BitmapRenderer()
            };

            _picQr.Image = writer.Write(content);
            Log("二维码生成成功");
        }
        catch (Exception ex)
        {
            Log($"生成二维码失败: {ex.Message}\n{ex.StackTrace}");
            MessageBox.Show($"生成二维码失败: {ex.Message}", @"错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private void BeginCapture()
    {
        try
        {
            Log("开始截图识别");
            TopMost = false;
            _maskForm = new Form
            {
                WindowState = FormWindowState.Maximized,
                FormBorderStyle = FormBorderStyle.None,
                BackColor = Color.Gray,
                Opacity = 0.25,
                TopMost = true,
                Cursor = Cursors.Cross
            };

            _maskForm.MouseDown += (_, e) => { _startPoint = e.Location; _isSelecting = true; };
            _maskForm.MouseMove += (_, _) => { };
            _maskForm.MouseUp += (_, e) =>
            {
                _isSelecting = false;
                _maskForm?.Close();
                TopMost = true;

                var x1 = Math.Min(_startPoint.X, e.X);
                var y1 = Math.Min(_startPoint.Y, e.Y);
                var w = Math.Abs(e.X - _startPoint.X);
                var h = Math.Abs(e.Y - _startPoint.Y);

                Log($"截图区域: x={x1}, y={y1}, w={w}, h={h}");

                if (w < 40 || h < 40)
                {
                    _txtContent.Text = @"选区过小";
                    Log("选区过小");
                    return;
                }

                using var bmp = new Bitmap(w, h);
                using var g = Graphics.FromImage(bmp);
                g.CopyFromScreen(x1, y1, 0, 0, bmp.Size);
                DecodeQr(bmp);
            };

            _maskForm.ShowDialog();
        }
        catch (Exception ex)
        {
            Log($"截图识别失败: {ex.Message}\n{ex.StackTrace}");
            MessageBox.Show($"截图识别失败: {ex.Message}", @"错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private async void DecodeQr(Bitmap bmp)
    {
        _cancelToken?.Cancel();
        _cancelToken = new CancellationTokenSource();
        
        Bitmap? clonedBmp = null;
        
        try
        {
            clonedBmp = new Bitmap(bmp);
            Log($"开始解码二维码: {clonedBmp.Width}x{clonedBmp.Height}");
            
            var result = await Task.Run(() =>
            {
                return TryDecodeWithProgress(clonedBmp!, _cancelToken.Token);
            }, _cancelToken.Token);
            
            if (result != null)
            {
                _txtContent.Text = result.Text;
                Log($"解码成功: {result.Text.Length} 字符");
            }
            else
            {
                _txtContent.Text = @"未识别到二维码";
                Log("未识别到二维码");
            }
        }
        catch (OperationCanceledException)
        {
            _txtContent.Text = @"识别已取消";
            Log("识别已取消");
        }
        catch (Exception ex)
        {
            Log($"解码失败: {ex.Message}");
            _txtContent.Text = $"识别失败: {ex.Message}";
        }
        finally
        {
            clonedBmp?.Dispose();
        }
    }

    private Result? TryDecodeWithProgress(Bitmap image, CancellationToken token)
    {
        var reader = new QRCodeReader();
        
        int[] scales = { 2, 3, 4, 5, 6, 8, 10 };
        int[] thresholds = { 50, 70, 90, 110, 130, 150, 170, 190, 210 };
        
        foreach (var scale in scales)
        {
            token.ThrowIfCancellationRequested();
            using var scaled = ScaleImage(image, scale);
            var result = QuickDecode(reader, scaled);
            if (result != null) return result;
        }
        
        foreach (var thresh in thresholds)
        {
            token.ThrowIfCancellationRequested();
            using var binary = ApplyThreshold(image, thresh);
            var result = QuickDecode(reader, binary);
            if (result != null) return result;
        }
        
        foreach (var thresh in thresholds)
        {
            token.ThrowIfCancellationRequested();
            using var binary = ApplyThreshold(image, thresh);
            foreach (var scale in new[] { 2, 3, 4 })
            {
                token.ThrowIfCancellationRequested();
                using var scaled = ScaleImage(binary, scale);
                var result = QuickDecode(reader, scaled);
                if (result != null) return result;
            }
        }
        
        int[] adaptiveBlockSizes = { 80, 100, 120, 140 };
        foreach (var blockSize in adaptiveBlockSizes)
        {
            token.ThrowIfCancellationRequested();
            using var adaptive = ApplyAdaptiveThreshold(image, blockSize);
            var result = QuickDecode(reader, adaptive);
            if (result != null) return result;
        }
        
        token.ThrowIfCancellationRequested();
        using var otsu = ApplyOtsuThreshold(image);
        var otsuResult = QuickDecode(reader, otsu);
        if (otsuResult != null) return otsuResult;
        
        foreach (var scale in new[] { 2, 3, 4, 5, 6 })
        {
            token.ThrowIfCancellationRequested();
            using var otsuScaled = ScaleImage(otsu, scale);
            var result = QuickDecode(reader, otsuScaled);
            if (result != null) return result;
        }
        
        token.ThrowIfCancellationRequested();
        using var enhanced = EnhanceContrast(image);
        var enhancedResult = QuickDecode(reader, enhanced);
        if (enhancedResult != null) return enhancedResult;
        
        foreach (var scale in new[] { 2, 3, 4 })
        {
            token.ThrowIfCancellationRequested();
            using var enhancedScaled = ScaleImage(enhanced, scale);
            var result = QuickDecode(reader, enhancedScaled);
            if (result != null) return result;
        }
        
        token.ThrowIfCancellationRequested();
        using var denoised = RemoveNoise(image);
        var denoisedResult = QuickDecode(reader, denoised);
        if (denoisedResult != null) return denoisedResult;
        
        foreach (var scale in new[] { 2, 3 })
        {
            token.ThrowIfCancellationRequested();
            using var denoisedScaled = ScaleImage(denoised, scale);
            var result = QuickDecode(reader, denoisedScaled);
            if (result != null) return result;
        }
        
        return null;
    }

    private Result? QuickDecode(QRCodeReader reader, Bitmap image)
    {
        try
        {
            var hints = new Dictionary<DecodeHintType, object>
            {
                { DecodeHintType.TRY_HARDER, true }
            };
            
            var lumSource = new RGBLuminanceSource(image);
            var binaryBitmap = new BinaryBitmap(new HybridBinarizer(lumSource));
            return reader.decode(binaryBitmap, hints);
        }
        catch
        {
            return null;
        }
    }

    private Bitmap ScaleImage(Bitmap original, int scale)
    {
        var scaled = new Bitmap(original.Width * scale, original.Height * scale);
        using var g = Graphics.FromImage(scaled);
        g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
        g.DrawImage(original, 0, 0, scaled.Width, scaled.Height);
        return scaled;
    }

    private Bitmap ApplyThreshold(Bitmap original, int threshold)
    {
        var result = new Bitmap(original.Width, original.Height);
        for (int y = 0; y < original.Height; y++)
        {
            for (int x = 0; x < original.Width; x++)
            {
                Color pixel = original.GetPixel(x, y);
                int gray = (int)(pixel.R * 0.299 + pixel.G * 0.587 + pixel.B * 0.114);
                result.SetPixel(x, y, gray > threshold ? Color.White : Color.Black);
            }
        }
        return result;
    }

    private Bitmap EnhanceContrast(Bitmap original)
    {
        var result = new Bitmap(original.Width, original.Height);
        for (int y = 0; y < original.Height; y++)
        {
            for (int x = 0; x < original.Width; x++)
            {
                Color pixel = original.GetPixel(x, y);
                int gray = (int)(pixel.R * 0.299 + pixel.G * 0.587 + pixel.B * 0.114);
                gray = Math.Max(0, Math.Min(255, (gray - 128) * 2 + 128));
                result.SetPixel(x, y, Color.FromArgb(gray, gray, gray));
            }
        }
        return result;
    }

    private Bitmap RemoveNoise(Bitmap original)
    {
        var result = new Bitmap(original.Width, original.Height);
        for (int y = 0; y < original.Height; y++)
        {
            for (int x = 0; x < original.Width; x++)
            {
                Color pixel = original.GetPixel(x, y);
                int gray = (int)(pixel.R * 0.299 + pixel.G * 0.587 + pixel.B * 0.114);
                
                if (gray < 80) gray = 0;
                else if (gray > 180) gray = 255;
                else gray = 128;
                
                result.SetPixel(x, y, Color.FromArgb(gray, gray, gray));
            }
        }
        return result;
    }

    private Bitmap InvertColors(Bitmap original)
    {
        var result = new Bitmap(original.Width, original.Height);
        for (int y = 0; y < original.Height; y++)
        {
            for (int x = 0; x < original.Width; x++)
            {
                Color pixel = original.GetPixel(x, y);
                result.SetPixel(x, y, Color.FromArgb(255 - pixel.R, 255 - pixel.G, 255 - pixel.B));
            }
        }
        return result;
    }

    private Bitmap ApplyAdaptiveThreshold(Bitmap original, int blockSize)
    {
        var result = new Bitmap(original.Width, original.Height);
        int halfBlock = blockSize / 2;
        
        for (int y = 0; y < original.Height; y++)
        {
            for (int x = 0; x < original.Width; x++)
            {
                int sum = 0;
                int count = 0;
                
                for (int dy = -halfBlock; dy <= halfBlock; dy++)
                {
                    for (int dx = -halfBlock; dx <= halfBlock; dx++)
                    {
                        int nx = Math.Clamp(x + dx, 0, original.Width - 1);
                        int ny = Math.Clamp(y + dy, 0, original.Height - 1);
                        Color pixel = original.GetPixel(nx, ny);
                        sum += (int)(pixel.R * 0.299 + pixel.G * 0.587 + pixel.B * 0.114);
                        count++;
                    }
                }
                
                int localThreshold = sum / count;
                Color currentPixel = original.GetPixel(x, y);
                int gray = (int)(currentPixel.R * 0.299 + currentPixel.G * 0.587 + currentPixel.B * 0.114);
                result.SetPixel(x, y, gray > localThreshold ? Color.White : Color.Black);
            }
        }
        return result;
    }

    private Bitmap ApplyOtsuThreshold(Bitmap original)
    {
        var grayValues = new int[256];
        int width = original.Width;
        int height = original.Height;
        
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                Color pixel = original.GetPixel(x, y);
                int gray = (int)(pixel.R * 0.299 + pixel.G * 0.587 + pixel.B * 0.114);
                grayValues[gray]++;
            }
        }
        
        int total = width * height;
        float sum = 0;
        for (int i = 0; i < 256; i++) sum += i * grayValues[i];
        
        float sumB = 0;
        int wB = 0;
        float maxVariance = 0;
        int threshold = 0;
        
        for (int t = 0; t < 256; t++)
        {
            wB += grayValues[t];
            if (wB == 0) continue;
            
            int wF = total - wB;
            if (wF == 0) break;
            
            sumB += t * grayValues[t];
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;
            float variance = wB * wF * (mB - mF) * (mB - mF);
            
            if (variance > maxVariance)
            {
                maxVariance = variance;
                threshold = t;
            }
        }
        
        return ApplyThreshold(original, threshold);
    }

    private void UploadImage()
    {
        try
        {
            using var ofd = new OpenFileDialog
            {
                Filter = @"图片|*.png;*.jpg;*.jpeg;*.bmp"
            };

            if (ofd.ShowDialog() == DialogResult.OK)
            {
                Log($"上传图片: {ofd.FileName}");
                using var img = new Bitmap(ofd.FileName);
                DecodeQr(img);
            }
        }
        catch (Exception ex)
        {
            Log($"上传图片失败: {ex.Message}\n{ex.StackTrace}");
            MessageBox.Show($"打开图片失败: {ex.Message}", @"错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}

public class RGBLuminanceSource : LuminanceSource
{
    private byte[] _luminance;

    public RGBLuminanceSource(Bitmap bitmap) : base(bitmap.Width, bitmap.Height)
    {
        var width = bitmap.Width;
        var height = bitmap.Height;
        _luminance = new byte[width * height];
        
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                Color pixel = bitmap.GetPixel(x, y);
                _luminance[y * width + x] = (byte)((pixel.R * 0.299) + (pixel.G * 0.587) + (pixel.B * 0.114));
            }
        }
    }

    private RGBLuminanceSource(byte[] luminance, int width, int height) : base(width, height)
    {
        _luminance = luminance;
    }

    public override byte[] Matrix => _luminance;

    public override byte[] getRow(int y, byte[] row)
    {
        if (row == null || row.Length < Width)
        {
            row = new byte[Width];
        }
        Array.Copy(_luminance, y * Width, row, 0, Width);
        return row;
    }

    public override LuminanceSource crop(int left, int top, int width, int height)
    {
        byte[] newLuminance = new byte[width * height];
        for (int y = 0; y < height; y++)
        {
            Array.Copy(_luminance, (y + top) * Width + left, newLuminance, y * width, width);
        }
        return new RGBLuminanceSource(newLuminance, width, height);
    }
}
