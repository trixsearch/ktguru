"""
KT Guru — FastAPI RAG API for knowledge transfer.
"""
from __future__ import annotations

import logging
import re
from contextlib import asynccontextmanager
from typing import Any

from fastapi import Depends, FastAPI, File, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from langchain_text_splitters import RecursiveCharacterTextSplitter
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from config import settings
from database import get_db
from models import Document, DocumentChunk, UnansweredQuestion
from schemas import AskRequest, AskResponse
from services.ai_service import AIService
import os
import certifi

os.environ["SSL_CERT_FILE"] = certifi.where()
os.environ["REQUESTS_CA_BUNDLE"] = certifi.where()

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

ai_service: AIService | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global ai_service
    ai_service = AIService()
    ai_service.initialize()
    logger.info("KT Guru API startup complete (FAISS dir: %s)", settings.FAISS_DIR)
    yield
    ai_service = None


app = FastAPI(
    title="KT Guru",
    description="Knowledge Transfer RAG — document upload and Q&A",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
def root() -> dict[str, str]:
    return {"service": "KT Guru API", "docs": "/docs"}


TEXT_FILENAME_RE = re.compile(r"^[\w\-. ]+\.txt$", re.IGNORECASE)
MAX_UPLOAD_BYTES = 5 * 1024 * 1024


def get_ai() -> AIService:
    if ai_service is None:
        raise HTTPException(status_code=503, detail="AI service not ready")
    return ai_service


async def get_ai_dep(
    _req: Request,
) -> AIService:
    s = get_ai()
    return s


def _split_document(text: str) -> list[str]:
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=1000,
        chunk_overlap=200,
        length_function=len,
        is_separator_regex=False,
    )
    return splitter.split_text(text)


@app.post("/api/upload")
async def upload_document(
    file: UploadFile = File(..., description="Plain text .txt file"),
    db: AsyncSession = Depends(get_db),
    ai: AIService = Depends(get_ai_dep),
) -> dict[str, Any]:
    if not file.filename:
        raise HTTPException(status_code=400, detail="Filename is required")
    if not TEXT_FILENAME_RE.match(file.filename):
        raise HTTPException(
            status_code=400,
            detail="Only .txt filenames with safe characters are accepted",
        )
    raw = await file.read()
    if len(raw) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="File too large (max 5 MB)")
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise HTTPException(
            status_code=400,
            detail="File must be valid UTF-8 text",
        ) from None

    chunks = _split_document(text)
    if not chunks:
        raise HTTPException(status_code=400, detail="No text content to index after splitting")

    doc = Document(filename=file.filename)
    db.add(doc)
    await db.flush()
    await db.refresh(doc)

    meta_batch: list[dict[str, Any]] = []
    text_batch: list[str] = []

    for i, chunk_text in enumerate(chunks):
        db.add(
            DocumentChunk(
                document_id=doc.id,
                chunk_text=chunk_text,
                chunk_index=i,
            )
        )
    await db.flush()

    res = await db.execute(
        select(DocumentChunk)
        .where(DocumentChunk.document_id == doc.id)
        .order_by(DocumentChunk.chunk_index)
    )
    row_chunks = list(res.scalars().all())
    for ch in row_chunks:
        text_batch.append(ch.chunk_text)
        meta_batch.append(
            {
                "filename": file.filename,
                "document_id": str(doc.id),
                "chunk_id": str(ch.id),
                "chunk_index": str(ch.chunk_index or 0),
            }
        )

    await db.commit()

    await ai.add_embedded_chunks(text_batch, meta_batch)

    return {
        "status": "ok",
        "document_id": doc.id,
        "filename": file.filename,
        "chunks": len(chunks),
    }


@app.post("/api/ask", response_model=AskResponse)
async def ask(
    body: AskRequest,
    db: AsyncSession = Depends(get_db),
    ai: AIService = Depends(get_ai_dep),
) -> AskResponse:
    if not settings.GROQ_API_KEY:
        raise HTTPException(
            status_code=503,
            detail="GROQ_API_KEY is not configured on the server",
        )

    q = body.query.strip()
    if not q:
        raise HTTPException(status_code=400, detail="query must not be empty")

    answer, sources, log_unanswered = await ai.ask(q)

    if log_unanswered:
        uq = UnansweredQuestion(
            question=q,
            status="UNRESOLVED",
        )
        db.add(uq)
        await db.commit()

    return AskResponse(answer=answer, sources=sources)


@app.get("/api/health")
async def health(
    db: AsyncSession = Depends(get_db),
) -> dict[str, str]:
    await db.execute(select(Document).limit(1))
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
    )
