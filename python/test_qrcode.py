import qrcode
img = qrcode.make('Hello from your pip server!')
img.save('test_qrcode.png')
print('QR code generated successfully!')