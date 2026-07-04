#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通用部署引擎 (Universal Deploy Engine)
========================================
- 配置化：换项目只改 deploy-config.yml
- 插件化：支持 docker-hotfix / docker-compose / k8s 等多种部署策略
- 自进化：每次部署记录经验，踩坑自动沉淀
- 安全网：热更新失败自动回滚，零宕机风险

用法:
    python deploy_engine.py status                    # 查看所有服务状态
    python deploy_engine.py build                     # 本地编译打包
    python deploy_engine.py deploy --all              # 部署所有服务
    python deploy_engine.py deploy --service kb-file  # 部署单个服务
    python deploy_engine.py rollback --service kb-file # 回滚单个服务
    python deploy_engine.py logs --service kb-file    # 查看服务日志
    python deploy_engine.py history                   # 查看部署历史
    python deploy_engine.py issues                    # 查看已知问题库
    python deploy_engine.py doctor                    # 环境诊断 + 优化建议
"""

import os
import sys
import time
import json
import hashlib
import argparse
import datetime
import subprocess
from pathlib import Path
from typing import Optional, Dict, List, Tuple

try:
    import yaml
except ImportError:
    print("⚠️  缺少 pyyaml，正在安装...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pyyaml", "-q"])
    import yaml

try:
    import paramiko
except ImportError:
    print("⚠️  缺少 paramiko，正在安装...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "paramiko", "-q"])
    import paramiko


# ============================================================
#  颜色输出工具
# ============================================================

class Color:
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    BOLD = '\033[1m'
    END = '\033[0m'

    @classmethod
    def ok(cls, msg): print(f"{cls.GREEN}✅ {msg}{cls.END}")
    @classmethod
    def fail(cls, msg): print(f"{cls.RED}❌ {msg}{cls.END}")
    @classmethod
    def warn(cls, msg): print(f"{cls.YELLOW}⚠️  {msg}{cls.END}")
    @classmethod
    def info(cls, msg): print(f"{cls.BLUE}ℹ️  {msg}{cls.END}")
    @classmethod
    def step(cls, msg): print(f"\n{cls.BOLD}{cls.CYAN}>>> {msg}{cls.END}")
    @classmethod
    def banner(cls, msg):
        line = "=" * 60
        print(f"\n{cls.BOLD}{cls.CYAN}{line}{cls.END}")
        print(f"{cls.BOLD}{cls.CYAN}  {msg}{cls.END}")
        print(f"{cls.BOLD}{cls.CYAN}{line}{cls.END}\n")


# ============================================================
#  SSH 连接管理器（连接复用 + 超时处理 + 实时输出）
# ============================================================

class SSHManager:
    def __init__(self, config: dict):
        self.host = config['host']
        self.port = config.get('port', 22)
        self.username = config['username']
        self.password = config.get('password')
        self.key_file = config.get('key_file')
        self.timeout = config.get('connect_timeout', 15)
        self.command_timeout = config.get('command_timeout', 120)
        self.client: Optional[paramiko.SSHClient] = None
        self._sftp = None

    def connect(self):
        """建立 SSH 连接（可重复调用，已连接则跳过）"""
        if self.client and self.client.get_transport() and self.client.get_transport().is_active():
            return
        Color.info(f"连接 {self.username}@{self.host}:{self.port}...")
        self.client = paramiko.SSHClient()
        self.client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        kwargs = {
            'hostname': self.host,
            'port': self.port,
            'username': self.username,
            'timeout': self.timeout,
        }
        if self.key_file:
            kwargs['key_filename'] = self.key_file
        else:
            kwargs['password'] = self.password
        self.client.connect(**kwargs)
        Color.ok(f"已连接到 {self.host}")

    def close(self):
        if self._sftp:
            self._sftp.close()
            self._sftp = None
        if self.client:
            self.client.close()
            self.client = None

    def exec_cmd(self, cmd: str, timeout: Optional[int] = None) -> Tuple[int, str, str]:
        """执行命令，返回 (exit_code, stdout, stderr)"""
        self.connect()
        t = timeout or self.command_timeout
        stdin, stdout, stderr = self.client.exec_command(cmd, timeout=t)
        exit_code = stdout.channel.recv_exit_status()
        out = stdout.read().decode('utf-8', errors='replace')
        err = stderr.read().decode('utf-8', errors='replace')
        return exit_code, out, err

    def exec_background(self, cmd: str, log_file: str) -> str:
        """后台执行命令，输出写到日志文件，返回 PID"""
        self.connect()
        # 用 nohup 后台执行，输出重定向到文件
        bg_cmd = f"nohup {cmd} > {log_file} 2>&1 & echo $!"
        exit_code, out, err = self.exec_cmd(bg_cmd, timeout=10)
        pid = out.strip().split('\n')[-1].strip()
        return pid

    def tail_log(self, log_file: str, last_pos: int = 0) -> Tuple[str, int]:
        """读取日志文件的新增内容，返回 (新增内容, 新位置)"""
        self.connect()
        cmd = f"if [ -f {log_file} ]; then wc -c < {log_file}; else echo 0; fi"
        _, size_str, _ = self.exec_cmd(cmd, timeout=10)
        try:
            current_size = int(size_str.strip())
        except ValueError:
            current_size = 0

        if current_size <= last_pos:
            return "", last_pos

        # 读取新增部分
        cmd = f"tail -c +{last_pos + 1} {log_file}"
        _, content, _ = self.exec_cmd(cmd, timeout=10)
        return content, current_size

    def sftp(self) -> paramiko.SFTPClient:
        """获取 SFTP 客户端（复用连接）"""
        self.connect()
        if not self._sftp:
            self._sftp = self.client.open_sftp()
        return self._sftp

    def upload_file(self, local_path: str, remote_path: str):
        """上传文件，带进度回调"""
        self.connect()
        sftp = self.sftp()
        local_size = os.path.getsize(local_path)
        Color.info(f"上传 {Path(local_path).name} ({local_size / 1024 / 1024:.1f} MB) -> {remote_path}")

        # 确保远程目录存在
        remote_dir = os.path.dirname(remote_path)
        self.exec_cmd(f"mkdir -p {remote_dir}", timeout=10)

        # 上传
        start = time.time()
        sftp.put(local_path, remote_path)
        elapsed = time.time() - start
        speed = local_size / 1024 / 1024 / elapsed if elapsed > 0 else 0
        Color.ok(f"上传完成，用时 {elapsed:.1f}s，速度 {speed:.1f} MB/s")

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, *args):
        self.close()


# ============================================================
#  经验记录器（自进化核心）
# ============================================================

class ExperienceLogger:
    def __init__(self, config: dict, project_root: str):
        self.enabled = config.get('enabled', True)
        history_dir = config.get('history_dir', '.deploy-history')
        self.history_path = Path(project_root) / history_dir
        self.log_file = self.history_path / config.get('log_file', 'deploy-log.jsonl')
        self.issues_file = self.history_path / config.get('issues_file', 'known-issues.md')
        self.patterns_dir = self.history_path / config.get('patterns_dir', 'patterns')
        if self.enabled:
            self.history_path.mkdir(parents=True, exist_ok=True)
            self.patterns_dir.mkdir(parents=True, exist_ok=True)
            if not self.issues_file.exists():
                self.issues_file.write_text("# 已知问题库\n\n> 自动记录部署中遇到的问题和解决方案\n\n", encoding='utf-8')

    def log_deploy(self, service: str, status: str, duration: float, details: dict = None):
        """记录一次部署事件"""
        if not self.enabled:
            return
        record = {
            'timestamp': datetime.datetime.now().isoformat(),
            'service': service,
            'status': status,  # success / failed / rollback
            'duration_seconds': round(duration, 1),
            'details': details or {}
        }
        with open(self.log_file, 'a', encoding='utf-8') as f:
            f.write(json.dumps(record, ensure_ascii=False) + '\n')

    def add_issue(self, title: str, symptom: str, cause: str, solution: str, service: str = None):
        """添加一条已知问题（从失败中学习）"""
        if not self.enabled:
            return
        entry = f"""
## {datetime.datetime.now().strftime('%Y-%m-%d')} - {title}

**症状**: {symptom}

**服务**: {service or '通用'}

**根因**: {cause}

**解决方案**: {solution}

---
"""
        with open(self.issues_file, 'a', encoding='utf-8') as f:
            f.write(entry)
        Color.ok(f"已记录到问题库: {title}")

    def get_stats(self) -> dict:
        """获取部署统计"""
        if not self.enabled or not self.log_file.exists():
            return {}
        records = []
        with open(self.log_file, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line:
                    records.append(json.loads(line))
        if not records:
            return {}
        total = len(records)
        success = sum(1 for r in records if r['status'] == 'success')
        failed = sum(1 for r in records if r['status'] == 'failed')
        rollback = sum(1 for r in records if r['status'] == 'rollback')
        avg_duration = sum(r['duration_seconds'] for r in records) / total
        # 按服务统计
        by_service = {}
        for r in records:
            svc = r['service']
            if svc not in by_service:
                by_service[svc] = {'total': 0, 'success': 0, 'avg_duration': 0}
            by_service[svc]['total'] += 1
            if r['status'] == 'success':
                by_service[svc]['success'] += 1
            by_service[svc]['avg_duration'] += r['duration_seconds']
        for svc in by_service:
            by_service[svc]['avg_duration'] = round(
                by_service[svc]['avg_duration'] / by_service[svc]['total'], 1
            )
        return {
            'total': total,
            'success': success,
            'failed': failed,
            'rollback': rollback,
            'success_rate': round(success / total * 100, 1) if total > 0 else 0,
            'avg_duration': round(avg_duration, 1),
            'by_service': by_service
        }

    def recommend_strategy(self, service: str) -> str:
        """根据历史记录推荐部署策略"""
        stats = self.get_stats()
        if not stats:
            return "首次部署，建议先 status 勘察环境"
        svc_stats = stats.get('by_service', {}).get(service)
        if not svc_stats:
            return "该服务首次部署，建议使用热更新策略"
        if svc_stats['total'] >= 3 and svc_stats['success'] / svc_stats['total'] < 0.5:
            return f"⚠️  该服务部署成功率较低 ({svc_stats['success']}/{svc_stats['total']})，建议先 doctor 诊断"
        return f"历史平均用时 {svc_stats['avg_duration']}s，成功率 {round(svc_stats['success']/svc_stats['total']*100, 1)}%"


# ============================================================
#  Docker 热更新部署策略
# ============================================================

class DockerHotfixStrategy:
    def __init__(self, ssh: SSHManager, config: dict, exp_logger: ExperienceLogger):
        self.ssh = ssh
        self.config = config
        self.exp = exp_logger
        self.container_pattern = config.get('container_name_pattern', '{service}')
        self.jar_path_pattern = config.get('container_jar_path', '/app/{service}.jar')
        self.success_keywords = config.get('success_keywords', ['Started '])
        self.fail_keywords = config.get('fail_keywords', ['APPLICATION FAILED TO START'])
        self.startup_timeout = config.get('startup_timeout', 120)
        self.log_check_interval = config.get('log_check_interval', 5)

    def _container_name(self, service: str) -> str:
        return self.container_pattern.format(service=service)

    def _jar_path(self, service: str) -> str:
        return self.jar_path_pattern.format(service=service)

    def check_service_running(self, service: str) -> bool:
        """检查服务容器是否在运行"""
        container = self._container_name(service)
        code, out, _ = self.ssh.exec_cmd(f"docker ps --format '{{{{.Names}}}}' | grep -x {container}", timeout=10)
        return code == 0 and out.strip() == container

    def backup_jar(self, service: str) -> str:
        """备份容器内当前 jar 包，用于回滚"""
        container = self._container_name(service)
        backup_path = f"/tmp/{service}-backup.jar"
        code, _, err = self.ssh.exec_cmd(
            f"docker cp {container}:{self._jar_path(service)} {backup_path}",
            timeout=30
        )
        if code != 0:
            Color.warn(f"备份失败: {err.strip()[:100]}")
            return ""
        Color.info(f"已备份当前 jar 到 {backup_path}")
        return backup_path

    def hotfix(self, service: str, remote_jar: str) -> bool:
        """
        热更新单个服务：备份 -> 拷贝新 jar -> 重启 -> 监控启动 -> 失败则回滚
        返回 True 表示成功，False 表示失败（已自动回滚）
        """
        start_time = time.time()
        container = self._container_name(service)

        # 0. 勘察环境
        Color.step(f"部署服务: {service}")
        if not self.check_service_running(service):
            Color.fail(f"容器 {container} 未运行，无法热更新")
            self.exp.log_deploy(service, 'failed', time.time() - start_time,
                              {'reason': 'container not running'})
            return False

        # 1. 备份旧 jar
        backup_path = self.backup_jar(service)
        if not backup_path:
            Color.warn("备份失败，继续部署（但无法回滚）")

        # 2. 拷贝新 jar 进容器
        Color.info(f"拷贝新 jar 到容器 {container}...")
        code, _, err = self.ssh.exec_cmd(
            f"docker cp {remote_jar} {container}:{self._jar_path(service)}",
            timeout=30
        )
        if code != 0:
            Color.fail(f"拷贝失败: {err.strip()}")
            self.exp.log_deploy(service, 'failed', time.time() - start_time,
                              {'reason': 'docker cp failed', 'error': err.strip()})
            return False
        Color.ok("jar 包已更新")

        # 3. 重启容器
        Color.info(f"重启容器 {container}...")
        code, _, err = self.ssh.exec_cmd(f"docker restart {container}", timeout=30)
        if code != 0:
            Color.fail(f"重启失败: {err.strip()}")
            self._rollback(service, backup_path, start_time)
            return False
        Color.ok("容器已重启，正在监控启动状态...")

        # 4. 实时监控启动日志
        success = self._watch_startup(service)

        if success:
            duration = time.time() - start_time
            Color.ok(f"{service} 部署成功！用时 {duration:.1f}s")
            self.exp.log_deploy(service, 'success', duration)
            return True
        else:
            Color.fail(f"{service} 启动失败，正在回滚...")
            self._rollback(service, backup_path, start_time)
            return False

    def _watch_startup(self, service: str) -> bool:
        """监控启动日志，判断启动成功还是失败"""
        container = self._container_name(service)
        start_time = time.time()
        log_file = f"/tmp/{service}-startup.log"
        last_log_pos = 0

        # 清理旧的日志文件
        self.ssh.exec_cmd(f"rm -f {log_file}", timeout=5)

        # 用 --tail 0 -f 从最新位置开始 follow，不输出历史日志
        # 这样就不会匹配到旧的成功/失败关键词
        self.ssh.exec_cmd(
            f"docker logs --tail 0 -f {container} > {log_file} 2>&1 &",
            timeout=5
        )
        time.sleep(1)

        while time.time() - start_time < self.startup_timeout:
            # 读取新增日志
            new_logs, last_log_pos = self.ssh.tail_log(log_file, last_log_pos)
            if new_logs:
                # 打印最后几行
                lines = new_logs.strip().split('\n')
                for line in lines[-3:]:
                    if line.strip():
                        print(f"  📜 {line.strip()[:120]}")

                # 检查失败关键词
                for kw in self.fail_keywords:
                    if kw in new_logs:
                        Color.fail(f"检测到失败标志: {kw}")
                        return False

                # 检查成功关键词
                for kw in self.success_keywords:
                    if kw in new_logs:
                        return True

            time.sleep(self.log_check_interval)

        # 超时，再检查一下健康状态
        return self._check_health_docker(service)

    def _check_health_docker(self, service: str) -> bool:
        """用 docker 健康检查判断状态"""
        container = self._container_name(service)
        code, out, _ = self.ssh.exec_cmd(
            f"docker inspect {container} --format '{{{{json .State.Health.Status}}}}'",
            timeout=10
        )
        if code == 0 and '"healthy"' in out.strip():
            return True
        return False

    def _rollback(self, service: str, backup_path: str, start_time: float):
        """回滚到旧版本"""
        if not backup_path:
            Color.fail("没有备份，无法回滚！")
            self.exp.log_deploy(service, 'failed', time.time() - start_time,
                              {'reason': 'no backup'})
            return
        container = self._container_name(service)
        Color.warn(f"回滚 {service} 到备份版本...")
        code1, _, err1 = self.ssh.exec_cmd(
            f"docker cp {backup_path} {container}:{self._jar_path(service)}",
            timeout=30
        )
        code2, _, err2 = self.ssh.exec_cmd(f"docker restart {container}", timeout=30)
        if code1 == 0 and code2 == 0:
            # 等一会儿确认回滚成功
            time.sleep(30)
            if self._check_health_docker(service):
                Color.ok("回滚成功，服务已恢复")
                self.exp.log_deploy(service, 'rollback', time.time() - start_time,
                                  {'reason': 'startup failed'})
                return
        Color.fail(f"回滚失败: {err1.strip() if code1 != 0 else err2.strip()}")
        self.exp.log_deploy(service, 'failed', time.time() - start_time,
                          {'reason': 'rollback failed'})

    def get_status(self, service: str) -> str:
        """获取服务状态"""
        container = self._container_name(service)
        code, out, _ = self.ssh.exec_cmd(
            f"docker ps --format '{{{{.Names}}}} {{{{.Status}}}}' | grep {container}",
            timeout=10
        )
        if code != 0:
            return "NOT_FOUND"
        return out.strip()


# ============================================================
#  主部署引擎
# ============================================================

class DeployEngine:
    def __init__(self, config_path: str):
        self.config_path = Path(config_path)
        if not self.config_path.exists():
            Color.fail(f"配置文件不存在: {config_path}")
            sys.exit(1)
        with open(self.config_path, 'r', encoding='utf-8') as f:
            self.config = yaml.safe_load(f)

        self.project_name = self.config['project']['name']
        self.local_root = Path(self.config['project']['local_root'])
        self.remote_root = self.config['project']['remote_root']
        self.strategy_name = self.config['project'].get('deploy_strategy', 'docker-hotfix')

        # 初始化 SSH
        self.ssh = SSHManager(self.config['server'])

        # 初始化经验记录器
        self.exp = ExperienceLogger(
            self.config.get('evolution', {}),
            str(self.local_root)
        )

        # 初始化部署策略
        self.strategy = self._init_strategy()

    def _init_strategy(self):
        if self.strategy_name == 'docker-hotfix':
            return DockerHotfixStrategy(
                self.ssh,
                self.config.get('docker_hotfix', {}),
                self.exp
            )
        else:
            Color.fail(f"不支持的部署策略: {self.strategy_name}")
            sys.exit(1)

    def _get_artifact(self, service: str) -> Optional[dict]:
        """获取服务的构建产物配置"""
        for art in self.config['build'].get('artifacts', []):
            if art['service'] == service:
                return art
        return None

    def _get_all_services(self) -> List[str]:
        """获取所有服务名，按依赖顺序排序（先部署被依赖的）"""
        services = self.config.get('services', [])
        # 按 weight 从小到大排序（weight 小的先部署）
        services_sorted = sorted(services, key=lambda s: s.get('weight', 50))
        return [s['name'] for s in services_sorted]

    def _file_hash(self, path: str) -> str:
        """计算文件 MD5"""
        if not os.path.exists(path):
            return ""
        h = hashlib.md5()
        with open(path, 'rb') as f:
            while True:
                chunk = f.read(8192)
                if not chunk:
                    break
                h.update(chunk)
        return h.hexdigest()

    def _remote_file_hash(self, remote_path: str) -> str:
        """获取远程文件 MD5"""
        code, out, _ = self.ssh.exec_cmd(
            f"if [ -f {remote_path} ]; then md5sum {remote_path} | cut -d' ' -f1; else echo ''; fi",
            timeout=10
        )
        return out.strip()

    # ---- 命令实现 ----

    def cmd_status(self):
        """查看所有服务状态"""
        Color.banner(f"{self.project_name} - 服务状态")
        self.ssh.connect()

        # 基础设施
        print(f"{Color.BOLD}基础设施:{Color.END}")
        for infra in self.config.get('infrastructure', []):
            name = infra['name']
            container = infra['container']
            code, out, _ = self.ssh.exec_cmd(
                f"docker ps --format '{{{{.Status}}}}' --filter name={container}",
                timeout=10
            )
            status = out.strip() or "NOT RUNNING"
            icon = "✅" if "Up" in status and "healthy" in status else "⚠️" if "Up" in status else "❌"
            print(f"  {icon} {name:15s} {status}")

        # 应用服务
        print(f"\n{Color.BOLD}应用服务:{Color.END}")
        for svc in self._get_all_services():
            status = self.strategy.get_status(svc)
            icon = "✅" if "healthy" in status else "⚠️" if "Up" in status else "❌"
            print(f"  {icon} {svc:20s} {status}")

        # 模块状态（如果配置了）
        health_cfg = self.config.get('health_check', {})
        if health_cfg.get('mode') == 'http' and 'modules_endpoint' in health_cfg:
            base = health_cfg['http'].get('base_url', 'http://localhost:8090')
            endpoint = health_cfg['modules_endpoint']
            print(f"\n{Color.BOLD}模块状态:{Color.END}")
            code, out, _ = self.ssh.exec_cmd(f"curl -s --max-time 5 {base}{endpoint}", timeout=10)
            if code == 0 and out.strip():
                try:
                    modules = json.loads(out.strip())
                    for m in modules:
                        icon = "✅" if m.get('status') == 'UP' else "❌"
                        print(f"  {icon} {m['name']:20s} {m['status']} (instances: {m['instances']})")
                except json.JSONDecodeError:
                    print(f"  {out.strip()[:100]}")

    def cmd_build(self, services: List[str] = None):
        """本地编译打包"""
        Color.banner(f"{self.project_name} - 本地构建")
        build_cfg = self.config['build']

        if build_cfg['tool'] != 'maven':
            Color.fail(f"暂不支持构建工具: {build_cfg['tool']}")
            return False

        mvn_path = build_cfg['maven']['path']
        java_home = build_cfg['maven'].get('java_home', '')
        args = build_cfg['maven'].get('args', 'package -DskipTests')

        if services:
            # 只构建指定服务
            for svc in services:
                art = self._get_artifact(svc)
                if not art:
                    Color.warn(f"未找到服务 {svc} 的构建配置，跳过")
                    continue
                svc_dir = self.local_root / art['local_path'].split('/')[0]
                Color.step(f"构建 {svc}...")
                env = os.environ.copy()
                if java_home:
                    env['JAVA_HOME'] = java_home
                result = subprocess.run(
                    f'{mvn_path} {args}',
                    cwd=str(svc_dir),
                    shell=True,
                    env=env
                )
                if result.returncode != 0:
                    Color.fail(f"{svc} 构建失败")
                    return False
                Color.ok(f"{svc} 构建成功")
        else:
            # 全量构建
            Color.info("全量构建所有服务...")
            env = os.environ.copy()
            if java_home:
                env['JAVA_HOME'] = java_home
            # 先构建 kb-common
            common_dir = self.local_root / 'kb-common'
            if common_dir.exists():
                Color.step("构建 kb-common...")
                result = subprocess.run(
                    f'{mvn_path} install -DskipTests -q',
                    cwd=str(common_dir),
                    shell=True,
                    env=env
                )
                if result.returncode != 0:
                    Color.fail("kb-common 构建失败")
                    return False
                Color.ok("kb-common 构建成功")
            # 再构建各个服务
            for svc in self._get_all_services():
                art = self._get_artifact(svc)
                if not art:
                    continue
                svc_dir = self.local_root / art['local_path'].split('/')[0]
                Color.step(f"构建 {svc}...")
                result = subprocess.run(
                    f'{mvn_path} {args}',
                    cwd=str(svc_dir),
                    shell=True,
                    env=env
                )
                if result.returncode != 0:
                    Color.fail(f"{svc} 构建失败")
                    return False
                Color.ok(f"{svc} 构建成功")

        Color.ok("所有服务构建完成！")
        return True

    def cmd_deploy(self, services: List[str] = None):
        """部署服务"""
        Color.banner(f"{self.project_name} - 部署服务")
        self.ssh.connect()

        # 勘察环境
        Color.step("环境勘察")
        self.cmd_status()

        services_to_deploy = services or self._get_all_services()

        # 检查哪些服务需要更新（增量）
        Color.step("增量检查")
        need_update = []
        for svc in services_to_deploy:
            art = self._get_artifact(svc)
            if not art:
                Color.warn(f"未找到 {svc} 的构建配置，跳过")
                continue
            local_path = str(self.local_root / art['local_path'])
            remote_path = f"{self.remote_root}/{art['remote_path']}"

            if not os.path.exists(local_path):
                Color.warn(f"本地文件不存在: {local_path}，需要先 build")
                continue

            local_hash = self._file_hash(local_path)
            remote_hash = self._remote_file_hash(remote_path)

            if local_hash == remote_hash and remote_hash:
                Color.info(f"{svc}: 文件未变化，跳过 (hash: {local_hash[:8]})")
            else:
                need_update.append((svc, local_path, remote_path))
                Color.info(f"{svc}: 需要更新 (local: {local_hash[:8]}, remote: {remote_hash[:8] if remote_hash else 'none'})")

        if not need_update:
            Color.ok("所有服务都是最新的，无需部署")
            return True

        Color.info(f"需要更新 {len(need_update)} 个服务: {[s[0] for s in need_update]}")

        # 推荐策略
        for svc, _, _ in need_update:
            recommendation = self.exp.recommend_strategy(svc)
            print(f"  📊 {svc}: {recommendation}")

        # 逐个部署
        success_count = 0
        for svc, local_path, remote_path in need_update:
            # 上传
            Color.step(f"上传 {svc}")
            try:
                self.ssh.upload_file(local_path, remote_path)
            except Exception as e:
                Color.fail(f"上传失败: {e}")
                continue

            # 部署
            if self.strategy.hotfix(svc, remote_path):
                success_count += 1
            else:
                Color.fail(f"{svc} 部署失败")

        # 汇总
        Color.banner("部署完成")
        print(f"  总计: {len(need_update)} 个服务")
        print(f"  成功: {success_count}")
        print(f"  失败: {len(need_update) - success_count}")

        return success_count == len(need_update)

    def cmd_rollback(self, service: str):
        """回滚单个服务（从备份恢复）"""
        Color.step(f"回滚服务: {service}")
        self.ssh.connect()

        backup_path = f"/tmp/{service}-backup.jar"
        code, out, _ = self.ssh.exec_cmd(f"ls -lh {backup_path} 2>&1", timeout=10)
        if code != 0 or "No such" in out:
            Color.fail("没有找到备份文件，无法回滚")
            return False

        Color.info(f"找到备份: {out.strip()}")
        # 直接用 strategy 的回滚逻辑
        if hasattr(self.strategy, '_rollback'):
            start = time.time()
            self.strategy._rollback(service, backup_path, start)
            return True
        return False

    def cmd_logs(self, service: str, tail: int = 50):
        """查看服务日志"""
        self.ssh.connect()
        container = self.strategy._container_name(service)
        code, out, _ = self.ssh.exec_cmd(f"docker logs --tail {tail} {container} 2>&1", timeout=10)
        print(out)

    def cmd_history(self):
        """查看部署历史"""
        Color.banner(f"{self.project_name} - 部署历史")
        stats = self.exp.get_stats()
        if not stats:
            print("  暂无部署记录")
            return

        print(f"  总部署次数: {stats['total']}")
        print(f"  成功: {stats['success']}  失败: {stats['failed']}  回滚: {stats['rollback']}")
        print(f"  成功率: {stats['success_rate']}%")
        print(f"  平均用时: {stats['avg_duration']}s")
        print()
        print(f"{Color.BOLD}按服务统计:{Color.END}")
        for svc, s in sorted(stats['by_service'].items()):
            rate = round(s['success'] / s['total'] * 100, 1) if s['total'] > 0 else 0
            print(f"  {svc:20s} {s['total']} 次, 成功率 {rate}%, 平均 {s['avg_duration']}s")

    def cmd_issues(self):
        """查看已知问题库"""
        if not self.exp.issues_file.exists():
            print("暂无已知问题")
            return
        content = self.exp.issues_file.read_text(encoding='utf-8')
        print(content)

    def cmd_doctor(self):
        """环境诊断 + 优化建议"""
        Color.banner(f"{self.project_name} - 环境诊断")
        self.ssh.connect()
        issues_found = []

        # 1. SSH 连接
        Color.step("SSH 连接")
        try:
            self.ssh.exec_cmd("echo ok", timeout=5)
            Color.ok("SSH 连接正常")
        except Exception as e:
            Color.fail(f"SSH 连接失败: {e}")
            issues_found.append(("SSH 连接失败", str(e)))

        # 2. Docker 可用性
        Color.step("Docker 可用性")
        code, out, err = self.ssh.exec_cmd("docker info --format '{{.ServerVersion}}'", timeout=10)
        if code == 0 and out.strip():
            Color.ok(f"Docker 版本: {out.strip()}")
        else:
            Color.fail(f"Docker 不可用: {err.strip()}")
            issues_found.append(("Docker 不可用", err.strip()))

        # 3. 服务状态
        Color.step("服务健康检查")
        unhealthy = []
        for svc in self._get_all_services():
            status = self.strategy.get_status(svc)
            if "healthy" in status:
                pass
            elif "Up" in status:
                Color.warn(f"{svc}: 启动中或未配置健康检查")
            else:
                Color.fail(f"{svc}: 未运行")
                unhealthy.append(svc)
        if unhealthy:
            issues_found.append(("服务未运行", ", ".join(unhealthy)))
        else:
            Color.ok("所有服务运行正常")

        # 4. 磁盘空间
        Color.step("磁盘空间")
        code, out, _ = self.ssh.exec_cmd("df -h / | tail -1", timeout=10)
        if code == 0:
            parts = out.strip().split()
            if len(parts) >= 5:
                usage = parts[4]
                print(f"  根分区使用率: {usage}")
                if int(usage.rstrip('%')) > 80:
                    Color.warn("磁盘空间不足 20%，建议清理")
                    issues_found.append(("磁盘空间不足", f"使用率 {usage}"))
                else:
                    Color.ok("磁盘空间充足")

        # 5. 历史数据分析
        Color.step("部署历史分析")
        stats = self.exp.get_stats()
        if stats:
            print(f"  历史部署 {stats['total']} 次, 成功率 {stats['success_rate']}%")
            if stats['success_rate'] < 70:
                Color.warn("部署成功率较低，建议查看 known-issues.md")
                issues_found.append(("部署成功率低", f"{stats['success_rate']}%"))
            else:
                Color.ok("部署成功率良好")

            # 找出最容易失败的服务
            bad_services = []
            for svc, s in stats.get('by_service', {}).items():
                if s['total'] >= 2 and s['success'] / s['total'] < 0.7:
                    bad_services.append(svc)
            if bad_services:
                Color.warn(f"高频失败服务: {', '.join(bad_services)}")

        # 6. 优化建议
        Color.banner("优化建议")
        suggestions = [
            "使用增量部署，只上传变更的服务",
            "热更新前先本地验证配置正确性",
            "定期清理 /tmp 下的备份 jar 包",
            "为关键服务配置自动回滚和告警",
        ]
        for i, s in enumerate(suggestions, 1):
            print(f"  {i}. {s}")

        if issues_found:
            print(f"\n{Color.RED}发现 {len(issues_found)} 个问题:{Color.END}")
            for title, desc in issues_found:
                print(f"  - {title}: {desc}")

    def close(self):
        self.ssh.close()


# ============================================================
#  CLI 入口
# ============================================================

def main():
    parser = argparse.ArgumentParser(
        description='通用部署引擎 - 配置化、自进化的部署工具',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  %(prog)s status                    查看所有服务状态
  %(prog)s build                     本地编译打包所有服务
  %(prog)s build --service kb-file   只构建指定服务
  %(prog)s deploy --all              部署所有服务（自动增量）
  %(prog)s deploy --service kb-file  部署单个服务
  %(prog)s rollback --service kb-file 回滚单个服务
  %(prog)s logs --service kb-file    查看服务日志
  %(prog)s history                   查看部署历史统计
  %(prog)s issues                    查看已知问题库
  %(prog)s doctor                    环境诊断 + 优化建议
        """
    )
    parser.add_argument('-c', '--config', default='deploy-config.yml',
                        help='配置文件路径（默认: deploy-config.yml）')

    sub = parser.add_subparsers(dest='command', required=True)

    sub.add_parser('status', help='查看服务状态')

    p_build = sub.add_parser('build', help='本地编译打包')
    p_build.add_argument('--service', action='append', help='指定服务（可多次指定）')

    p_deploy = sub.add_parser('deploy', help='部署服务')
    p_deploy.add_argument('--all', action='store_true', help='部署所有服务')
    p_deploy.add_argument('--service', action='append', help='指定服务（可多次指定）')

    p_rollback = sub.add_parser('rollback', help='回滚服务')
    p_rollback.add_argument('--service', required=True, help='服务名')

    p_logs = sub.add_parser('logs', help='查看服务日志')
    p_logs.add_argument('--service', required=True, help='服务名')
    p_logs.add_argument('--tail', type=int, default=50, help='行数（默认 50）')

    sub.add_parser('history', help='查看部署历史')
    sub.add_parser('issues', help='查看已知问题库')
    sub.add_parser('doctor', help='环境诊断')

    args = parser.parse_args()

    # 初始化引擎
    engine = DeployEngine(args.config)

    try:
        if args.command == 'status':
            engine.cmd_status()

        elif args.command == 'build':
            services = getattr(args, 'service', None)
            engine.cmd_build(services)

        elif args.command == 'deploy':
            if args.all:
                engine.cmd_deploy()
            elif args.service:
                engine.cmd_deploy(args.service)
            else:
                Color.fail("请指定 --all 或 --service")
                sys.exit(1)

        elif args.command == 'rollback':
            engine.cmd_rollback(args.service)

        elif args.command == 'logs':
            engine.cmd_logs(args.service, args.tail)

        elif args.command == 'history':
            engine.cmd_history()

        elif args.command == 'issues':
            engine.cmd_issues()

        elif args.command == 'doctor':
            engine.cmd_doctor()

    finally:
        engine.close()


if __name__ == '__main__':
    main()
