// ============================================================
// Jenkins 初始化脚本 04: 创建 Pipeline 任务（从 Drone 迁移）
// ============================================================
// 自动创建以下任务:
//   - devtools-mykng        (知识库微服务)
//   - devtools-active-manager (激活码系统)
//
// 每个任务使用 Pipeline from SCM，Jenkinsfile 在代码仓库中
// ============================================================

import jenkins.model.*
import org.jenkinsci.plugins.workflow.job.*
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import hudson.plugins.git.*
import hudson.triggers.SCMTrigger
import java.util.logging.Logger

def logger = Logger.getLogger("")
def instance = Jenkins.get()

logger.info("===== kb-cicd: 创建 Pipeline 任务 =====")

// Git 仓库配置
def gitRepoUrl = "https://gitee.com/jonesAriven/devtools.git"
def gitBranch = "*/dev"

// 项目定义（从 Drone .drone.yml 迁移）
def projects = [
    [
        name: "devtools-mykng",
        displayName: "📘 知识库微服务 (mykng)",
        description: "kb-gateway + kb-auth + kb-file + kb-knowledge + kb-intelligence\n部署目标: mykng (100.93.36.113)"
    ],
    [
        name: "devtools-active-manager",
        displayName: "🔑 激活码系统 (active-manager)",
        description: "激活码服务端\n部署目标: 内网Debian (192.168.31.182)"
    ]
]

projects.each { project ->
    def jobName = project.name
    
    // 检查任务是否已存在
    def existingJob = instance.getItem(jobName)
    if (existingJob != null) {
        logger.info("⏭️ 任务 ${jobName} 已存在，跳过创建")
        return
    }
    
    logger.info("创建任务: ${jobName}")
    
    // 创建 Pipeline 任务
    def job = new WorkflowJob(instance, jobName)
    job.setDisplayName(project.displayName)
    job.setDescription(project.description)
    
    // 配置 Pipeline from SCM（从 Git 仓库读取 Jenkinsfile）
    def flowDef = new CpsScmFlowDefinition(
        new GitSCM(
            [[new UserRemoteConfig(gitRepoUrl)]],
            [gitBranch],     // branches
            false,           // do generate submodule configurations
            null,            // browser
            null,            // gitTool
            []               // extensions
        ),
        "Jenkinsfile"       // Jenkinsfile 路径
    )
    
    // 每个任务的 Jenkinsfile 可以不同，通过项目名区分
    // 使用轻量级 checkout 提速
    flowDef.setLightweight(true)
    
    job.setFlowDefinition(flowDef)
    
    // 添加到 Jenkins
    instance.add(job, true)
    
    logger.info("✅ 任务 ${jobName} 创建成功")
}

instance.save()
logger.info("===== kb-cicd: Pipeline 任务创建完成 (${projects.size()} 个) =====")
