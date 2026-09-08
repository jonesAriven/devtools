#!/usr/bin/env python3
import subprocess
result = subprocess.run(['ssh', 'root@192.168.31.31.182', 'cat', '/mnt/shared/woodScript/cd/deploy-kb-ops-web.sh'], capture_output=True, text=True)
print(result.stdout)
print(result.stderr)
