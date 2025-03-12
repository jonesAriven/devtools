#!/bin/bash

# Maven私服缓存管理脚本
# 此脚本用于管理Nexus Repository Manager的缓存，包括删除和备份功能

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志文件
LOG_DIR="./logs"
LOG_FILE="$LOG_DIR/cache_$(date +%Y%m%d%H%M%S).log"

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
        log "${YELLOW}警告: Nexus服务未运行，某些操作可能无法完成${NC}"
        read -p "是否启动Nexus服务? (y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            log "${BLUE}正在启动Nexus服务...${NC}"
            ./nexus-service.sh start
        else
            log "${YELLOW}继续操作，但某些功能可能受限${NC}"
        fi
    fi
}

# 备份缓存
backup_cache() {
    log "${BLUE}开始备份Maven缓存...${NC}"
    
    # 设置备份目录和文件名
    BACKUP_DIR="./backups"
    mkdir -p "$BACKUP_DIR"
    BACKUP_FILE="$BACKUP_DIR/nexus_cache_$(date +%Y%m%d%H%M%S).tar.gz"
    
    # 确认Nexus已停止
    log "${YELLOW}为确保数据一致性，需要先停止Nexus服务${NC}"
    read -p "是否停止Nexus服务并继续备份? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log "${RED}备份已取消${NC}"
        return 1
    fi
    
    # 停止Nexus服务
    log "${BLUE}停止Nexus服务...${NC}"
    ./nexus-service.sh stop
    
    # 执行备份
    log "${BLUE}正在创建备份...${NC}"
    tar -czf "$BACKUP_FILE" -C "./nexus-repository" sonatype-work/nexus3/blobs sonatype-work/nexus3/db
    
    if [ $? -eq 0 ]; then
        log "${GREEN}备份成功: $BACKUP_FILE${NC}"
        
        # 计算备份大小
        BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
        log "${GREEN}备份文件大小: $BACKUP_SIZE${NC}"
    else
        log "${RED}备份失败${NC}"
    fi
    
    # 重新启动Nexus服务
    log "${BLUE}重新启动Nexus服务...${NC}"
    ./nexus-service.sh start
    
    log "${GREEN}备份操作完成${NC}"
}

# 恢复缓存
restore_cache() {
    log "${BLUE}开始恢复Maven缓存...${NC}"
    
    # 检查备份目录
    BACKUP_DIR="./backups"
    if [ ! -d "$BACKUP_DIR" ] || [ -z "$(ls -A "$BACKUP_DIR")" ]; then
        log "${RED}错误: 未找到备份文件${NC}"
        return 1
    fi
    
    # 列出可用备份
    log "${BLUE}可用备份:${NC}"
    ls -lt "$BACKUP_DIR" | grep ".tar.gz" | awk '{print NR") " $9 " - " $6 " " $7 " " $8}'
    
    # 选择备份文件
    read -p "请选择要恢复的备份文件编号: " backup_num
    BACKUP_FILE=$(ls -t "$BACKUP_DIR"/*.tar.gz | sed -n "${backup_num}p")
    
    if [ ! -f "$BACKUP_FILE" ]; then
        log "${RED}错误: 无效的备份文件${NC}"
        return 1
    fi
    
    log "${YELLOW}选择的备份文件: $(basename "$BACKUP_FILE")${NC}"
    log "${RED}警告: 恢复操作将覆盖当前缓存数据${NC}"
    read -p "是否继续? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log "${RED}恢复操作已取消${NC}"
        return 1
    fi
    
    # 停止Nexus服务
    log "${BLUE}停止Nexus服务...${NC}"
    ./nexus-service.sh stop
    
    # 备份当前数据
    TEMP_BACKUP="./nexus-repository/current_data_$(date +%Y%m%d%H%M%S).tar.gz"
    log "${BLUE}备份当前数据: $TEMP_BACKUP${NC}"
    tar -czf "$TEMP_BACKUP" -C "./nexus-repository" sonatype-work/nexus3/blobs sonatype-work/nexus3/db
    
    # 删除当前缓存数据
    log "${BLUE}删除当前缓存数据...${NC}"
    rm -rf ./nexus-repository/sonatype-work/nexus3/blobs
    rm -rf ./nexus-repository/sonatype-work/nexus3/db
    
    # 恢复备份数据
    log "${BLUE}恢复备份数据...${NC}"
    tar -xzf "$BACKUP_FILE" -C "./nexus-repository"
    
    if [ $? -eq 0 ]; then
        log "${GREEN}恢复成功${NC}"
    else
        log "${RED}恢复失败，尝试回滚到之前的数据${NC}"
        rm -rf ./nexus-repository/sonatype-work/nexus3/blobs
        rm -rf ./nexus-repository/sonatype-work/nexus3/db
        tar -xzf "$TEMP_BACKUP" -C "./nexus-repository"
    fi
    
    # 重新启动Nexus服务
    log "${BLUE}重新启动Nexus服务...${NC}"
    ./nexus-service.sh start
    
    log "${GREEN}恢复操作完成${NC}"
}

# 清理缓存
clean_cache() {
    log "${BLUE}开始清理Maven缓存...${NC}"
    
    # 确认操作
    log "${RED}警告: 清理操作将删除所有缓存的依赖包${NC}"
    read -p "是否继续? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log "${RED}清理操作已取消${NC}"
        return 1
    fi
    
    # 停止Nexus服务
    log "${BLUE}停止Nexus服务...${NC}"
    ./nexus-service.sh stop
    
    # 备份当前数据
    TEMP_BACKUP="./backups/pre_clean_$(date +%Y%m%d%H%M%S).tar.gz"
    mkdir -p "./backups"
    log "${BLUE}备份当前数据: $TEMP_BACKUP${NC}"
    tar -czf "$TEMP_BACKUP" -C "./nexus-repository" sonatype-work/nexus3/blobs sonatype-work/nexus3/db
    
    # 清理缓存数据
    log "${BLUE}清理缓存数据...${NC}"
    rm -rf ./nexus-repository/sonatype-work/nexus3/blobs/*
    
    # 保留数据库结构但清除内容
    log "${BLUE}重置数据库...${NC}"
    rm -rf ./nexus-repository/sonatype-work/nexus3/db/*
    
    # 重新启动Nexus服务
    log "${BLUE}重新启动Nexus服务...${NC}"
    ./nexus-service.sh start
    
    log "${GREEN}清理操作完成${NC}"
    log "${YELLOW}注意: 首次访问时Nexus将重新初始化仓库${NC}"
}

# 显示缓存统计信息
show_cache_stats() {
    log "${BLUE}获取缓存统计信息...${NC}"
    
    # 检查Nexus是否运行
    check_nexus_running
    
    # 计算缓存大小
    CACHE_SIZE=$(du -sh ./nexus-repository/sonatype-work/nexus3/blobs 2>/dev/null | cut -f1)
    if [ -z "$CACHE_SIZE" ]; then
        CACHE_SIZE="0"
    fi
    
    # 获取仓库信息
    log "${GREEN}缓存总大小: $CACHE_SIZE${NC}"
    log "${YELLOW}要获取详细的仓库统计信息，请访问Nexus Web界面:${NC}"
    log "${YELLOW}http://localhost:8081/#admin/repository/repositories${NC}"
    
    # 显示磁盘使用情况
    log "${BLUE}磁盘使用情况:${NC}"
    df -h .
}

# 主菜单
show_menu() {
    echo -e "\n${BLUE}===== Maven私服缓存管理工具 =====${NC}"
    echo -e "${YELLOW}1. 备份缓存${NC}"
    echo -e "${YELLOW}2. 恢复缓存${NC}"
    echo -e "${YELLOW}3. 清理缓存${NC}"
    echo -e "${YELLOW}4. 显示缓存统计信息${NC}"
    echo -e "${YELLOW}0. 退出${NC}"
    echo -e "${BLUE}=============================${NC}"
    echo -n "请选择操作 [0-4]: "
}

# 主函数
main() {
    # 检查Nexus是否已安装
    check_nexus_installed
    
    while true; do
        show_menu
        read choice
        
        case $choice in
            1)
                backup_cache
                ;;
            2)
                restore_cache
                ;;
            3)
                clean_cache
                ;;
            4)
                show_cache_stats
                ;;
            0)
                log "${GREEN}退出程序${NC}"
                break
                ;;
            *)
                log "${RED}无效的选择，请重试${NC}"
                ;;
        esac
        
        echo -e "\n按Enter键继续..."
        read
    done
}

# 执行主函数
main