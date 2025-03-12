#!/bin/bash

# Maven私服配置脚本 - 基于Nexus Repository Manager
# 此脚本用于配置Nexus Repository Manager作为Maven私服，包括设置代理仓库指向中央库

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志文件
LOG_DIR="./logs"
LOG_FILE="$LOG_DIR/configure_$(date +%Y%m%d%H%M%S).log"

# 创建日志目录
mkdir -p "$LOG_DIR"

# 记录日志的函数
log() {
    local message="$1"
    local timestamp=$(date +"%Y-%m-%d %H:%M:%S")
    echo -e "${timestamp} - ${message}" | tee -a "$LOG_FILE"
}

# 检查Nexus是否已安装
check_nexus_installed() {
    if [ ! -d "./nexus-repository/nexus" ]; then
        log "${RED}错误: Nexus未安装，请先运行install_nexus.sh安装Nexus${NC}"
        exit 1
    fi
}

# 检查Nexus是否正在运行
check_nexus_running() {
    if ! curl -s http://localhost:8081 > /dev/null; then
        log "${YELLOW}警告: Nexus服务未运行${NC}"
        log "${BLUE}正在启动Nexus服务...${NC}"
        ./nexus-service.sh start
        
        # 等待服务启动
        log "${BLUE}等待Nexus服务启动...${NC}"
        for i in {1..30}; do
            sleep 5
            if curl -s http://localhost:8081 > /dev/null; then
                log "${GREEN}Nexus服务已启动${NC}"
                break
            fi
            echo -n "."
            if [ $i -eq 30 ]; then
                log "${RED}Nexus服务启动超时，请检查日志${NC}"
                exit 1
            fi
        done
    else
        log "${GREEN}Nexus服务已在运行${NC}"
    fi
}

# 获取管理员密码
get_admin_password() {
    log "${BLUE}获取管理员密码...${NC}"
    
    PASSWORD_FILE="./nexus-repository/sonatype-work/nexus3/admin.password"
    if [ -f "$PASSWORD_FILE" ]; then
        ADMIN_PASSWORD=$(cat "$PASSWORD_FILE")
        log "${GREEN}已获取管理员初始密码${NC}"
    else
        log "${YELLOW}未找到初始密码文件，可能已经完成初始化${NC}"
        read -p "请输入管理员密码: " -s ADMIN_PASSWORD
        echo
    fi
}

# 使用curl发送REST API请求
send_api_request() {
    local method=$1
    local endpoint=$2
    local data=$3
    local content_type="application/json"
    
    # 构建认证信息
    local auth="admin:$ADMIN_PASSWORD"
    
    # 发送请求
    if [ -z "$data" ]; then
        curl -s -X $method -H "Content-Type: $content_type" -u "$auth" "http://localhost:8081/service/rest/$endpoint"
    else
        curl -s -X $method -H "Content-Type: $content_type" -u "$auth" -d "$data" "http://localhost:8081/service/rest/$endpoint"
    fi
}

# 配置Maven中央仓库代理
configure_maven_central_proxy() {
    log "${BLUE}配置Maven中央仓库代理...${NC}"
    
    # 检查仓库是否已存在
    local repo_check=$(send_api_request "GET" "v1/repositories/maven-central")
    if [[ $repo_check == *"name\":\"maven-central"* ]]; then
        log "${YELLOW}Maven中央仓库代理已存在，跳过配置${NC}"
        return 0
    fi
    
    # 创建Maven中央仓库代理
    local repo_data='
    {
      "name": "maven-central",
      "online": true,
      "storage": {
        "blobStoreName": "default",
        "strictContentTypeValidation": true
      },
      "proxy": {
        "remoteUrl": "https://repo1.maven.org/maven2/",
        "contentMaxAge": 1440,
        "metadataMaxAge": 1440
      },
      "negativeCache": {
        "enabled": true,
        "timeToLive": 1440
      },
      "httpClient": {
        "blocked": false,
        "autoBlock": true,
        "connection": {
          "retries": 3,
          "userAgentSuffix": "Nexus/3.x",
          "timeout": 60,
          "enableCircularRedirects": false,
          "enableCookies": false
        }
      },
      "routingRule": null,
      "maven": {
        "versionPolicy": "RELEASE",
        "layoutPolicy": "PERMISSIVE"
      }
    }'
    
    send_api_request "POST" "v1/repositories/maven/proxy" "$repo_data"
    
    if [ $? -eq 0 ]; then
        log "${GREEN}Maven中央仓库代理配置成功${NC}"
    else
        log "${RED}Maven中央仓库代理配置失败${NC}"
    fi
}

# 配置Aliyun Maven仓库代理（国内加速）
configure_aliyun_proxy() {
    log "${BLUE}配置Aliyun Maven仓库代理（国内加速）...${NC}"
    
    # 检查仓库是否已存在
    local repo_check=$(send_api_request "GET" "v1/repositories/aliyun-maven")
    if [[ $repo_check == *"name\":\"aliyun-maven"* ]]; then
        log "${YELLOW}Aliyun Maven仓库代理已存在，跳过配置${NC}"
        return 0
    fi
    
    # 创建Aliyun Maven仓库代理
    local repo_data='
    {
      "name": "aliyun-maven",
      "online": true,
      "storage": {
        "blobStoreName": "default",
        "strictContentTypeValidation": true
      },
      "proxy": {
        "remoteUrl": "https://maven.aliyun.com/repository/public/",
        "contentMaxAge": 1440,
        "metadataMaxAge": 1440
      },
      "negativeCache": {
        "enabled": true,
        "timeToLive": 1440
      },
      "httpClient": {
        "blocked": false,
        "autoBlock": true,
        "connection": {
          "retries": 3,
          "userAgentSuffix": "Nexus/3.x",
          "timeout": 60,
          "enableCircularRedirects": false,
          "enableCookies": false
        }
      },
      "routingRule": null,
      "maven": {
        "versionPolicy": "RELEASE",
        "layoutPolicy": "PERMISSIVE"
      }
    }'
    
    send_api_request "POST" "v1/repositories/maven/proxy" "$repo_data"
    
    if [ $? -eq 0 ]; then
        log "${GREEN}Aliyun Maven仓库代理配置成功${NC}"
    else
        log "${RED}Aliyun Maven仓库代理配置失败${NC}"
    fi
}

# 创建Maven仓库组
create_maven_group() {
    log "${BLUE}创建Maven仓库组...${NC}"
    
    # 检查仓库组是否已存在
    local repo_check=$(send_api_request "GET" "v1/repositories/maven-public")
    if [[ $repo_check == *"name\":\"maven-public"* ]]; then
        log "${YELLOW}Maven仓库组已存在，跳过创建${NC}"
        return 0
    fi
    
    # 创建Maven仓库组
    local repo_data='
    {
      "name": "maven-public",
      "online": true,
      "storage": {
        "blobStoreName": "default",
        "strictContentTypeValidation": true
      },
      "group": {
        "memberNames": ["aliyun-maven", "maven-central", "maven-releases", "maven-snapshots"]
      }
    }'
    
    send_api_request "POST" "v1/repositories/maven/group" "$repo_data"
    
    if [ $? -eq 0 ]; then
        log "${GREEN}Maven仓库组创建成功${NC}"
    else
        log "${RED}Maven仓库组创建失败${NC}"
    fi
}

# 创建Maven设置文件示例
create_maven_settings_example() {
    log "${BLUE}创建Maven设置文件示例...${NC}"
    
    # 创建示例目录
    mkdir -p "./examples"
    
    # 创建settings.xml示例文件
    cat > "./examples/settings.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <!-- 配置私服认证信息 -->
  <servers>
    <server>
      <id>nexus-releases</id>
      <username>admin</username>
      <password>your_password_here</password>
    </server>
    <server>
      <id>nexus-snapshots</id>
      <username>admin</username>
      <password>your_password_here</password>
    </server>
  </servers>

  <!-- 配置镜像，使所有Maven仓库请求都指向私服 -->
  <mirrors>
    <mirror>
      <id>nexus</id>
      <mirrorOf>*</mirrorOf>
      <url>http://your_server_ip:8081/repository/maven-public/</url>
    </mirror>
  </mirrors>

  <!-- 配置Maven默认使用的仓库 -->
  <profiles>
    <profile>
      <id>nexus</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>http://central</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>central</id>
          <url>http://central</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>

  <activeProfiles>
    <activeProfile>nexus</activeProfile>
  </activeProfiles>

</settings>
EOF
    
    log "${GREEN}Maven设置文件示例已创建: ./examples/settings.xml${NC}"
    log "${YELLOW}请根据实际情况修改服务器IP地址和认证信息${NC}"
}

# 主函数
main() {
    log "${BLUE}===== Maven私服配置程序 - 基于Nexus Repository Manager =====${NC}"
    log "${BLUE}开始时间: $(date)${NC}"
    
    # 检查Nexus是否已安装
    check_nexus_installed
    
    # 检查Nexus是否正在运行
    check_nexus_running
    
    # 获取管理员密码
    get_admin_password
    
    # 配置Maven中央仓库代理
    configure_maven_central_proxy
    
    # 配置Aliyun Maven仓库代理（国内加速）
    configure_aliyun_proxy
    
    # 创建Maven仓库组
    create_maven_group
    
    # 创建Maven设置文件示例
    create_maven_settings_example
    
    log "${GREEN}===== 配置完成 =====${NC}"
    log "${GREEN}Maven私服已配置完成，可通过以下地址访问:${NC}"
    log "${YELLOW}  http://localhost:8081${NC}"
    log "${GREEN}Maven仓库地址:${NC}"
    log "${YELLOW}  http://localhost:8081/repository/maven-public/${NC}"
    log "${GREEN}请参考 ./examples/settings.xml 配置您的Maven客户端${NC}"
    log "${BLUE}结束时间: $(date)${NC}"
}

# 执行主函数
main