"""
下载并导出 bge-small-zh-v1.5 的 ONNX 模型到 models/。

策略(离线自包含目标):
  1. 优先从 HuggingFace 下载已导出的 ONNX 版本(若可达)。
  2. HF 不可达时,支持环境变量 HF_ENDPOINT 指向镜像(如 hf-mirror.com)。
  3. 也支持从本地路径 --from-local 拷贝(内网已有模型时)。

产物:models/bge-small-zh-v1.5/{model.onnx, tokenizer.json}
"""
from __future__ import annotations
import argparse
import os
import shutil
import sys
from pathlib import Path

MODEL_DIR = Path(__file__).resolve().parent.parent / "models" / "bge-small-zh-v1.5"
REPO = "BAAI/bge-small-zh-v1.5"


def via_optimum():
    """用 optimum 把 HF 模型导出为 ONNX(需 optimum[onnxruntime])。"""
    from optimum.onnxruntime import ORTModelForFeatureExtraction
    from transformers import AutoTokenizer

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    print(f"从 {REPO} 导出 ONNX(endpoint={os.environ.get('HF_ENDPOINT','default')})…")
    model = ORTModelForFeatureExtraction.from_pretrained(REPO, export=True)
    model.save_pretrained(MODEL_DIR)
    tok = AutoTokenizer.from_pretrained(REPO)
    tok.save_pretrained(MODEL_DIR)
    # 统一文件名为 model.onnx
    for f in MODEL_DIR.glob("*.onnx"):
        if f.name != "model.onnx":
            f.rename(MODEL_DIR / "model.onnx")
    print(f"完成 -> {MODEL_DIR}")


def from_local(src: str):
    src_p = Path(src)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    for fn in ("model.onnx", "tokenizer.json"):
        s = src_p / fn
        if not s.exists():
            print(f"⚠️ 源缺少 {fn}")
            continue
        shutil.copy2(s, MODEL_DIR / fn)
    print(f"从本地拷贝完成 -> {MODEL_DIR}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-local", help="从本地目录拷贝 model.onnx + tokenizer.json")
    ap.add_argument("--mirror", action="store_true", help="用 hf-mirror.com 镜像")
    args = ap.parse_args()

    if args.from_local:
        from_local(args.from_local)
    else:
        if args.mirror:
            os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"
        via_optimum()
