"""PDF 抽取器(基于 PyMuPDF/fitz,离线、快)。"""
from __future__ import annotations
from pathlib import Path
from omnifind.extractors import BaseExtractor, ExtractResult, register


@register
class PdfExtractor(BaseExtractor):
    EXTENSIONS = [".pdf"]
    PRIORITY = 10

    def extract(self, path: Path) -> ExtractResult:
        import fitz  # PyMuPDF
        parts = []
        with fitz.open(path) as doc:
            title = doc.metadata.get("title") or path.stem
            for page in doc:
                parts.append(page.get_text("text"))
            meta = {"pages": doc.page_count, "ext": ".pdf"}
        return ExtractResult(text="\n".join(parts), title=title, metadata=meta)
