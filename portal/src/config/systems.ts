/** 系统分类 */
export type SystemCategory = 'web' | 'tool' | 'infra' | 'doc'

/** 系统状态 */
export type SystemStatus = 'online' | 'offline' | 'unknown' | 'checking'

/** 系统配置 */
export interface SystemConfig {
  id: string
  name: string
  description: string
  category: SystemCategory
  url?: string
  healthCheckUrl?: string
  icon: string
  color: string
  docs?: { label: string; url: string }[]
  downloadPath?: string
  techStack?: string
  loginUsername?: string
  loginPassword?: string
  status?: number
  sortOrder?: number
}

/** 系统登录凭据 */
export interface SystemCredentials {
  username: string
  password: string
}

/** 所有系统/工具配置 */
export const systems: SystemConfig[] = [
  // ========== Web 系统 ==========
  {
    id: 'mykng',
    name: 'mykng 知识库',
    description: '个人知识库系统：文档管理、文件存储、网页收藏、全文搜索、知识引擎',
    category: 'web',
    url: 'https://kb.marschat.online/kb/',
    healthCheckUrl: 'https://kb.marschat.online/kb/api/auth/actuator/health',
    icon: 'Reading',
    color: '#409eff',
    techStack: 'Spring Boot 3.2 + Vue3 + MySQL + MongoDB + MinIO + MeiliSearch',
    docs: [
      { label: '产品文档', url: 'https://kb.marschat.online/kb/#/dashboard' },
      { label: '部署方案', url: 'https://kb.marschat.online/kb/' },
    ],
  },
  {
    id: 'activation-code',
    name: '激活码服务',
    description: '激活码生成与管理平台：RSA签名、设备绑定、验证工具库',
    category: 'web',
    url: 'https://tools.marschat.online',
    healthCheckUrl: 'https://tools.marschat.online/actuator/health',
    icon: 'Key',
    color: '#e6a23c',
    techStack: 'Spring Boot 3.4 + Java 21 + MyBatis-Plus + MySQL',
    docs: [
      { label: '设计文档', url: 'https://tools.marschat.online' },
    ],
  },
  {
    id: 'activation-code-usage',
    name: '激活码使用页面',
    description: '激活码在线解析与验证工具，无需登录即可使用',
    category: 'web',
    url: 'https://tools.marschat.online/activecode/index.html',
    icon: 'Promotion',
    color: '#e6a23c',
    techStack: '激活码解析与验证（无需登录）',
  },
  {
    id: 'nexus',
    name: 'Nexus 私服',
    description: '统一包管理仓库：npm / Maven / pip / Docker 全栈制品管理',
    category: 'infra',
    url: 'https://nexus.marschat.online',
    healthCheckUrl: 'https://nexus.marschat.online/service/rest/v1/status',
    icon: 'Box',
    color: '#67c23a',
    techStack: 'Nexus Repository Manager 3',
  },
  {
    id: 'frp-dashboard',
    name: 'FRP 仪表盘',
    description: 'FRP 内网穿透管理：隧道监控、客户端管理、配置预览',
    category: 'infra',
    url: 'http://120.26.66.182:7500',
    healthCheckUrl: 'http://120.26.66.182:7500',
    icon: 'Connection',
    color: '#f56c6c',
    techStack: 'FRP + Spring Boot + Vue2',
  },
  {
    id: 'dolphin',
    name: 'DolphinScheduler',
    description: '分布式任务调度平台：定时任务编排、工作流管理',
    category: 'infra',
    url: 'https://tools.marschat.online/dolphin/',
    healthCheckUrl: 'https://tools.marschat.online/dolphin/',
    icon: 'Calendar',
    color: '#909399',
    techStack: 'Apache DolphinScheduler 3.x',
  },
  {
    id: 'kb-ops',
    name: 'kb-ops 运维管理',
    description: '运维管理平台：主机/服务/端口/凭据/域名/依赖/部署记录/看板/矛盾检测/导入',
    category: 'infra',
    url: 'https://kb.marschat.online/ops/',
    healthCheckUrl: 'https://kb.marschat.online/ops/actuator/health',
    icon: 'SetUp',
    color: '#9c27b0',
    techStack: 'Spring Boot 3.2 + Java 21 + MyBatis-Plus + MySQL + Redis',
    docs: [
      { label: '项目源码', url: 'https://github.com/' },
    ],
  },
  {
    id: 'nacos',
    name: 'Nacos 服务中心',
    description: '服务注册与配置中心：服务发现、配置管理、服务治理',
    category: 'infra',
    url: 'https://kb.marschat.online/nacos/',
    healthCheckUrl: 'https://kb.marschat.online/nacos/v1/console/health/liveness',
    icon: 'Connection',
    color: '#67c23a',
    techStack: 'Nacos 2.3.x',
  },
  {
    id: 'minio',
    name: 'MinIO 对象存储',
    description: '分布式对象存储：文件管理、桶管理、访问策略、监控',
    category: 'infra',
    url: 'https://kb.marschat.online/minio/',
    healthCheckUrl: 'https://kb.marschat.online/minio/minio/health/live',
    icon: 'FolderOpened',
    color: '#e6a23c',
    techStack: 'MinIO RELEASE.2024',
  },
  {
    id: 'meilisearch',
    name: 'MeiliSearch 搜索引擎',
    description: '全文搜索引擎：索引管理、搜索预览、API密钥管理',
    category: 'infra',
    url: 'https://kb.marschat.online/meilisearch/',
    healthCheckUrl: 'https://kb.marschat.online/meilisearch/health',
    icon: 'Search',
    color: '#ff6b6b',
    techStack: 'MeiliSearch 1.12',
  },
  {
    id: 'vaultwarden',
    name: 'Vaultwarden 密码管理',
    description: '密码管理器：个人密码、安全笔记、身份认证器、组织共享',
    category: 'web',
    url: 'https://kb.marschat.online/vault/',
    icon: 'Lock',
    color: '#175ddc',
    techStack: 'Vaultwarden (Bitwarden compatible)',
  },

  // ========== 工具软件 ==========
  {
    id: 'qrcode-tool-csharp',
    name: 'QRCodeTool (C#)',
    description: '二维码扫描与生成工具，已接入激活码验证体系',
    category: 'tool',
    icon: 'Aim',
    color: '#9c27b0',
    techStack: 'C# .NET 6 WinForms',
    downloadPath: '/portal/downloads/QRCodeTool.zip',
    docs: [
      { label: '使用说明', url: 'https://kb.marschat.online/kb/' },
    ],
  },
  {
    id: 'activation-verifier',
    name: '激活码验证库',
    description: 'C# 类库：设备指纹、防调试、时间篡改检测、RSA验证',
    category: 'tool',
    icon: 'Lock',
    color: '#9c27b0',
    techStack: 'C# .NET 6 类库 (Jones.Activation.dll)',
    downloadPath: '/portal/downloads/Jones.Activation.dll',
  },
  {
    id: 'qr-generator-rust',
    name: 'QR Generator (Rust)',
    description: 'Rust 版二维码工具：高性能截图识别与生成',
    category: 'tool',
    icon: 'Aim',
    color: '#9c27b0',
    techStack: 'Rust + egui',
    downloadPath: '/portal/downloads/qr-rust.zip',
  },
  {
    id: 'git-auto',
    name: 'Git 自动化工具',
    description: '批量 Git 仓库标签管理：自动打标签、版本号管理',
    category: 'tool',
    icon: 'Files',
    color: '#9c27b0',
    techStack: 'Python + Bat',
    downloadPath: '/portal/downloads/git-auto.zip',
  },

  // ========== 项目文档 ==========
  {
    id: 'devtools-docs',
    name: '项目文档中心',
    description: 'devtools 项目文档：架构设计、部署手册、API规范',
    category: 'doc',
    url: 'https://kb.marschat.online/kb/',
    icon: 'Document',
    color: '#00bcd4',
    docs: [
      { label: 'mykng 文档', url: 'https://kb.marschat.online/kb/' },
      { label: '激活码文档', url: 'https://tools.marschat.online' },
      { label: 'Nexus 文档', url: 'https://nexus.marschat.online' },
    ],
  },
  {
    id: 'openclaw-docs',
    name: 'OpenClaw 知识库',
    description: '龙虾 OpenClaw 体系文档：主机清单、凭据汇总、运维方案',
    category: 'doc',
    url: 'https://kb.marschat.online/kb/',
    icon: 'Memo',
    color: '#00bcd4',
  },
]

/** 分类标签 */
export const categoryLabels: Record<SystemCategory, string> = {
  web: 'Web 系统',
  tool: '工具软件',
  infra: '基础设施',
  doc: '项目文档',
}

/** 分类图标 */
export const categoryIcons: Record<SystemCategory, string> = {
  web: 'Monitor',
  tool: 'Tools',
  infra: 'Setting',
  doc: 'FolderOpened',
}
