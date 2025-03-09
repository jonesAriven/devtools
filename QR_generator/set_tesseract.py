import pytesseract 
pytesseract.pytesseract.tesseract_cmd = r'D:\huliang\softWare\Tesseract-OCR\tesseract.exe' 
print('Tesseract 路径:', pytesseract.get_tesseract_cmd()) 
