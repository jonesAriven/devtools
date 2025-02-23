#!/bin/bash
echo "正在检查并安装依赖..."
pip install -r requirements.txt
echo "依赖安装完成！"
echo "正在启动程序..."
python qr_generator.py 