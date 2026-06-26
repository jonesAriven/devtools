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
cd D:\huliang\java\ideaworkspace\devtools\active-manager\activation-code-server
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
cd D:\huliang\java\ideaworkspace\devtools\active-manager\activation-code-server
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

## 5. RSA 密钥管理

### 5.1 密钥文件位置

RSA 密钥对用于激活码的签名和验签，是整个系统的核心安全资产。

| 位置 | 路径 | 说明 |
|------|------|------|
| 项目根目录（外部） | `rsa_keys/private_key.pem`, `rsa_keys/public_key.pem` | 开发时使用，不在 JAR 内 |
| resources 目录（内置） | `src/main/resources/rsa_keys/private_key.pem`, `src/main/resources/rsa_keys/public_key.pem` | 打包进 JAR，部署时使用 |

### 5.2 密钥加载优先级（RsaKeyConfig）

```
1. classpath（JAR 内的 BOOT-INF/classes/rsa_keys/）  ← 优先
2. 文件系统（相对路径 rsa_keys/）                      ← 回退
3. 自动生成新密钥对                                    ← 都找不到时
```

### 5.3 两种部署方案

#### 方案A：密钥内置到 JAR（当前采用）

将密钥文件放在 `src/main/resources/rsa_keys/` 下，Maven 打包时自动包含进 JAR。

**优点**：
- 部署简单，只需一个 JAR 文件即可运行
- Docker 镜像构建无需额外挂载

**缺点**：
- 密钥固化在 JAR 中，更换密钥需重新打包
- JAR 泄露即密钥泄露

**操作**：将 `rsa_keys/` 下的 pem 文件复制到 `src/main/resources/rsa_keys/`，然后 `mvn clean package -DskipTests`。

验证密钥是否包含：
```bash
jar tf target/activation-code-server-1.0.0.jar | findstr pem
# 应输出：
# BOOT-INF/classes/rsa_keys/private_key.pem
# BOOT-INF/classes/rsa_keys/public_key.pem
```

#### 方案B：密钥外部挂载（生产推荐）

密钥不打包进 JAR，通过 Docker 卷挂载或环境变量指定路径。

**优点**：
- 密钥与代码分离，安全性更高
- 更换密钥无需重新打包，只需替换文件重启容器
- 符合 12-Factor 配置外置原则

**缺点**：
- 部署时需额外管理密钥文件
- Docker 启动命令稍复杂

**Docker 部署示例**：

```bash
# 在宿主机创建密钥目录
mkdir -p /opt/activecode/rsa_keys

# 上传密钥文件
scp rsa_keys/private_key.pem root01@192.168.31.182:/opt/activecode/rsa_keys/
scp rsa_keys/public_key.pem root01@192.168.31.182:/opt/activecode/rsa_keys/

# 启动容器时挂载密钥目录
docker run -d --name activecode \
  -p 18080:8080 \
  -v /opt/activecode/rsa_keys:/activation-code/rsa_keys \
  -e ACTIVATION_RSA_PRIVATE_KEY_PATH=/activation-code/rsa_keys/private_key.pem \
  -e ACTIVATION_RSA_PUBLIC_KEY_PATH=/activation-code/rsa_keys/public_key.pem \
  --restart unless-stopped activecode
```

> **注意**：采用方案B时，需在 `application.yml` 中将密钥路径配置为外部绝对路径，或通过环境变量覆盖。

### 5.4 密钥一致性要求

- **服务端私钥**和**客户端公钥**必须是同一对密钥，否则激活码签名验证会失败
- 客户端（C++/C# verifier）内嵌的公钥 PEM 必须与服务端 `public_key.pem` 完全一致
- 更换密钥后，数据库中所有旧的激活码将失效，需重新生成

## 6. 关键说明

1. **数据库连接**：`application.yml` 中数据库地址是 `192.168.31.182:3306`，Docker容器内此地址不可达。通过 `ENV SPRING_DATASOURCE_URL` 覆盖为 `host.docker.internal:3306`，让容器访问宿主机上的MySQL。Spring Boot会自动用环境变量覆盖yml配置。
2. **端口映射**：宿主机 `18080` → 容器 `8080`，避免与宿主机上其他服务冲突。
3. **`--restart unless-stopped`**：主机重启后容器自动恢复运行。
4. **公网访问**：如需公网访问，需在云服务器安全组中放行18080端口，并配置Nginx反向代理。

## 7. 问题排查记录

### 7.1 激活码签名验证失败（密钥不一致）

**现象**：客户端输入激活码后提示"激活码无效"，服务端日志显示 `激活码签名验证失败`。

**根因**：数据库中的激活码是用旧密钥生成的，而当前服务端加载了不同的密钥对。

**排查过程**：
1. 检查服务端日志：`WARN com.jones.activation.util.CryptoUtil - 激活码签名验证失败`
2. 确认 `RsaKeyConfig` 的密钥加载逻辑：优先 classpath → 回退文件系统 → 自动生成新密钥
3. 发现 JAR 包内没有 pem 文件（`rsa_keys/` 在项目根目录，不在 `src/main/resources/`）
4. 当 JAR 内无密钥且文件系统路径也找不到时，`RsaKeyConfig` 会自动生成新密钥对
5. 新密钥与数据库中旧激活码使用的密钥不同，导致签名验证失败

**解决方案**：
- 将密钥文件复制到 `src/main/resources/rsa_keys/`，确保打包进 JAR
- 清除数据库中用旧密钥生成的激活码记录
- 用当前密钥重新生成激活码

### 7.2 Windows 下 Socket operation on nonsocket 错误

**现象**：`java -jar` 启动服务时报 `java.net.SocketException: Socket operation on nonsocket: socket`，Tomcat 无法绑定任何端口（8080、8081 均失败）。

**根因**：Windows Winsock 网络栈损坏，导致 JDK 无法创建 ServerSocket。

**解决方案**：
1. 以管理员身份打开 CMD，执行：`netsh winsock reset`
2. 重启电脑
3. 重新启动服务

### 7.3 JDK 版本混淆

**现象**：构建或运行时使用了错误的 JDK 版本（如 JDK 25 而非 JDK 21）。

**规范**：
- 统一使用 JDK 21（路径：`D:\huliang\software\Java\jdk-21.0.11`）
- 禁止使用同目录下的 `jdk-25`
- 已在 `.trae/rules/project_rules.md` 中明确标注

## 8. 常用运维命令

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
