import os
import subprocess
import sys
import shutil
import platform

def install_package(package_name, target=None):
    """统一的包安装函数"""
    try:
        # 尝试不同的镜像源
        mirrors = [
            'http://mirrors.aliyun.com/pypi/simple',
            'http://pypi.doubanio.com/simple',
            'http://mirrors.cloud.tencent.com/pypi/simple'
        ]
        
        for mirror in mirrors:
            try:
                host = mirror.split('/')[2]
                cmd = [
                    sys.executable,
                    '-m',
                    'pip',
                    'install',
                    '--index-url', mirror,
                    '--trusted-host', host,
                ]
                
                if target:
                    cmd.extend(['--target', target, '--no-deps'])
                
                cmd.append(package_name)
                
                subprocess.check_call(cmd)
                return True
            except Exception as e:
                print(f"使用 {mirror} 安装失败: {str(e)}")
                continue
        
        return False
    except Exception as e:
        print(f"安装 {package_name} 失败: {str(e)}")
        return False

def download_dependencies():
    # 创建lib目录
    if not os.path.exists('lib'):
        os.makedirs('lib')
    
    # 基本依赖列表（移除opencv-python）
    dependencies = [
        'qrcode',
        'pillow',
        'pyzbar',
        'keyboard',
        'numpy'
    ]
    
    print("开始安装依赖...")
    
    # 安装基本依赖
    for dep in dependencies:
        print(f"正在处理 {dep}...")
        if install_package(dep):
            install_package(dep, target='./lib')
    
    # 复制numpy的.libs目录（如果存在）
    try:
        import numpy
        numpy_path = os.path.dirname(numpy.__file__)
        libs_path = os.path.join(numpy_path, '.libs')
        if os.path.exists(libs_path):
            dst_libs = os.path.join('./lib', '.libs')
            if os.path.exists(dst_libs):
                shutil.rmtree(dst_libs)
            shutil.copytree(libs_path, dst_libs)
    except Exception as e:
        print(f"复制numpy .libs时出错: {str(e)}")
    
    print("依赖处理完成！")

if __name__ == "__main__":
    download_dependencies() 
    download_dependencies() 