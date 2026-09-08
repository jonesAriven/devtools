import subprocess
result = subprocess.run(['cmd', '/c', 'type', 'D:\\huliang\\java\\ideaworkspace\\devtools\\do_cmd.py'], capture_output=True, text=True, encoding='gbk')
print(result.stdout.decode('gbk'))
print(result.stderr.decode('gbk'))
