import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from deploy_engine import ExperienceLogger, Color

exp_config = {
    'enabled': True,
    'history_dir': '.deploy-history',
    'log_file': 'deploy-log.jsonl',
    'issues_file': 'known-issues.md',
    'patterns_dir': 'patterns',
}
exp = ExperienceLogger(exp_config, str(Path(__file__).parent))

exp.add_issue(
    title="启动监控误判：历史日志匹配到旧的成功关键词",
    symptom="热更新后监控启动状态，几秒内就报告'启动成功'，但实际服务还在启动中。日志显示匹配到了上一次启动的 Started Kb 关键词。",
    cause="使用 docker logs -f 时默认输出全部历史日志，然后才开始 follow 新日志。历史日志中包含了上一次启动的成功关键词，导致误判。",
    solution="使用 docker logs --tail 0 -f <container>，--tail 0 表示从第 0 行历史开始（即不输出任何历史日志），只 follow 新产生的日志。这是 docker 原生支持的参数，最可靠。不要用「等几秒再读」「按字节偏移量跳过历史」这类 hack 方案。",
    service="通用"
)

Color.ok("已记录到问题库")
