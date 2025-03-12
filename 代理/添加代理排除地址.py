#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import sys
import re
import subprocess
import ctypes

# 检查管理员权限
def is_admin():
    try:
        return ctypes.windll.shell32.IsUserAnAdmin()
    except:
        return False

# 请求管理员权限
def request_admin():
    if not is_admin():
        print("[警告] 需要管理员权限才能修改代理设置")
        print("[信息] 正在请求管理员权限...")
        
        # 重新以管理员身份运行此脚本
        ctypes.windll.shell32.ShellExecuteW(
            None, "runas", sys.executable, ' '.join(['"' + arg + '"' for arg in sys.argv]), None, 1
        )
        sys.exit(0)

# 获取当前代理绕过列表
def get_proxy_bypass_list():
    try:
        print("[信息] 正在获取当前代理配置...")
        result = subprocess.run(['netsh', 'winhttp', 'show', 'proxy'], 
                               capture_output=True, text=True, encoding='gbk')
        output = result.stdout
        
        print("\n[原始输出开始]\n{}[原始输出结束]\n".format(output))
        
        # 查找绕过列表
        bypass_match = re.search(r'绕过列表\s*:\s*(.*)$', output, re.MULTILINE)
        if bypass_match:
            current = bypass_match.group(1).strip()
            print(f"[信息] 当前绕过列表: '{current}'")
            return current
        
        print("[警告] 未找到有效的绕过列表，使用默认空值")
        return ""
    except Exception as e:
        print(f"[错误] 获取代理配置时发生异常: {str(e)}")
        sys.exit(1)

# 设置代理绕过列表
def set_proxy_bypass_list(bypass_list):
    try:
        print("\n[操作] 正在更新代理配置...")
        result = subprocess.run(['netsh', 'winhttp', 'set', 'proxy', f'bypass-list="{bypass_list}"'], 
                               capture_output=True, text=True, encoding='gbk')
        
        if result.returncode == 0:
            print("\n[成功] 代理排除地址已成功更新")
            return True
        else:
            print(f"\n[错误] 更新代理设置失败，错误信息:\n{result.stderr}")
            return False
    except Exception as e:
        print(f"[严重错误] 设置代理时发生异常: {str(e)}")
        return False

# 清理多余分号
def clean_bypass_list(bypass_list):
    # 替换连续的分号为单个分号
    cleaned = re.sub(r';+', ';', bypass_list)
    # 移除开头和结尾的分号
    cleaned = cleaned.strip(';')
    return cleaned

# 主函数
def main():
    # 检查并请求管理员权限
    request_admin()
    
    print("======================================================")
    print("              Windows 代理排除地址设置工具")
    print("======================================================")
    print("")
    print("此工具用于添加不经过代理服务器的IP地址或域名")
    print("注意: 此设置在系统重启后将会失效")
    print("")
    
    # 获取当前代理设置
    current_bypass = get_proxy_bypass_list()
    
    # 获取用户输入
    user_input = input("请输入要排除的IP或域名(多个请用分号;分隔): ")
    
    if not user_input.strip():
        print("[错误] 未输入任何内容，操作取消")
        input("\n按 Enter 退出...")
        return
    
    print("[信息] 正在处理输入...")
    
    # 处理新的绕过列表
    if not current_bypass:
        updated_bypass = user_input
    else:
        updated_bypass = f"{current_bypass};{user_input}"
    
    # 清理多余分号
    updated_bypass = clean_bypass_list(updated_bypass)
    
    print(f"[信息] 更新后的绕过列表: {updated_bypass}")
    
    # 设置新的代理绕过列表
    success = set_proxy_bypass_list(updated_bypass)
    
    if success:
        print(f"[信息] 以下地址已添加到排除列表: {user_input}")
        print("[提示] 此设置在系统重启后将会失效")
    
    # 暂停查看结果
    input("\n按 Enter 退出...")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[信息] 操作被用户取消")
    except Exception as e:
        print(f"\n[严重错误] 程序执行失败: {str(e)}")
        input("\n按 Enter 退出...")