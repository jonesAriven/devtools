// ============================================================
// Jenkins 初始化脚本 04: 创建所有 Pipeline 任务（独立任务）
// ============================================================
//
// 每个项目一个独立的 Pipeline 任务，使用各自的 Jenkinsfile
// 任务命名规则: devtools-<项目名>
//
// 任务列表:
//   1. devtools-mykng          → mykng/Jenkinsfile.mykng
//   2. devtools-active-manager → active-manager/Jenkinsfile.active-manager
//   3. devtools-kb-ops         → kb-ops/Jenkinsfile.kb-ops
//   4. devtools-myfrp          → myfrp/Jenkinsfile.myfrp
//   5. devtools-portal         → portal/Jenkinsfile.portal
//   6. devtools-infra-monitor  → infra-monitor/Jenkinsfile.infra-monitor
// ============================================================

import jenkins.model.*
import org.jenkinsci.plugins.workflow.job.*
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import hudson.plugins.git.*
import hudson.triggers.SCMTrigger
import java.util.logging.Logger

def logger = Logger.getLogger("")
def instance = Jenkins.get()

logger.info("===== kb-cicd: 创建独立 Pipeline 任务 =====")

// Git 仓库配置
def gitRepoUrl = "https://gitee.com/jonesAriven/devtools.git"
def gitBranch = "*/dev"

// ====================
// 项目定义列表
// ====================
// 每个项目: [jobId, displayName, description, jenkinsfilePath]
def projects = [
    [
        id: "devtools-mykng",
        name: "📘 知识库微服务 (mykng)",
        desc: """知识库微服务群 (5个微服务)
├── kb-gateway   :8090 - API网关 + JWT鉴权
├── kb-auth      :8081 - 认证服务
├── kb-file      :8082 - 文件服务
├── kb-knowledge :8083 - 知识库服务
└── kb-intelligence:8086 - AI智能服务

部署目标: mykng (100.93.36.113, Tailscale)
Docker Compose Project: kb-deploy""",
        jenkinsfile: "mykng/Jenkinsfile.mykng"
    ],
    [
        id: "devtools-active-manager",
        name: "🔑 激活码系统 (active-manager)",
        desc: """激活码管理服务
端口: 18080 (映射容器 8080)
数据库: 宿主机 MySQL (tools)
部署目标: 内网Debian (192.168.31.182)
容器名: activecode""",
        jenkinsfile: "active-manager/Jenkinsfile.active-manager"
    ],
    [
        id: "devtools-kb-ops",
        name: "⚙️ 运维平台 (kb-ops)",
        desc: """运维管理平台
端口: 8084
部署目标: mykng (100.93.36.113)
容器名: kb-ops
首次部署自动创建 docker-compose.yml""",
        jenkinsfile: "kb-ops/Jenkinsfile.kb-ops"
    ],
    [
        id: "devtools-myfrp",
        name: "🌐 FRP管理面板 (myfrp)",
        desc: """FRP内网穿透管理面板
端口: 18082
部署目标: mykng (100.93.36.113)
容器名: frp-manager
前后端分离 (Java后端 + Vue3前端)""",
        jenkinsfile: "myfrp/Jenkinsfile.myfrp"
    ],
    [
        id: "devtools-portal",
        name: "🚪 门户系统 (portal)",
        desc: """DevTools 工具总入口看板
技术栈: Vue3 + Vite + Element Plus + TypeScript
部署方式: Nginx 静态文件 或 Docker
部署目标: mykng (100.93.36.113)""",
        jenkinsfile: "portal/Jenkinsfile.portal"
    ],
    [
        id: "devtools-infra-monitor",
        name: "📊 基础设施监控 (infra-monitor)",
        desc: """服务器/服务监控平台
后端: Java/Spring Boot
前端: Vue3/Vite (infra-monitor-web)
端口: 8085 (默认)
部署目标: mykng (100.93.36.113)
容器名: infra-monitor
前后端分离，前端集成到后端JAR""",
        jenkinsfile: "infra-monitor/Jenkinsfile.infra-monitor"
    ]
]

// ====================
// 创建或更新每个任务
// ====================
projects.each { project ->
    def jobId = project.id
    
    // 检查任务是否已存在
    def existingJob = instance.getItem(jobId)
    
    if (existingJob != null) {
        logger.info("⏭️ 任务 ${jobId} 已存在，跳过创建")
        
        // 可选：更新描述
        if (existingJob.description != project.desc) {
            existingJob.setDescription(project.desc)
            logger.info("  ↻ 已更新 ${jobId} 描述")
        }
        return
    }
    
    logger.info("创建任务: ${jobId}")
    
    // 创建 Pipeline 任务
    def job = new WorkflowJob(instance, jobId)
    job.setDisplayName(project.name)
    job.setDescription(project.desc)
    
    // 配置 Pipeline from SCM（从 Git 读取对应的 Jenkinsfile）
    def flowDef = new CpsScmFlowDefinition(
        new GitSCM(
            [[new UserRemoteConfig(gitRepoUrl)]],
            [gitBranch],
            false,
            null,
            null,
            []
        ),
        project.jenkinsfile  // 每个 task 使用自己的 Jenkinsfile
    )
    
    flowDef.setLightweight(true)
    job.setFlowDefinition(flowDef)
    
    // 添加到 Jenkins
    instance.add(job, true)
    
    logger.info("✅ 任务 ${jobId} 创建成功 (Jenkinsfile: ${project.jenkinsfile})")
}

instance.save()

logger.info("===== kb-cicd: Pipeline 任务创建完成 (${projects.size()} 个独立任务) =====")
logger.info ""
logger.info "===== 任务清单 ====="
projects.each { p ->
    logger.info "  • ${p.id}: ${p.name}"
}
