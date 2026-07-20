"""纯文本 & 代码文件抽取器。"""
from __future__ import annotations
from pathlib import Path
from omnifind.extractors import BaseExtractor, ExtractResult, register

TEXT_EXTS = [
    ".txt", ".md", ".csv", ".log", ".rtf",
    ".py", ".js", ".ts", ".java", ".c", ".cpp", ".h", ".go", ".rs",
    ".json", ".yaml", ".yml", ".xml", ".ini", ".toml", ".sql",
    ".sh", ".bat", ".ps1", ".html", ".htm",
]


@register
class PlainTextExtractor(BaseExtractor):
    EXTENSIONS = TEXT_EXTS
    PRIORITY = 0

    def extract(self, path: Path) -> ExtractResult:
        # 尝试多种编码,离线环境常见 utf-8 / gbk
        raw = path.read_bytes()
        for enc in ("utf-8", "gb18030", "utf-16", "latin-1"):
            try:
                text = raw.decode(enc)
                return ExtractResult(text=text, title=path.stem,
                                     metadata={"encoding": enc, "ext": path.suffix.lower()})
            except UnicodeDecodeError:
                continue
        return ExtractResult(ok=False, error="decode failed (all encodings)")
