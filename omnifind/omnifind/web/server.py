"""
OmniFind Web 服务 —— FastAPI,监听 127.0.0.1,提供查询 API + 静态 UI。
"""
from __future__ import annotations
from pathlib import Path
from contextlib import asynccontextmanager

from fastapi import FastAPI, Query
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from omnifind.core.config import OmniConfig, ensure_dirs
from omnifind.core.router import QueryRouter
from omnifind.layers.l1_filename.index import FilenameIndex
from omnifind.layers.l2_fulltext.index import FullTextIndex

STATIC_DIR = Path(__file__).resolve().parent / "static"

# 全局单例(简单起见;后续可换依赖注入)
_state: dict = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    ensure_dirs()
    cfg = OmniConfig.load()
    l1 = FilenameIndex()
    l2 = FullTextIndex()
    l3 = None
    try:
        from pathlib import Path as _P
        if _P(cfg.embed_model_path).joinpath("model.onnx").exists():
            from omnifind.layers.l3_semantic.builder import make_embedder
            from omnifind.layers.l3_semantic.index import SemanticIndex
            emb = make_embedder(cfg)
            l3 = SemanticIndex(emb, dim=emb.dim)
    except Exception as e:  # noqa: BLE001
        print(f"[L3] 语义层不可用(跳过):{e}")
    router = QueryRouter(l1=l1, l2=l2, l3=l3)
    _state.update(cfg=cfg, l1=l1, l2=l2, l3=l3, router=router)
    yield
    l1.close()
    l2.close()


app = FastAPI(title="OmniFind", version="0.1.0", lifespan=lifespan)


@app.get("/api/search")
def search(q: str = Query(""), limit: int = Query(30, ge=1, le=200)):
    router: QueryRouter = _state["router"]
    resp = router.search(q, limit=limit)
    return JSONResponse(resp.to_dict())


@app.get("/api/status")
def status():
    l3 = _state.get("l3")
    return {
        "l1_count": _state["l1"].count(),
        "l2_count": _state["l2"].count(),
        "l3_count": l3.count() if l3 else 0,
        "scan_roots": _state["cfg"].scan_roots,
    }


@app.get("/")
def index():
    return FileResponse(STATIC_DIR / "index.html")


# 静态资源
if STATIC_DIR.exists():
    app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")


def main():
    import uvicorn
    cfg = OmniConfig.load()
    uvicorn.run("omnifind.web.server:app", host=cfg.host, port=cfg.port, reload=False)


if __name__ == "__main__":
    main()
