using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
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
    private bool _compressMode = true;
    private readonly ToolTip _toolTip = new();

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
            Size = new Size(440, 560);
            MinimumSize = new Size(440, 460);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.Sizable;

            var toolbar = new Panel
            {
                Location = new Point(0, 0),
                Size = new Size(440, 30),
                BackColor = Color.FromArgb(245, 245, 245)
            };
            Controls.Add(toolbar);

            var chkCompress = new CheckBox
            {
                Text = @"压缩模式",
                Location = new Point(6, 4),
                Size = new Size(85, 22),
                Checked = true,
                Font = new Font("微软雅黑", 9)
            };
            chkCompress.CheckedChanged += (_, _) =>
            {
                _compressMode = chkCompress.Checked;
                GenerateQr(_txtContent.Text.Trim());
            };
            _toolTip.SetToolTip(chkCompress, "开启后GZip压缩，容量提升2~3倍");
            toolbar.Controls.Add(chkCompress);

            var btnCapture = new PictureBox
            {
                Location = new Point(96, 3),
                Size = new Size(24, 24),
                SizeMode = PictureBoxSizeMode.CenterImage,
                Image = CreateSmallCaptureIcon(),
                Cursor = Cursors.Hand
            };
            btnCapture.Click += (_, _) => BeginCapture();
            _toolTip.SetToolTip(btnCapture, "截图识别二维码");
            toolbar.Controls.Add(btnCapture);

            var btnUpload = new PictureBox
            {
                Location = new Point(124, 3),
                Size = new Size(24, 24),
                SizeMode = PictureBoxSizeMode.CenterImage,
                Image = CreateSmallUploadIcon(),
                Cursor = Cursors.Hand
            };
            btnUpload.Click += (_, _) => UploadImage();
            _toolTip.SetToolTip(btnUpload, "上传图片识别二维码");
            toolbar.Controls.Add(btnUpload);

            _picQr.Size = new Size(400, 400);
            _picQr.Location = new Point(10, 34);
            _picQr.SizeMode = PictureBoxSizeMode.Zoom;
            _picQr.BackColor = Color.White;
            _picQr.BorderStyle = BorderStyle.FixedSingle;
            Controls.Add(_picQr);

            _txtContent.Multiline = true;
            _txtContent.ScrollBars = ScrollBars.Vertical;
            _txtContent.Location = new Point(10, 438);
            _txtContent.Size = new Size(412, 100);
            _txtContent.Font = new Font("微软雅黑", 10);
            _txtContent.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
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

    private static string CompressText(string text)
    {
        var bytes = Encoding.UTF8.GetBytes(text);
        using var ms = new MemoryStream();
        using (var gz = new GZipStream(ms, CompressionLevel.Optimal, true))
        {
            gz.Write(bytes, 0, bytes.Length);
        }
        return "GZ:" + Convert.ToBase64String(ms.ToArray());
    }

    private static string? TryDecompressText(string text)
    {
        if (!text.StartsWith("GZ:")) return null;
        try
        {
            var data = Convert.FromBase64String(text.Substring(3));
            using var ms = new MemoryStream(data);
            using var gz = new GZipStream(ms, CompressionMode.Decompress);
            using var sr = new StreamReader(gz, Encoding.UTF8);
            return sr.ReadToEnd();
        }
        catch
        {
            return null;
        }
    }

    private void GenerateQr(string content)
    {
        try
        {
            Log($"生成二维码: {content.Length} 字符, 压缩模式: {_compressMode}");
            if (string.IsNullOrWhiteSpace(content))
            {
                _picQr.Image = null;
                return;
            }

            var encodeContent = _compressMode ? CompressText(content) : content;
            Log($"编码内容长度: {encodeContent.Length} 字符");

            var writer = new BarcodeWriter<Bitmap>
            {
                Format = BarcodeFormat.QR_CODE,
                Options = new QrCodeEncodingOptions
                {
                    Width = 600,
                    Height = 600,
                    Margin = 2,
                    ErrorCorrection = ErrorCorrectionLevel.L,
                    CharacterSet = "UTF-8"
                },
                Renderer = new ZXing.Windows.Compatibility.BitmapRenderer()
            };

            _picQr.Image = writer.Write(encodeContent);

            using var testBmp = new Bitmap(_picQr.Image);
            var testResult = QuickDecode(new QRCodeReader(), testBmp);
            if (testResult != null)
            {
                Log($"自检解码成功: {testResult.Text.Length} 字符");
            }
            else
            {
                Log("自检解码失败: 原始600x600二维码自身就无法被ZXing解码!");
            }

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

            var allScreens = Screen.AllScreens;
            var minX = allScreens.Min(s => s.Bounds.X);
            var minY = allScreens.Min(s => s.Bounds.Y);
            var maxRight = allScreens.Max(s => s.Bounds.Right);
            var maxBottom = allScreens.Max(s => s.Bounds.Bottom);
            var totalW = maxRight - minX;
            var totalH = maxBottom - minY;

            Log($"虚拟屏幕: {minX},{minY} {totalW}x{totalH} ({allScreens.Length}个显示器)");

            var screenBmp = new Bitmap(totalW, totalH);
            using (var sg = Graphics.FromImage(screenBmp))
            {
                sg.CopyFromScreen(minX, minY, 0, 0, screenBmp.Size);
            }

            _maskForm = new Form
            {
                StartPosition = FormStartPosition.Manual,
                Bounds = new Rectangle(minX, minY, totalW, totalH),
                FormBorderStyle = FormBorderStyle.None,
                BackColor = Color.Black,
                Opacity = 0.3,
                TopMost = true,
                Cursor = Cursors.Cross,
                ShowInTaskbar = false,
                KeyPreview = true
            };

            Rectangle selectionRect = Rectangle.Empty;
            bool hasSelection = false;

            _maskForm.Paint += (s, e) =>
            {
                if (!hasSelection) return;
                var g = e.Graphics;

                using var clearPen = new Pen(Color.Lime, 3);
                g.DrawRectangle(clearPen, selectionRect);

                using var font = new Font("微软雅黑", 10);
                var text = $"{selectionRect.Width} × {selectionRect.Height}";
                var textSize = g.MeasureString(text, font);
                int labelX = selectionRect.X;
                int labelY = selectionRect.Y - (int)textSize.Height - 6;
                if (labelY < 0) labelY = selectionRect.Bottom + 4;

                using var bgBrush = new SolidBrush(Color.FromArgb(200, 0, 0, 0));
                g.FillRectangle(bgBrush, labelX, labelY, textSize.Width + 8, textSize.Height + 4);
                g.DrawString(text, font, Brushes.Lime, labelX + 4, labelY + 2);
            };

            _maskForm.MouseDown += (_, e) =>
            {
                _startPoint = e.Location;
                _isSelecting = true;
                hasSelection = false;
                selectionRect = Rectangle.Empty;
            };

            _maskForm.MouseMove += (_, e) =>
            {
                if (!_isSelecting) return;

                int x = Math.Min(_startPoint.X, e.X);
                int y = Math.Min(_startPoint.Y, e.Y);
                int w = Math.Abs(e.X - _startPoint.X);
                int h = Math.Abs(e.Y - _startPoint.Y);

                selectionRect = new Rectangle(x, y, w, h);
                hasSelection = true;
                _maskForm.Invalidate();
            };

            _maskForm.MouseUp += (_, e) =>
            {
                _isSelecting = false;

                var x1 = Math.Min(_startPoint.X, e.X);
                var y1 = Math.Min(_startPoint.Y, e.Y);
                var w = Math.Abs(e.X - _startPoint.X);
                var h = Math.Abs(e.Y - _startPoint.Y);

                _maskForm?.Close();
                TopMost = true;

                Log($"截图区域: x={x1}, y={y1}, w={w}, h={h}");

                if (w < 40 || h < 40)
                {
                    _txtContent.Text = @"选区过小";
                    Log("选区过小");
                    screenBmp.Dispose();
                    return;
                }

                var cropBmp = new Bitmap(w, h);
                using (var cg = Graphics.FromImage(cropBmp))
                {
                    cg.DrawImage(screenBmp, new Rectangle(0, 0, w, h), new Rectangle(x1, y1, w, h), GraphicsUnit.Pixel);
                }
                screenBmp.Dispose();

                DecodeQr(cropBmp);
                cropBmp.Dispose();
            };

            _maskForm.KeyDown += (_, e) =>
            {
                if (e.KeyCode == Keys.Escape)
                {
                    _isSelecting = false;
                    _maskForm?.Close();
                    screenBmp.Dispose();
                    TopMost = true;
                }
            };

            _maskForm.FormClosed += (_, _) =>
            {
                if (_isSelecting)
                {
                    _isSelecting = false;
                    screenBmp.Dispose();
                }
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
            // var debugPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, $"debug_screenshot_{DateTime.Now:HHmmss}.png");
            // clonedBmp.Save(debugPath, System.Drawing.Imaging.ImageFormat.Png);
            // Log($"截图已保存: {debugPath}");
            
            var result = await Task.Run(() =>
            {
                return TryDecodeWithProgress(clonedBmp!, _cancelToken.Token);
            }, _cancelToken.Token);
            
            if (result != null)
            {
                var decodedText = result.Text;
                var decompressed = TryDecompressText(decodedText);
                if (decompressed != null)
                {
                    _txtContent.Text = decompressed;
                    Log($"解码成功(压缩): {decompressed.Length} 字符");
                }
                else
                {
                    _txtContent.Text = decodedText;
                    Log($"解码成功(原文): {decodedText.Length} 字符");
                }
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
        var sw = System.Diagnostics.Stopwatch.StartNew();
        _firstDecodeError = null;

        int[] scales = { 2, 3, 4, 5 };
        int[] thresholds = { 50, 70, 90, 110, 130, 150, 170, 190, 210 };

        var strategyGroup = 0;
        Log($"策略组{++strategyGroup}: 缩放 2x-5x, 图片{image.Width}x{image.Height}");
        foreach (var scale in scales)
        {
            token.ThrowIfCancellationRequested();
            using var scaled = ScaleImage(image, scale);
            var result = QuickDecode(reader, scaled);
            if (result != null)
            {
                Log($"=> 解码策略: 缩放{scale}x, 耗时{sw.ElapsedMilliseconds}ms");
                return result;
            }
        }

        token.ThrowIfCancellationRequested();
        Log($"策略组{++strategyGroup}: 阈值 50-210");
        foreach (var thresh in thresholds)
        {
            token.ThrowIfCancellationRequested();
            using var binary = ApplyThreshold(image, thresh);
            var result = QuickDecode(reader, binary);
            if (result != null)
            {
                Log($"=> 解码策略: 阈值{thresh}, 耗时{sw.ElapsedMilliseconds}ms");
                return result;
            }
        }

        token.ThrowIfCancellationRequested();
        Log($"策略组{++strategyGroup}: 阈值+缩放 50-210×2-4x");
        foreach (var thresh in thresholds)
        {
            token.ThrowIfCancellationRequested();
            using var binary = ApplyThreshold(image, thresh);
            foreach (var scale in new[] { 2, 3, 4 })
            {
                token.ThrowIfCancellationRequested();
                using var scaled = ScaleImage(binary, scale);
                var result = QuickDecode(reader, scaled);
                if (result != null)
                {
                    Log($"=> 解码策略: 阈值{thresh}+缩放{scale}x, 耗时{sw.ElapsedMilliseconds}ms");
                    return result;
                }
            }
        }

        token.ThrowIfCancellationRequested();
        Log($"策略组{++strategyGroup}: Otsu+缩放");
        using var otsu = ApplyOtsuThreshold(image);
        var otsuResult = QuickDecode(reader, otsu);
        if (otsuResult != null)
        {
            Log($"=> 解码策略: Otsu, 耗时{sw.ElapsedMilliseconds}ms");
            return otsuResult;
        }

        foreach (var scale in new[] { 2, 3, 4, 5 })
        {
            token.ThrowIfCancellationRequested();
            using var otsuScaled = ScaleImage(otsu, scale);
            var result = QuickDecode(reader, otsuScaled);
            if (result != null)
            {
                Log($"=> 解码策略: Otsu+缩放{scale}x, 耗时{sw.ElapsedMilliseconds}ms");
                return result;
            }
        }

        token.ThrowIfCancellationRequested();
        Log($"策略组{++strategyGroup}: 对比度增强+缩放");
        using var enhanced = EnhanceContrast(image);
        var enhancedResult = QuickDecode(reader, enhanced);
        if (enhancedResult != null)
        {
            Log($"=> 解码策略: 对比度增强, 耗时{sw.ElapsedMilliseconds}ms");
            return enhancedResult;
        }

        foreach (var scale in new[] { 2, 3, 4 })
        {
            token.ThrowIfCancellationRequested();
            using var enhancedScaled = ScaleImage(enhanced, scale);
            var result = QuickDecode(reader, enhancedScaled);
            if (result != null)
            {
                Log($"=> 解码策略: 对比度增强+缩放{scale}x, 耗时{sw.ElapsedMilliseconds}ms");
                return result;
            }
        }

        token.ThrowIfCancellationRequested();
        Log($"策略组{++strategyGroup}: 反色");
        using var inverted = InvertColors(image);
        var invertedResult = QuickDecode(reader, inverted);
        if (invertedResult != null)
        {
            Log($"=> 解码策略: 反色, 耗时{sw.ElapsedMilliseconds}ms");
            return invertedResult;
        }

        token.ThrowIfCancellationRequested();
        Log($"策略组{++strategyGroup}: 去噪+缩放");
        using var denoised = RemoveNoise(image);
        var denoisedResult = QuickDecode(reader, denoised);
        if (denoisedResult != null)
        {
            Log($"=> 解码策略: 去噪, 耗时{sw.ElapsedMilliseconds}ms");
            return denoisedResult;
        }

        foreach (var scale in new[] { 2, 3 })
        {
            token.ThrowIfCancellationRequested();
            using var denoisedScaled = ScaleImage(denoised, scale);
            var result = QuickDecode(reader, denoisedScaled);
            if (result != null)
            {
                Log($"=> 解码策略: 去噪+缩放{scale}x, 耗时{sw.ElapsedMilliseconds}ms");
                return result;
            }
        }

        Log($"所有策略均未识别, 耗时{sw.ElapsedMilliseconds}ms, ZXing错误: {_firstDecodeError ?? "无"}");
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
        catch (Exception ex)
        {
            if (_firstDecodeError == null)
            {
                _firstDecodeError = ex.GetType().Name + ": " + ex.Message;
                Log($"ZXing异常: {_firstDecodeError} (图片{image.Width}x{image.Height})");
            }
            return null;
        }
    }

    private string? _firstDecodeError;

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

    private static Bitmap CreateSmallCaptureIcon()
    {
        var bmp = new Bitmap(24, 24);
        using var g = Graphics.FromImage(bmp);
        g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        using var pen = new Pen(Color.FromArgb(64, 64, 64), 1.5f);
        g.DrawRectangle(pen, 4, 7, 16, 13);
        g.FillRectangle(Brushes.White, 7, 3, 10, 7);
        g.DrawRectangle(pen, 7, 3, 10, 7);
        g.DrawLine(pen, 9, 1, 15, 1);
        using var lensBrush = new SolidBrush(Color.FromArgb(100, 100, 100));
        g.FillEllipse(lensBrush, 9, 10, 6, 6);
        return bmp;
    }

    private static Bitmap CreateSmallUploadIcon()
    {
        var bmp = new Bitmap(24, 24);
        using var g = Graphics.FromImage(bmp);
        g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        using var pen = new Pen(Color.FromArgb(64, 64, 64), 1.5f);
        var folderPts = new Point[] { new(2, 8), new(2, 22), new(22, 22), new(22, 8), new(11, 8), new(9, 5), new(5, 5), new(5, 8) };
        g.DrawPolygon(pen, folderPts);
        g.DrawLine(pen, 2, 8, 5, 8);
        using var arrowPen = new Pen(Color.FromArgb(0, 120, 212), 2);
        arrowPen.EndCap = System.Drawing.Drawing2D.LineCap.ArrowAnchor;
        g.DrawLine(arrowPen, 12, 19, 12, 11);
        return bmp;
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
