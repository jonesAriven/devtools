#!/bin/bash

# Maven私服安装脚本 - 基于Nexus Repository Manager
# 此脚本用于在Linux服务器上安装和配置Nexus Repository Manager作为Maven私服

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志文件
LOG_DIR="./logs"
LOG_FILE="$LOG_DIR/install_$(date +%Y%m%d%H%M%S).log"

# 创建日志目录
mkdir -p "$LOG_DIR"

# 记录日志的函数
log() {
    local message="$1"
    local timestamp=$(date +"%Y-%m-%d %H:%M:%S")
    echo -e "${timestamp} - ${message}" | tee -a "$LOG_FILE"
}

# 检查命令是否存在
check_command() {
    command -v "$1" >/dev/null 2>&1
}

# 检查系统要求
check_requirements() {
    log "${BLUE}检查系统要求...${NC}"
    
    # 检查Java
    if check_command java; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1-2)
        log "${GREEN}检测到Java版本: $JAVA_VERSION${NC}"
        
        # 检查Java版本是否满足要求（Nexus 3.x需要Java 8或更高版本）
        if [[ "$JAVA_VERSION" < "1.8" ]]; then
            log "${RED}错误: Java版本过低，Nexus需要Java 8或更高版本${NC}"
            log "${YELLOW}请安装Java 8或更高版本后重试${NC}"
            exit 1
        fi
    else
        log "${RED}错误: 未检测到Java${NC}"
        log "${YELLOW}请安装Java 8或更高版本后重试${NC}"
        log "${YELLOW}可以使用以下命令安装OpenJDK 8:${NC}"
        log "${YELLOW}  - Debian/Ubuntu: sudo apt-get install openjdk-8-jdk${NC}"
        log "${YELLOW}  - CentOS/RHEL: sudo yum install java-1.8.0-openjdk${NC}"
        exit 1
    fi
    
    # 检查内存
    TOTAL_MEM=$(free -m | grep Mem | awk '{print $2}')
    log "${BLUE}系统总内存: ${TOTAL_MEM}MB${NC}"
    
    if [ "$TOTAL_MEM" -lt 2048 ]; then
        log "${YELLOW}警告: 系统内存小于2GB，Nexus可能运行缓慢${NC}"
        log "${YELLOW}建议至少分配2GB内存给Nexus服务器${NC}"
        read -p "是否继续安装? (y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log "${RED}安装已取消${NC}"
            exit 1
        fi
    fi
    
    # 检查磁盘空间
    DISK_SPACE=$(df -h . | tail -n 1 | awk '{print $4}' | sed 's/G//')
    log "${BLUE}当前目录可用磁盘空间: ${DISK_SPACE}GB${NC}"
    
    if (( $(echo "$DISK_SPACE < 10" | bc -l) )); then
        log "${YELLOW}警告: 可用磁盘空间小于10GB${NC}"
        log "${YELLOW}建议至少分配10GB空间用于存储Maven构件${NC}"
        read -p "是否继续安装? (y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log "${RED}安装已取消${NC}"
            exit 1
        fi
    fi
    
    log "${GREEN}系统检查完成${NC}"
}

# 下载Nexus
download_nexus() {
    log "${BLUE}开始下载Nexus...${NC}"
    
    NEXUS_VERSION="3.59.0-01"
    NEXUS_ARCHIVE="nexus-$NEXUS_VERSION-unix.tar.gz"
    
    # 定义多个下载源，按优先级排序
    DOWNLOAD_URLS=(
        "https://download.sonatype.com/nexus/3/$NEXUS_ARCHIVE"
        "https://sonatype-download.global.ssl.fastly.net/nexus/3/$NEXUS_ARCHIVE"
        "https://repo1.maven.org/maven2/org/sonatype/nexus/nexus-professional/$NEXUS_VERSION/$NEXUS_ARCHIVE"
    )
    
    if [ -f "$NEXUS_ARCHIVE" ]; then
        log "${YELLOW}检测到已下载的Nexus安装包，跳过下载${NC}"
    else
        log "${BLUE}下载Nexus $NEXUS_VERSION...${NC}"
        
        # 尝试从多个源下载，直到成功
        download_success=false
        for url in "${DOWNLOAD_URLS[@]}"; do
            log "${BLUE}尝试从 $url 下载...${NC}"
            wget --tries=3 --timeout=30 "$url" -O "$NEXUS_ARCHIVE"
            
            if [ $? -eq 0 ]; then
                download_success=true
                log "${GREEN}从 $url 下载成功${NC}"
                break
            else
                log "${YELLOW}从 $url 下载失败，尝试下一个源...${NC}"
            fi
        done
        
        if [ "$download_success" = false ]; then
            log "${RED}所有下载源均下载失败${NC}"
            log "${YELLOW}请手动下载Nexus安装包并放置在当前目录:${NC}"
            log "${YELLOW}  - 官方下载地址: https://www.sonatype.com/products/repository-oss-download${NC}"
            exit 1
        fi
        
        log "${GREEN}Nexus下载完成${NC}"
    fi
}

# 安装Nexus
install_nexus() {
    log "${BLUE}开始安装Nexus...${NC}"
    
    # 解压Nexus
    log "${BLUE}解压Nexus...${NC}"
    tar -xzf "$NEXUS_ARCHIVE"
    
    if [ $? -ne 0 ]; then
        log "${RED}解压Nexus失败${NC}"
        exit 1
    fi
    
    # 重命名目录
    NEXUS_DIR=$(ls -d nexus-* | grep -v tar.gz)
    SONATYPE_DIR="sonatype-work"
    
    # 创建安装目录
    INSTALL_DIR="./nexus-repository"
    mkdir -p "$INSTALL_DIR"
    
    # 移动文件到安装目录
    mv "$NEXUS_DIR" "$INSTALL_DIR/nexus"
    mv "$SONATYPE_DIR" "$INSTALL_DIR/"
    
    log "${GREEN}Nexus安装完成${NC}"
    
    # 配置Nexus
    configure_nexus
}

# 配置Nexus
configure_nexus() {
    log "${BLUE}配置Nexus...${NC}"
    
    NEXUS_HOME="$INSTALL_DIR/nexus"
    
    # 修改JVM内存设置
    MEMORY_SETTINGS="-Xms512m -Xmx1024m"
    CONFIG_FILE="$NEXUS_HOME/bin/nexus.vmoptions"
    
    # 备份原始配置
    cp "$CONFIG_FILE" "${CONFIG_FILE}.bak"
    
    # 更新内存设置
    sed -i "s/-Xms.*/-Xms512m/g" "$CONFIG_FILE"
    sed -i "s/-Xmx.*/-Xmx1024m/g" "$CONFIG_FILE"
    
    # 设置运行用户
    NEXUS_RC="$NEXUS_HOME/bin/nexus.rc"
    echo 'run_as_user="root"' > "$NEXUS_RC"
    
    log "${GREEN}Nexus配置完成${NC}"
}

# 创建启动脚本
create_service_script() {
    log "${BLUE}创建服务管理脚本...${NC}"
    
    # 创建服务控制脚本
    cat > "./nexus-service.sh" << 'EOF'
#!/bin/bash

# Nexus服务控制脚本

NEXUS_HOME="./nexus-repository/nexus"
ACTION=$1

start_nexus() {
    echo "启动Nexus服务..."
    "$NEXUS_HOME/bin/nexus" start
    
    # 等待服务启动
    echo "等待Nexus服务启动..."
    for i in {1..30}; do
        sleep 5
        if curl -s http://localhost:8081 > /dev/null; then
            echo "Nexus服务已启动，可通过 http://localhost:8081 访问"
            return 0
        fi
        echo -n "."
    done
    
    echo "\nNexus服务启动超时，请检查日志"
    return 1
}

stop_nexus() {
    echo "停止Nexus服务..."
    "$NEXUS_HOME/bin/nexus" stop
    
    # 等待服务停止
    for i in {1..12}; do
        sleep 5
        if ! curl -s http://localhost:8081 > /dev/null; then
            echo "Nexus服务已停止"
            return 0
        fi
        echo -n "."
    done
    
    echo "\nNexus服务停止超时，可能需要手动终止进程"
    return 1
}

restart_nexus() {
    stop_nexus
    start_nexus
}

status_nexus() {
    "$NEXUS_HOME/bin/nexus" status
    
    # 检查Web界面是否可访问
    if curl -s http://localhost:8081 > /dev/null; then
        echo "Web界面可访问: http://localhost:8081"
    else
        echo "Web界面不可访问"
    fi
}

case "$ACTION" in
    start)
        start_nexus
        ;;
    stop)
        stop_nexus
        ;;
    restart)
        restart_nexus
        ;;
    status)
        status_nexus
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac
EOF
    
    # 添加执行权限
    chmod +x "./nexus-service.sh"
    
    log "${GREEN}服务管理脚本创建完成${NC}"
}

# 主函数
main() {
    log "${BLUE}===== Maven私服安装程序 - 基于Nexus Repository Manager =====${NC}"
    log "${BLUE}开始时间: $(date)"
    
    # 检查系统要求
    check_requirements
    
    # 下载Nexus
    download_nexus
    
    # 安装Nexus
    install_nexus
    
    # 创建服务脚本
    create_service_script
    
    log "${GREEN}===== 安装完成 =====${NC}"
    log "${GREEN}可以使用以下命令启动Nexus服务:${NC}"
    log "${YELLOW}  ./nexus-service.sh start${NC}"
    log "${GREEN}安装完成后，请访问:${NC}"
    log "${YELLOW}  http://localhost:8081${NC}"
    log "${GREEN}默认管理员账号:${NC}"
    log "${YELLOW}  用户名: admin${NC}"
    log "${YELLOW}  密码: 首次登录时会提示在服务器上查看初始密码${NC}"
    log "${YELLOW}  初始密码位置: ./nexus-repository/sonatype-work/nexus3/admin.password${NC}"
    log "${BLUE}结束时间: $(date)${NC}"
}

# 执行主函数
main