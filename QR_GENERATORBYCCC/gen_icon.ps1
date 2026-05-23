param([string]$OutputPath = "res/app.ico")

Add-Type -AssemblyName System.Drawing

function New-QrBitmap([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

    $m = $size / 16.0

    $g.Clear([System.Drawing.Color]::White)

    $black = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 40, 40, 40))
    $blue = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 41, 98, 255))
    $white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)

    # Top-left finder pattern (7x7)
    $g.FillRectangle($black, 0 * $m, 0 * $m, 7 * $m, 7 * $m)
    $g.FillRectangle($white, 1 * $m, 1 * $m, 5 * $m, 5 * $m)
    $g.FillRectangle($black, 2 * $m, 2 * $m, 3 * $m, 3 * $m)

    # Top-right finder pattern (7x7)
    $g.FillRectangle($black, 9 * $m, 0 * $m, 7 * $m, 7 * $m)
    $g.FillRectangle($white, 10 * $m, 1 * $m, 5 * $m, 5 * $m)
    $g.FillRectangle($black, 11 * $m, 2 * $m, 3 * $m, 3 * $m)

    # Bottom-left finder pattern (7x7)
    $g.FillRectangle($black, 0 * $m, 9 * $m, 7 * $m, 7 * $m)
    $g.FillRectangle($white, 1 * $m, 10 * $m, 5 * $m, 5 * $m)
    $g.FillRectangle($black, 2 * $m, 11 * $m, 3 * $m, 3 * $m)

    # Timing patterns
    $g.FillRectangle($black, 8 * $m, 6 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($black, 6 * $m, 8 * $m, 1 * $m, 1 * $m)

    # Blue data modules
    $g.FillRectangle($blue, 8 * $m, 0 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 9 * $m, 8 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 11 * $m, 8 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 8 * $m, 9 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 10 * $m, 9 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 9 * $m, 10 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 12 * $m, 10 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 8 * $m, 11 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 10 * $m, 12 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 12 * $m, 12 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 9 * $m, 13 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 11 * $m, 13 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 13 * $m, 13 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 8 * $m, 14 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 10 * $m, 14 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 12 * $m, 14 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 14 * $m, 14 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 9 * $m, 15 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 11 * $m, 15 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($blue, 13 * $m, 15 * $m, 1 * $m, 1 * $m)

    # Separators
    $g.FillRectangle($white, 7 * $m, 0 * $m, 1 * $m, 8 * $m)
    $g.FillRectangle($white, 0 * $m, 7 * $m, 8 * $m, 1 * $m)
    $g.FillRectangle($white, 7 * $m, 7 * $m, 1 * $m, 1 * $m)
    $g.FillRectangle($white, 8 * $m, 7 * $m, 7 * $m, 1 * $m)
    $g.FillRectangle($white, 7 * $m, 8 * $m, 1 * $m, 7 * $m)

    $black.Dispose()
    $blue.Dispose()
    $white.Dispose()
    $g.Dispose()
    return $bmp
}

# Helper: write uint16 little-endian to byte array
function Set-UInt16([byte[]]$buf, [int]$offset, [int]$val) {
    $buf[$offset] = [byte]($val -band 0xFF)
    $buf[$offset + 1] = [byte](($val -shr 8) -band 0xFF)
}

# Helper: write uint32 little-endian to byte array
function Set-UInt32([byte[]]$buf, [int]$offset, [long]$val) {
    $buf[$offset] = [byte]($val -band 0xFF)
    $buf[$offset + 1] = [byte](($val -shr 8) -band 0xFF)
    $buf[$offset + 2] = [byte](($val -shr 16) -band 0xFF)
    $buf[$offset + 3] = [byte](($val -shr 24) -band 0xFF)
}

# Generate PNG data for each size
$sizes = @(16, 32, 48, 256)
$pngDataList = @()

foreach ($sz in $sizes) {
    $bmp = New-QrBitmap $sz
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngDataList += ,$ms.ToArray()
    $bmp.Dispose()
    $ms.Dispose()
}

# Ensure output directory exists
$outDir = [System.IO.Path]::GetDirectoryName($OutputPath)
if ($outDir -and !(Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

# Build .ico file header as byte array (avoid BinaryWriter type issues)
$numImages = $sizes.Count
$headerSize = 6 + 16 * $numImages
$header = New-Object byte[] $headerSize

# ICONDIR (6 bytes)
Set-UInt16 $header 0 0          # Reserved
Set-UInt16 $header 2 1          # Type = icon
Set-UInt16 $header 4 $numImages # Count

# Calculate offsets for each image
$offset = [long]$headerSize
for ($i = 0; $i -lt $numImages; $i++) {
    $entryOffset = 6 + 16 * $i
    $w = if ($sizes[$i] -ge 256) { [byte]0 } else { [byte]$sizes[$i] }
    $h = $w

    $header[$entryOffset + 0] = $w                                    # Width
    $header[$entryOffset + 1] = $h                                    # Height
    $header[$entryOffset + 2] = 0                                     # ColorCount
    $header[$entryOffset + 3] = 0                                     # Reserved
    Set-UInt16 $header ($entryOffset + 4) 1                            # Planes
    Set-UInt16 $header ($entryOffset + 6) 32                           # BitCount
    Set-UInt32 $header ($entryOffset + 8) ([long]$pngDataList[$i].Length)  # BytesInRes
    Set-UInt32 $header ($entryOffset + 12) $offset                     # ImageOffset

    $offset += $pngDataList[$i].Length
}

# Write .ico file
$fs = [System.IO.File]::Create($OutputPath)
$fs.Write($header, 0, $header.Length)
foreach ($pd in $pngDataList) {
    $fs.Write($pd, 0, $pd.Length)
}
$fs.Close()

Write-Host "Generated $OutputPath with sizes: $($sizes -join ', ')"
