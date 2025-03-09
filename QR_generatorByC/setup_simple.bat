@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

title QR Generator 安装程序

rem 创建日志文件
>"setup_log.txt" (
echo ============================================
echo 安装日志 - %date% %time%
echo ============================================
echo.
)

echo 步骤1: 清理环境...
>>"setup_log.txt" echo [%date% %time%] 步骤1: 清理环境...
if exist "src" (
    rd /s /q "src"
    >>"setup_log.txt" echo [%date% %time%] 删除旧的src目录
)

echo 步骤2: 创建项目结构...
>>"setup_log.txt" echo [%date% %time%] 步骤2: 创建项目结构...
mkdir "src\QR_generatorByC"
cd "src\QR_generatorByC"
>>"..\..\setup_log.txt" echo [%date% %time%] 创建目录: %CD%

>>"..\..\setup_log.txt" echo [%date% %time%] 创建项目文件...
rem 创建项目文件
>"QR_generatorByC.csproj" (
echo ^<Project Sdk="Microsoft.NET.Sdk"^>
echo   ^<PropertyGroup^>
echo     ^<OutputType^>WinExe^</OutputType^>
echo     ^<TargetFramework^>net6.0-windows^</TargetFramework^>
echo     ^<Nullable^>enable^</Nullable^>
echo     ^<UseWindowsForms^>true^</UseWindowsForms^>
echo     ^<ImplicitUsings^>disable^</ImplicitUsings^>
echo   ^</PropertyGroup^>
echo   ^<ItemGroup^>
echo     ^<PackageReference Include="QRCoder" Version="1.4.3" /^>
echo   ^</ItemGroup^>
echo ^</Project^>
)
>>"..\..\setup_log.txt" echo [%date% %time%] 项目文件创建完成
type "QR_generatorByC.csproj" >>"..\..\setup_log.txt"
>>"..\..\setup_log.txt" echo.

>>"..\..\setup_log.txt" echo [%date% %time%] 创建Program.cs...
rem 创建Program.cs
>"Program.cs" (
echo using System;
echo using System.Windows.Forms;
echo.
echo namespace QR_generatorByC
echo {
echo     internal static class Program
echo     {
echo         [STAThread]
echo         static void Main^(^)
echo         {
echo             ApplicationConfiguration.Initialize^(^);
echo             Application.SetHighDpiMode^(HighDpiMode.SystemAware^);
echo             Application.EnableVisualStyles^(^);
echo             Application.SetCompatibleTextRenderingDefault^(false^);
echo             Application.Run^(new Form1^(^)^);
echo         }
echo     }
echo }
)

>>"..\..\setup_log.txt" echo [%date% %time%] Program.cs创建完成
type "Program.cs" >>"..\..\setup_log.txt"
>>"..\..\setup_log.txt" echo.

>>"..\..\setup_log.txt" echo [%date% %time%] 创建Form1.cs...
rem 创建Form1.cs
>"Form1.cs" (
echo using System;
echo using System.Drawing;
echo using System.Windows.Forms;
echo using QRCoder;
echo.
echo namespace QR_generatorByC
echo {
echo     public partial class Form1 : Form
echo     {
echo         private TextBox inputTextBox;
echo         private PictureBox qrPictureBox;
echo         private Button generateButton;
echo.
echo         public Form1^(^)
echo         {
echo             InitializeComponent^(^);
echo         }
echo.
echo         private void InitializeComponent()
echo         {
echo             this.SuspendLayout();
echo.
echo             // 输入框
echo             this.inputTextBox = new TextBox();
echo             this.inputTextBox.Location = new Point(10, 10);
echo             this.inputTextBox.Size = new Size(300, 20);
echo             this.Controls.Add(this.inputTextBox);
echo.
echo             // 生成按钮
echo             this.generateButton = new Button();
echo             this.generateButton.Location = new Point(10, 40);
echo             this.generateButton.Text = "生成二维码";
echo             this.generateButton.Click += new EventHandler(this.GenerateQRCode);
echo             this.Controls.Add(this.generateButton);
echo.
echo             // 二维码显示区域
echo             this.qrPictureBox = new PictureBox();
echo             this.qrPictureBox.Location = new Point(10, 80);
echo             this.qrPictureBox.Size = new Size(300, 300);
echo             this.qrPictureBox.BackColor = Color.White;
echo             this.Controls.Add(this.qrPictureBox);
echo.
echo             // 窗体设置
echo             this.ClientSize = new Size(350, 400);
echo             this.Text = "二维码生成器";
echo             this.ResumeLayout(false);
echo         }
echo.
echo         private void GenerateQRCode(object sender, EventArgs e)
echo         {
echo             try
echo             {
echo                 if (string.IsNullOrWhiteSpace(this.inputTextBox.Text))
echo                 {
echo                     MessageBox.Show("请输入内容");
echo                     return;
echo                 }
echo.
echo                 using (var generator = new QRCodeGenerator())
echo                 {
echo                     var data = generator.CreateQrCode(this.inputTextBox.Text, QRCodeGenerator.ECCLevel.Q);
echo                     using (var code = new QRCode(data))
echo                     {
echo                         var qrImage = code.GetGraphic(20);
echo                         this.qrPictureBox.Image = qrImage;
echo                     }
echo                 }
echo             }
echo             catch (Exception ex)
echo             {
echo                 MessageBox.Show("生成失败: " + ex.Message);
echo             }
echo         }
echo     }
echo }
)

>>"..\..\setup_log.txt" echo [%date% %time%] Form1.cs创建完成
type "Form1.cs" >>"..\..\setup_log.txt"
>>"..\..\setup_log.txt" echo.

echo 步骤3: 检查.NET环境...
>>"..\..\setup_log.txt" echo [%date% %time%] 步骤3: 检查.NET环境...
dotnet --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 未检测到.NET SDK，请先安装.NET 6.0 SDK或更高版本。
    >>"..\..\setup_log.txt" echo [%date% %time%] 错误: 未检测到.NET SDK
    cd ..\..\    
    exit /b 1
)
>>"..\..\setup_log.txt" echo [%date% %time%] .NET SDK检测成功

echo 步骤4: 还原NuGet包...
>>"..\..\setup_log.txt" echo [%date% %time%] 步骤4: 还原NuGet包...
dotnet restore
if %ERRORLEVEL% NEQ 0 (
    echo 错误: NuGet包还原失败。
    >>"..\..\setup_log.txt" echo [%date% %time%] 错误: NuGet包还原失败
    cd ..\..\    
    exit /b 1
)
>>"..\..\setup_log.txt" echo [%date% %time%] NuGet包还原成功

echo 步骤5: 编译项目...
>>"..\..\setup_log.txt" echo [%date% %time%] 步骤5: 编译项目...
dotnet build -c Release
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 项目编译失败。
    >>"..\..\setup_log.txt" echo [%date% %time%] 错误: 项目编译失败
    cd ..\..\    
    exit /b 1
)
>>"..\..\setup_log.txt" echo [%date% %time%] 项目编译成功

echo 步骤6: 发布应用程序...
>>"..\..\setup_log.txt" echo [%date% %time%] 步骤6: 发布应用程序...
echo 正在发布，这可能需要几分钟时间...
>>"..\..\.\setup_log.txt" echo [%date% %time%] 开始执行发布命令
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:PublishTrimmed=true -o "..\..\bin" --no-restore
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 应用程序发布失败。
    >>"..\..\setup_log.txt" echo [%date% %time%] 错误: 应用程序发布失败
    cd ..\..\    
    exit /b 1
)
>>"..\..\setup_log.txt" echo [%date% %time%] 应用程序发布成功

cd ..\..\    

echo 安装完成！可执行文件位于 bin 目录中。
>>"setup_log.txt" echo [%date% %time%] 安装完成，可执行文件位于 bin 目录中

echo.
echo 执行完成，请按任意键退出...
pause