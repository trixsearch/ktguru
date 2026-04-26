"""Embeddings, FAISS vector store, and ChatGroq RAG."""
from __future__ import annotations

import asyncio
import json
import logging
import re
import threading
from typing import Any

from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS
from langchain_groq import ChatGroq
from langchain_core.messages import HumanMessage, SystemMessage

from config import settings

logger = logging.getLogger(__name__)

RAG_SYSTEM_PROMPT = """You are a Senior Software Engineer on a knowledge transfer (KT) support team. \
Your job is to answer the user's question using ONLY the information in the "Context" section below. \
Be precise, technical, and concise.

Rules:
- If the context does not contain enough information to give a correct or safe answer, you MUST set "sufficient" to false in your JSON and write a short message in "answer" asking the user for clarification or what additional material is needed. Do not invent facts.
- If the context is enough, set "sufficient" to true and put the full answer in "answer".
- "sources" must be a list of source filenames (strings) that were actually useful from the context metadata.
- Output MUST be a single JSON object, no markdown fences, no text before or after the JSON."""


def _parse_json_object(text: str) -> dict[str, Any] | None:
    text = text.strip()
    m = re.search(r"\{[\s\S]*\}\s*$", text)
    if m:
        try:
            return json.loads(m.group(0))
        except json.JSONDecodeError:
            pass
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return None


class AIService:
    def __init__(self) -> None:
        self._embeddings = HuggingFaceEmbeddings(
            model_name=settings.EMBEDDING_MODEL,
            model_kwargs={"device": "cpu"},
        )
        self._faiss_path = settings.FAISS_DIR
        self._vectorstore: FAISS | None = None
        self._lock = threading.Lock()
        self._llm: ChatGroq | None = None

    def _ensure_llm(self) -> ChatGroq:
        if not settings.GROQ_API_KEY:
            raise ValueError("GROQ_API_KEY is not set")
        if self._llm is None:
            self._llm = ChatGroq(
                model=settings.GROQ_MODEL,
                api_key=settings.GROQ_API_KEY,
                temperature=0.2,
            )
        return self._llm

    def _load_or_init_faiss(self) -> None:
        with self._lock:
            if self._vectorstore is not None:
                return
            index_faiss = self._faiss_path / "index.faiss"
            if index_faiss.is_file():
                self._vectorstore = FAISS.load_local(
                    str(self._faiss_path),
                    self._embeddings,
                    allow_dangerous_deserialization=True,
                )
                logger.info("Loaded FAISS index from %s", self._faiss_path)
            else:
                self._vectorstore = None
                logger.info("No FAISS index on disk yet at %s", self._faiss_path)

    def initialize(self) -> None:
        self._faiss_path.mkdir(parents=True, exist_ok=True)
        self._load_or_init_faiss()

    def _add_texts_sync(
        self,
        texts: list[str],
        metadatas: list[dict[str, Any]],
    ) -> None:
        with self._lock:
            if not texts:
                return
            if self._vectorstore is None:
                self._vectorstore = FAISS.from_texts(
                    texts,
                    self._embeddings,
                    metadatas=metadatas,
                )
            else:
                self._vectorstore.add_texts(texts, metadatas=metadatas)
            self._vectorstore.save_local(str(self._faiss_path))
            logger.info("FAISS index updated: %d new chunks, saved to %s", len(texts), self._faiss_path)

    async def add_embedded_chunks(
        self,
        texts: list[str],
        metadatas: list[dict[str, Any]],
    ) -> None:
        if len(texts) != len(metadatas):
            raise ValueError("texts and metadatas must have the same length")
        await asyncio.to_thread(self._add_texts_sync, texts, metadatas)

    def _retrieve_and_rag_sync(self, query: str) -> tuple[str, list[str], bool]:
        """
        Returns (answer, sources, insufficient_context).
        insufficient_context True means caller should log to unanswered_questions.
        """
        if self._vectorstore is None:
            return (
                "No knowledge base has been uploaded yet. Please upload a text document first.",
                [],
                False,
            )

        docs = self._vectorstore.similarity_search(query, k=settings.FAISS_TOP_K)
        if not docs:
            return (
                "I could not find relevant information in the knowledge base for your question. "
                "Please upload more documentation or rephrase your question, including product or module names.",
                [],
                True,
            )

        context_blocks: list[str] = []
        source_names: list[str] = []
        for i, d in enumerate(docs, start=1):
            meta = d.metadata or {}
            fname = str(meta.get("filename", "unknown"))
            cid = str(meta.get("chunk_id", ""))
            context_blocks.append(f"[Source {i}: {fname} | chunk {cid}]\n{d.page_content}")
            if isinstance(fname, str) and fname not in source_names:
                source_names.append(fname)

        context = "\n\n---\n\n".join(context_blocks)
        user_content = f"""Context:\n{context}\n\nUser question: {query}\n\nRespond with JSON only:
{{"sufficient": true, "answer": "...", "sources": ["..."]}}"""

        llm = self._ensure_llm()
        messages = [
            SystemMessage(content=RAG_SYSTEM_PROMPT),
            HumanMessage(content=user_content),
        ]
        out = llm.invoke(messages)
        content = (out.content or "").strip() if hasattr(out, "content") else str(out)
        data = _parse_json_object(content)

        if data is not None and isinstance(data.get("answer"), str):
            sufficient = data.get("sufficient", True)
            if not isinstance(sufficient, bool):
                sufficient = str(sufficient).lower() in ("true", "1", "yes")
            answer = data["answer"].strip()
            src = data.get("sources")
            if isinstance(src, list):
                out_sources = [str(s) for s in src if s]
            else:
                out_sources = source_names
            return (answer, out_sources, not sufficient)

        # Model did not return valid JSON: treat as best-effort answer, do not log as unanswered
        return (content or "Unable to parse model response.", source_names, False)

    async def ask(
        self,
        query: str,
    ) -> tuple[str, list[str], bool]:
        return await asyncio.to_thread(self._retrieve_and_rag_sync, query)
