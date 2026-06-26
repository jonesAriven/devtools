#!/bin/bash
# 配置Docker镜像加速器 - 使用私服
mkdir -p /etc/docker
cat > /etc/docker/daemon.json << 'EOF'
{
  "registry-mirrors": [
    "https://nexus.marschat.online/repository/docker-public"
  ],
  "max-concurrent-downloads": 1,
  "max-download-attempts": 10
}
EOF
systemctl restart docker
sleep 3
echo "=== Docker mirror configured ==="
docker info 2>/dev/null | grep -A3 "Registry Mirrors"

# 拉取基础镜像（增加超时和重试）
echo "=== Pulling eclipse-temurin:21-jre-alpine ==="
for i in 1 2 3; do
    echo "Attempt $i..."
    docker pull eclipse-temurin:21-jre-alpine 2>&1
    if [ $? -eq 0 ]; then
        echo "=== PULL_SUCCESS ==="
        break
    fi
    echo "Attempt $i failed, waiting 5s..."
    sleep 5
done

docker images | grep eclipse-temurin
