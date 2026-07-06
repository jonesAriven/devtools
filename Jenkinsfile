// ============================================================
// Jenkinsfile — devtools CI/CD Pipeline
// ============================================================
//
// 📁 架构设计: 注册表模式（Registry Pattern）
//   本文件 = 调度中心 + 项目注册表
//   各项目/ci/deploy.sh = 具体部署逻辑
//
// 🎯 核心机制:
//   1. 所有项目共享一个统一 Pipeline
//   2. 通过 PROJECTS 注册表声明哪些项目可部署
//   3. 手动触发时传 DEPLOY_PROJECT 参数选择目标
//   4. 每个 pipeline 自动：检查项目 → 编译 → 部署
//
// 🔧 手动触发:
//   Jenkins → Build with Parameters → 选择:
//     DEPLOY_PROJECT=mykng          # 只部署 mykng
//     DEPLOY_PROJECT=active-manager # 只部署激活码
//     DEPLOY_PROJECT=all            # 全部部署
//     (不填则全部部署)
//
// ➕ 新增项目的步骤（只需改3处）:
//   1. 在 PROJECTS 注册表中添加一行
//   2. 创建 项目名/ci/deploy.sh
//   3. 在 stages 块中复制一个 stage 并改参数
//   完事！不用重复写 check/build/deploy 逻辑
//
// ⏰ 定时任务: Jenkins → Configure → Triggers → ☑️ Schedule
//   H 23 * * *  # 每天23:00构建（可选）
//
// 🌐 Webhook 自动触发: Gitee → Webhook → Jenkins
// ============================================================

pipeline {
    agent any
    
    // ================================
    // 🎛️ 全局参数定义（Build with Parameters）
    // ================================
    parameters {
        choice(
            name: 'DEPLOY_PROJECT',
            choices: ['all', 'mykng', 'active-manager', 'kb-ops', 'myfrp', 'portal', 'infra-monitor'],
            description: '📦 选择要部署的项目（多项目用逗号分隔）'
        )
        choice(
            name: 'DEPLOY_TARGET',
            choices: ['production', 'dev'],
            description: '🌍 部署目标环境'
        )
        string(
            name: 'GIT_BRANCH',
            defaultValue: 'dev',
            description: '🔀 Git 分支（一般不改）'
        )
    }
    
    // ================================
    // ⏱️ 全局超时 & 工具配置
    // ================================
    options {
        timeout(time: 60, unit: 'MINUTES')
        timestamps()                    // 日志带时间戳
        ansiColor('xterm')              // ANSI 彩色日志
        buildDiscarder(logRotator(numToKeepStr: '30'))  // 保留30条历史
        disableConcurrentBuilds()       // 同一项目不并发构建
    }
    
    // ================================
    // 📦 项目注册表（Projects Registry）
    // =================---------------
    // 所有支持 CI/CD 的项目都在这里注册。
    // 新增项目？只需加一行，然后复制一个 stage 即可。
    // ================================
    environment {
        // Maven 优化参数
        MAVEN_OPTS = '-Xmx2048m -Dmaven.repo.local=/root/.m2/repository'
        
        // ============================================================
        // 📋 项目注册表（Projects Registry）— 所有支持 CI/CD 的项目
        // ============================================================
        // 新增项目？只需加环境变量 + 复制一个 stage 块即可
        // 格式: <项目>_MODULE / _HOST / _SCRIPT / _MEMORY
        // ============================================================
        
        // ── 已上线项目 ──
        MYKNG_MODULE = 'mykng/kb-parent'
        MYKNG_HOST = '100.93.36.113'       # Tailscale IP
        MYKNG_SCRIPT = 'mykng/ci/deploy.sh'
        MYKNG_MEMORY = '2048m'
        
        ACTIVE_MANAGER_MODULE = 'active-manager/activation-code-server'
        ACTIVE_MANAGER_HOST = '192.168.31.182'  # 内网 Debian
        ACTIVE_MANAGER_SCRIPT = 'active-manager/ci/deploy.sh'
        ACTIVE_MANAGER_MEMORY = '1024m'
        
        // ── 待配置项目（需要先创建 ci/deploy.sh）──
        KB_OPS_MODULE = 'kb-ops'
        KB_OPS_HOST = '100.93.36.113'         # TODO: 确认部署目标
        KB_OPS_SCRIPT = 'kb-ops/ci/deploy.sh'   # TODO: 创建此文件
        KB_OPS_MEMORY = '1024m'
        
        MYFRP_MODULE = 'myfrp'
        MYFRP_HOST = '100.93.36.113'          # TODO: 确认部署目标
        MYFRP_SCRIPT = 'myfrp/ci/deploy.sh'      # TODO: 创建此文件
        MYFRP_MEMORY = '512m'
        
        PORTAL_MODULE = 'portal/portal-server'
        PORTAL_HOST = '100.93.36.113'           # TODO: 确认部署目标
        PORTAL_SCRIPT = 'portal/ci/deploy.sh'    # TODO: 创建此文件
        PORTAL_MEMORY = '1024m'
        
        INFRA_MONITOR_MODULE = 'infra-monitor/infra-monitor-server'
        INFRA_MONITOR_HOST = '100.93.36.113'     # TODO: 确认部署目标
        INFRA_MONITOR_SCRIPT = 'infra-monitor/ci/deploy.sh'  # TODO: 创建此文件
        INFRA_MONITOR_MEMORY = '512m'
    }
    
    // ================================
    // 🔄 触发器配置
    // ================================
    triggers {
        // Gitee Webhook 会自动触发（通过 Generic Webhook 插件）
        // 也可以取消注释启用定时构建
        // cron('H 23 * * *')  // 每天晚上11点
    }
    
    stages {
        // ================================
        // Stage 0: 环境准备 & 信息展示
        // ================================
        stage('📋 准备环境') {
            steps {
                echo "============================================="
                echo "  kb-cicd 构建开始"
                echo "  时间: ${new Date().format('yyyy-MM-dd HH:mm:ss')}"
                echo "  项目: ${params.DEPLOY_PROJECT}"
                echo "  环境: ${params.DEPLOY_TARGET}"
                echo "  分支: ${params.GIT_BRANCH}"
                echo "  Commit: ${GIT_COMMIT ?: env.GIT_COMMIT}"
                echo "============================================="
                
                // 显示环境信息
                sh '''
                    echo "--- Java 版本 ---"
                    java -version 2>&1 | head -1
                    echo ""
                    echo "--- Maven 版本 ---"
                    mvn -version 2>&1 | head -1
                    echo ""
                    echo "--- 磁盘空间 ---"
                    df -h / /root/.m2 2>/dev/null || true
                    echo ""
                    echo "--- 内存信息 ---"
                    free -h 2>/dev/null || true
                '''
            }
        }

        // ================================
        // Stage 1: mykng 知识库微服务
        // ================================
        stage('📘 mykng 知识库微服务') {
            when {
                anyOf {
                    expression { return params.DEPLOY_PROJECT == 'all' || params.DEPLOY_PROJECT == 'mykng' }
                }
            }
            environment {
                MAVEN_OPTS = '-Xmx2048m -Dmaven.repo.local=/root/.m2/repository'
            }
            steps {
                echo "=== [mykng] 开始构建 ==="
                
                // 1. Maven 编译
                milestone(label: 'mykng-build')
                sh '''
                    echo ">>> [mykng] Maven 编译 <<<"
                    cd mykng/kb-parent
                    
                    mvn clean package \\
                        -DskipTests \\
                        -B \\
                        -V \\
                        -ntp \\
                        -Pfast \\
                        -T 2C
                    
                    cd ../..
                    
                    echo "=== 编译产物 ==="
                    ls -lh mykng/kb-gateway/target/*.jar \\
                          mykng/kb-auth/target/*.jar \\
                          mykng/kb-file/target/*.jar \\
                          mykng/kb-knowledge/target/*.jar \\
                          mykng/kb-intelligence/target/*.jar \\
                          2>/dev/null || echo "⚠️ jar 包不存在"
                '''
                
                // 2. SSH 部署到 mykng 主机
                milestone(label: 'mykng-deploy')
                sshPublisher(
                    publishers: [
                        sshPublisherDesc(
                            configName: 'mykng-deploy',
                            transfers: [
                                sshTransfer(
                                    execCommand: """
                                        chmod +x /root/devtools/mykng/ci/deploy.sh
                                        bash /root/devtools/mykng/ci/deploy.sh "${GIT_COMMIT}" "${params.GIT_BRANCH}" "${params.DEPLOY_TARGET}"
                                    """,
                                    execTimeout: 900000  // 15分钟超时
                                )
                            ],
                            verbose: true
                        )
                    ]
                )
            }
            post {
                success { echo "✅ mykng 部署成功" }
                failure { echo "❌ mykng 构建或部署失败" }
            }
        }

        // ================================
        // Stage 2: active-manager 激活码系统
        // ================================
        stage('🔑 active-manager 激活码系统') {
            when {
                anyOf {
                    expression { return params.DEPLOY_PROJECT == 'all' || params.DEPLOY_PROJECT == 'active-manager' }
                }
            }
            environment {
                MAVEN_OPTS = '-Xmx1024m -Dmaven.repo.local=/root/.m2/repository'
            }
            steps {
                echo "=== [active-manager] 开始构建 ==="
                
                // 1. Maven 编译
                milestone(label: 'active-manager-build')
                sh '''
                    echo ">>> [active-manager] Maven 编译 <<<"
                    cd active-manager/activation-code-server
                    
                    mvn clean package \\
                        -DskipTests \\
                        -B \\
                        -V \\
                        -ntp \\
                        -Pfast
                    
                    cd ../..
                    
                    echo "=== 编译产物 ==="
                    ls -lh active-manager/activation-code-server/target/*.jar \\
                          2>/dev/null || echo "⚠️ jar 包不存在"
                '''
                
                // 2. SSH 部署到内网 Debian
                milestone(label: 'active-manager-deploy')
                sshPublisher(
                    publishers: [
                        sshPublisherDesc(
                            configName: 'lan-deploy',
                            transfers: [
                                sshTransfer(
                                    execCommand: """
                                        chmod +x /root/devtools/active-manager/ci/deploy.sh
                                        bash /root/devtools/active-manager/ci/deploy.sh "${GIT_COMMIT}" "${params.GIT_BRANCH}"
                                    """,
                                    execTimeout: 600000  // 10分钟超时
                                )
                            ],
                            verbose: true
                        )
                    ]
                )
            }
            post {
                success { echo "✅ active-manager 部署成功" }
                failure { echo "❌ active-manager 构建或部署失败" }
            }
        }
        
        // ================================
        // Stage 3: kb-ops 运维平台
        // ================================
        stage('⚙️ kb-ops 运维平台') {
            when {
                anyOf {
                    expression { return params.DEPLOY_PROJECT == 'all' || params.DEPLOY_PROJECT == 'kb-ops' }
                }
            }
            environment {
                MAVEN_OPTS = '-Xmx1024m -Dmaven.repo.local=/root/.m2/repository'
            }
            steps {
                echo "=== [kb-ops] 开始构建 ==="
                milestone(label: 'kb-ops-build')
                sh '''
                    echo ">>> [kb-ops] Maven 编译 <<<"
                    cd kb-ops
                    mvn clean package -DskipTests -B -V -ntp -Pfast
                    cd ..
                    echo "=== 编译产物 ==="
                    ls -lh kb-ops/target/*.jar 2>/dev/null || echo "⚠️ jar 包不存在"
                '''
                milestone(label: 'kb-ops-deploy')
                sshPublisher(publishers: [sshPublisherDesc(
                    configName: 'mykng-deploy',
                    transfers: [sshTransfer(
                        execCommand: """
                            if [ -f /root/devtools/kb-ops/ci/deploy.sh ]; then
                                chmod +x /root/devtools/kb-ops/ci/deploy.sh
                                bash /root/devtools/kb-ops/ci/deploy.sh "${GIT_COMMIT}" "${params.GIT_BRANCH}" "${params.DEPLOY_TARGET}"
                            else
                                echo "⚠️ kb-ops/ci/deploy.sh 不存在，跳过部署"
                                echo "请先创建部署脚本: kb-ops/ci/deploy.sh"
                            fi
                        """,
                        execTimeout: 600000
                    )],
                    verbose: true
                )])
            }
            post {
                success { echo "✅ kb-ops 部署成功" }
                failure { echo "❌ kb-ops 构建或部署失败" }
            }
        }

        // ================================
        // Stage 4: myfrp FRP管理面板
        // ================================
        stage('🌐 myfrp FRP管理面板') {
            when {
                anyOf {
                    expression { return params.DEPLOY_PROJECT == 'all' || params.DEPLOY_PROJECT == 'myfrp' }
                }
            }
            environment {
                MAVEN_OPTS = '-Xmx512m -Dmaven.repo.local=/root/.m2/repository'
            }
            steps {
                echo "=== [myfrp] 开始构建 ==="
                milestone(label: 'myfrp-build')
                sh '''
                    echo ">>> [myfrp] Maven 编译 <<<"
                    cd myfrp
                    mvn clean package -DskipTests -B -V -ntp -Pfast
                    cd ..
                    echo "=== 编译产物 ==="
                    ls -lh myfrp/target/*.jar 2>/dev/null || echo "⚠️ jar 包不存在"
                '''
                milestone(label: 'myfrp-deploy')
                sshPublisher(publishers: [sshPublisherDesc(
                    configName: 'mykng-deploy',
                    transfers: [sshTransfer(
                        execCommand: """
                            if [ -f /root/devtools/myfrp/ci/deploy.sh ]; then
                                chmod +x /root/devtools/myfrp/ci/deploy.sh
                                bash /root/devtools/myfrp/ci/deploy.sh "${GIT_COMMIT}" "${params.GIT_BRANCH}"
                            else
                                echo "⚠️ myfrp/ci/deploy.sh 不存在，跳过部署"
                            fi
                        """,
                        execTimeout: 300000
                    )],
                    verbose: true
                )])
            }
            post {
                success { echo "✅ myfrp 部署成功" }
                failure { echo "❌ myfrp 构建或部署失败" }
            }
        }

        // ================================
        // Stage 5: portal 门户系统 (Node.js)
        // ================================
        stage('🚪 portal 门户系统') {
            when {
                anyOf {
                    expression { return params.DEPLOY_PROJECT == 'all' || params.DEPLOY_PROJECT == 'portal' }
                }
            }
            steps {
                echo "=== [portal] 开始构建 ==="
                milestone(label: 'portal-build')
                sh '''
                    echo ">>> [portal] Node.js 构建 <<<"
                    cd portal
                    
                    # 检查 node_modules
                    if [ ! -d "node_modules" ]; then
                        echo "安装依赖..."
                        npm ci --registry=https://registry.npmmirror.com
                    fi
                    
                    # 构建
                    npm run build
                    
                    cd ..
                    echo "=== 构建产物 ==="
                    ls -lh portal/dist/ 2>/dev/null | head -10 || echo "⚠️ dist 目录不存在"
                '''
                milestone(label: 'portal-deploy')
                sshPublisher(publishers: [sshPublisherDesc(
                    configName: 'mykng-deploy',
                    transfers: [sshTransfer(
                        execCommand: """
                            if [ -f /root/devtools/portal/ci/deploy.sh ]; then
                                chmod +x /root/devtools/portal/ci/deploy.sh
                                bash /root/devtools/portal/ci/deploy.sh "${GIT_COMMIT}" "${params.GIT_BRANCH}"
                            else
                                echo "⚠️ portal/ci/deploy.sh 不存在，跳过部署"
                            fi
                        """,
                        execTimeout: 600000
                    )],
                    verbose: true
                )])
            }
            post {
                success { echo "✅ portal 部署成功" }
                failure { echo "❌ portal 构建或部署失败" }
            }
        }

        // ================================
        // Stage 6: infra-monitor 基础设施监控
        // ================================
        stage('📊 infra-monitor 基础设施监控') {
            when {
                anyOf {
                    expression { return params.DEPLOY_PROJECT == 'all' || params.DEPLOY_PROJECT == 'infra-monitor' }
                }
            }
            environment {
                MAVEN_OPTS = '-Xmx512m -Dmaven.repo.local=/root/.m2/repository'
            }
            steps {
                echo "=== [infra-monitor] 开始构建 ==="
                milestone(label: 'infra-monitor-build')
                sh '''
                    echo ">>> [infra-monitor] Maven 编译 <<<"
                    cd infra-monitor/infra-monitor-server
                    mvn clean package -DskipTests -B -V -ntp -Pfast
                    cd ../../
                    echo "=== 编译产物 ==="
                    ls -lh infra-monitor/infra-monitor-server/target/*.jar 2>/dev/null || echo "⚠️ jar 包不存在"
                '''
                milestone(label: 'infra-monitor-deploy')
                sshPublisher(publishers: [sshPublisherDesc(
                    configName: 'mykng-deploy',
                    transfers: [sshTransfer(
                        execCommand: """
                            if [ -f /root/devtools/infra-monitor/ci/deploy.sh ]; then
                                chmod +x /root/devtools/infra-monitor/ci/deploy.sh
                                bash /root/devtools/infra-monitor/ci/deploy.sh "${GIT_COMMIT}" "${params.GIT_BRANCH}"
                            else
                                echo "⚠️ infra-monitor/ci/deploy.sh 不存在，跳过部署"
                            fi
                        """,
                        execTimeout: 300000
                    )],
                    verbose: true
                )])
            }
            post {
                success { echo "✅ infra-monitor 部署成功" }
                failure { echo "❌ infra-monitor 构建或部署失败" }
            }
        }
        
        // ================================
        // Stage N+1: 🚀 快速新增项目模板
        // ================================
        // 复制下面的 stage 块，改5个地方即可：
        //   ① stage 名称和 emoji
        //   ② when 表达式中的项目名
        //   ③ environment MAVEN_OPTS（按需）
        //   ④ sh 中的构建命令（支持 Java/Node.js/Python/Go/.NET）
        //   ⑤ sshPublisher 中的 configName 和 execCommand
        //
        // 💡 多语言示例:
        //   Java:   cd xxx && mvn clean package -DskipTests -B && cd ..
        //   Node:   cd xxx && npm ci && npm run build && cd ..
        //   Python: cd xxx && pip install -r requirements.py && python main.py
        //   Go:     cd xxx && go build -o app ./...
        //   .NET:   cd xxx && dotnet build -c Release
        //
        // stage('🏷️ <项目名>') {
        //     when {
        //         anyOf {
        //             expression { return params.DEPLOY_PROJECT == 'all' || params.DEPLOY_PROJECT == '<项目名>' }
        //         }
        //     }
        //     environment {
        //         MAVEN_OPTS = '-Xmx<内存>m -Dmaven.repo.local=/root/.m2/repository'
        //     }
        //     steps {
        //         sh '''
        //             cd <项目路径> && <构建命令> && cd ../..
        //         '''
        //         sshPublisher(publishers: [sshPublisherDesc(
        //             configName: '<SSH配置名>',
        //             transfers: [sshTransfer(execCommand: 'bash /root/devtools/<项目>/ci/deploy.sh ...')],
        //             verbose: true
        //         )])
        //     }
        // }
    }  // end stages
    
    // ================================
    // 📊 Post: 构建后处理
    // ================================
    post {
        always {
            echo "============================================="
            echo "  kb-cicd 构建结束"
            echo "  状态: ${currentBuild.result ?: 'SUCCESS'}"
            echo "  耗时: ${currentBuild.durationString.replace(' and counting', '')}"
            echo "  时间: ${new Date().format('yyyy-MM-dd HH:mm:ss')}"
            echo "============================================="
            
            // 清理工作空间（节省磁盘）
            cleanWs(cleanWhenNotFailed: false, deleteDirs: true)
        }
        success {
            // 可选：发送通知
            // dingtalk(robot: 'jenkins', type: 'MARKDOWN', title: '✅ CI/CD 成功', text: "...")
        }
        failure {
            // 发送失败通知（钉钉/邮件等）
            echo "❌ 构建失败！请检查上方日志"
            // dingtalk(robot: 'jenkins', type: 'MARKDOWN', title: '❌ CI/CD 失败', text: "...")
        }
    }
}
