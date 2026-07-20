"""
L3 语义层 —— 本地 ONNX 嵌入器(bge-small-zh-v1.5)。

纯 CPU、离线、自包含。模型文件放 models/bge-small-zh-v1.5/:
  model.onnx        —— 量化后的 ONNX 模型(~90MB int8)
  tokenizer.json    —— HF tokenizers 快速分词器

嵌入流程:文本 -> tokenizer 编码 -> onnxruntime 推理 -> [CLS] 池化 -> L2 归一化。
BGE 系列约定:检索 query 前加指令前缀提升召回;doc 不加。
"""
from __future__ import annotations
from pathlib import Path

import numpy as np

# BGE-zh 检索指令前缀(官方推荐)
QUERY_INSTRUCTION = "为这个句子生成表示以用于检索相关文章:"


class OnnxEmbedder:
    def __init__(self, model_dir: str | Path, max_length: int = 512):
        self.model_dir = Path(model_dir)
        self.max_length = max_length
        self._session = None
        self._tokenizer = None

    def _lazy_load(self):
        if self._session is not None:
            return
        import onnxruntime as ort
        from tokenizers import Tokenizer

        model_path = self.model_dir / "model.onnx"
        tok_path = self.model_dir / "tokenizer.json"
        if not model_path.exists():
            raise FileNotFoundError(
                f"ONNX 模型缺失:{model_path}。请先运行 scripts/fetch_model.py 下载 bge-small-zh。"
            )
        so = ort.SessionOptions()
        so.intra_op_num_threads = 4
        so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self._session = ort.InferenceSession(
            str(model_path), sess_options=so, providers=["CPUExecutionProvider"]
        )
        self._tokenizer = Tokenizer.from_file(str(tok_path))
        self._tokenizer.enable_truncation(max_length=self.max_length)
        self._tokenizer.enable_padding()
        # 探测模型需要哪些输入(有的导出带 token_type_ids,有的不带)
        self._input_names = {i.name for i in self._session.get_inputs()}

    def encode(self, texts: list[str], is_query: bool = False) -> np.ndarray:
        """批量编码,返回 (N, dim) 的归一化向量。"""
        self._lazy_load()
        if is_query:
            texts = [QUERY_INSTRUCTION + t for t in texts]
        encs = self._tokenizer.encode_batch(texts)
        ids = np.array([e.ids for e in encs], dtype=np.int64)
        mask = np.array([e.attention_mask for e in encs], dtype=np.int64)
        feed = {"input_ids": ids, "attention_mask": mask}
        if "token_type_ids" in self._input_names:
            feed["token_type_ids"] = np.zeros_like(ids)
        # 只喂模型实际需要的输入
        feed = {k: v for k, v in feed.items() if k in self._input_names}
        outputs = self._session.run(None, feed)
        last_hidden = outputs[0]  # (N, seq, dim)
        # BGE 用 [CLS] 池化(取第 0 个 token)
        cls = last_hidden[:, 0, :]
        # L2 归一化
        norms = np.linalg.norm(cls, axis=1, keepdims=True)
        norms[norms == 0] = 1e-9
        return (cls / norms).astype(np.float32)

    def encode_one(self, text: str, is_query: bool = False) -> np.ndarray:
        return self.encode([text], is_query=is_query)[0]

    @property
    def dim(self) -> int:
        self._lazy_load()
        # bge-small-zh = 512 维
        return self._session.get_outputs()[0].shape[-1] or 512
