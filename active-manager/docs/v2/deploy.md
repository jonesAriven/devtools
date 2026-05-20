# Docker 部署方案

## 1. 环境信息

| 项目 | 值 |
|------|------|
| 目标主机 | 192.168.31.182 |
| SSH用户 | root01 |
| SSH密码 | root01 |
| SSH命令 | `ssh root01@192.168.31.182` |
| MySQL | 192.168.31.182:3306 (库: tools, 用户: tools, 密码: toolsmarschat) |
| 容器端口映射 | 宿主机 18080 → 容器 8080 |
| 访问地址 | http://192.168.31.182:18080/activecode/login.html |

## 2. 首次部署

### 步骤1：本地打包JAR

```bash
cd D:\huliang\java\ideaworkspace\jonesDevtools\active-manager\activation-code-server
mvn clean package -DskipTests
```

产物：`target/activation-code-server-1.0.0.jar`

### 步骤2：上传文件到远程主机

将JAR包和Dockerfile上传到 root01 家目录：

```bash
scp target/activation-code-server-1.0.0.jar root01@192.168.31.182:~/
scp Dockerfile root01@192.168.31.182:~/
```

### 步骤3：SSH到远程主机构建镜像

```bash
ssh root01@192.168.31.182
cd ~ && docker build -t activecode .
```

### 步骤4：启动容器

```bash
docker run -d --name activecode -p 18080:8080 --restart unless-stopped activecode
```

### 步骤5：验证

```bash
# 查看容器状态
docker ps | grep activecode

# 查看启动日志
docker logs activecode
```

浏览器访问：http://192.168.31.182:18080/activecode/login.html

## 3. 重新部署（更新版本）

### 步骤1：本地重新打包

```bash
cd D:\huliang\java\ideaworkspace\jonesDevtools\active-manager\activation-code-server
mvn clean package -DskipTests
```

### 步骤2：上传新文件

```bash
scp target/activation-code-server-1.0.0.jar root01@192.168.31.182:~/
scp Dockerfile root01@192.168.31.182:~/
```

### 步骤3：停止并删除旧容器，重新构建和启动

```bash
ssh root01@192.168.31.182

# 停止并删除旧容器
docker stop activecode && docker rm activecode

# 删除旧镜像（可选，不删也会被新镜像替代）
docker rmi activecode

# 重新构建镜像
cd ~ && docker build -t activecode .

# 启动新容器
docker run -d --name activecode -p 18080:8080 --restart unless-stopped activecode
```

### 步骤4：验证

```bash
docker ps | grep activecode
docker logs activecode
```

## 4. Dockerfile 内容

```dockerfile
# 用 DaoCloud 镜像源，无需登录
FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-jammy
WORKDIR /activation-code
COPY activation-code-server-1.0.0.jar activation-code-server-1.0.0.jar
EXPOSE 8080

# 数据库连接：容器内通过host.docker.internal访问宿主机MySQL
ENV SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/tools?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
ENV SPRING_DATASOURCE_USERNAME=tools
ENV SPRING_DATASOURCE_PASSWORD=toolsmarschat

ENTRYPOINT ["java", "-jar", "activation-code-server-1.0.0.jar"]
```

## 5. 关键说明

1. **数据库连接**：`application.yml` 中数据库地址是 `192.168.31.182:3306`，Docker容器内此地址不可达。通过 `ENV SPRING_DATASOURCE_URL` 覆盖为 `host.docker.internal:3306`，让容器访问宿主机上的MySQL。Spring Boot会自动用环境变量覆盖yml配置。
2. **端口映射**：宿主机 `18080` → 容器 `8080`，避免与宿主机上其他服务冲突。
3. **`--restart unless-stopped`**：主机重启后容器自动恢复运行。
4. **公网访问**：如需公网访问，需在云服务器安全组中放行18080端口，并配置Nginx反向代理。

## 6. 常用运维命令

```bash
# 查看容器状态
docker ps | grep activecode

# 查看实时日志
docker logs -f activecode

# 进入容器内部
docker exec -it activecode bash

# 重启容器
docker restart activecode

# 停止容器
docker stop activecode

# 删除容器
docker rm -f activecode
```
