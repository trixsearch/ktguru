# KT Guru — Backend

FastAPI service for document ingestion, vector embedding, and RAG (retrieval-augmented generation) Q&A.

## Stack

| Layer | Technology |
|--------|------------|
| API | [FastAPI](https://fastapi.tiangolo.com/) |
| RAG & chunking | [LangChain](https://www.langchain.com/) (`RecursiveCharacterTextSplitter`, community integrations) |
| Vector store | [FAISS](https://github.com/facebookresearch/faiss) (persisted on disk) |
| Embeddings | [sentence-transformers](https://www.sbert.net/) via `HuggingFaceEmbeddings` (default: `sentence-transformers/all-MiniLM-L6-v2`) |
| ORM & DB | [SQLAlchemy 2](https://www.sqlalchemy.org/) (async) with [MySQL](https://www.mysql.com/) (driver: [aiomysql](https://github.com/aio-libs/aiomysql) / [PyMySQL](https://github.com/PyMySQL/PyMySQL)) |
| LLM | [Groq](https://groq.com/) via [langchain-groq](https://python.langchain.com/docs/integrations/llms/groq) (`ChatGroq`) |

Ingestion may use **pandas** (CSV, Excel), **openpyxl** (`.xlsx`), **python-docx** (`.docx`) in addition to UTF-8 text and code files. Legacy **.xls** files use `pandas.read_excel(..., engine="xlrd")` with **xlrd 1.2.x** (pinned below 2.0, because **xlrd** 2+ no longer supports `.xls`).

## Environment variables (`.env`)

Create `ktguru_backend/.env` in this directory. All values can be overridden by the environment; defaults are defined in `config.py`.

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | Async MySQL URL, e.g. `mysql+aiomysql://user:pass@host:3306/kt_guru?charset=utf8mb4` |
| `GROQ_API_KEY` | Groq API key (required for `/api/ask`) |
| `GROQ_MODEL` | Model id (default: `llama-3.3-70b-versatile`) |
| `EMBEDDING_MODEL` | Hugging Face model id for embeddings |
| `FAISS_DIR` | Directory for the FAISS index files (default: `data/faiss_index` under this package) |
| `FAISS_TOP_K` | Number of similar chunks to retrieve (default: `3`) |
| `CORS_ORIGINS` | Comma-separated list of allowed browser origins (e.g. Vite: `http://127.0.0.1:5173`) |

`pydantic-settings` loads `.env` automatically; keep secrets out of version control.

## Custom SSL / HTTP client (Groq)

On some **corporate networks** or with certain TLS setups, the default certificate bundle may not succeed when the Groq API is called. The LLM is constructed in `services/ai_service.py` with an `httpx.Client(verify=False)` **custom HTTP client** passed into `ChatGroq`’s `http_client` so requests can complete in those environments.

> **Security note:** Disabling TLS verification reduces protection against man-in-the-middle attacks. Use only in controlled or debugging contexts; for production, prefer proper trust store configuration and `verify=True` when your environment allows it.

`main.py` also sets `SSL_CERT_FILE` and `REQUESTS_CA_BUNDLE` to [certifi](https://github.com/certifi/python-certifi)’s bundle for other libraries that respect those variables.

## API routes

### `POST /api/upload`

Multipart form upload: field name `file`.

- **Accepted formats:** plain text and UTF-8 code (`.txt`, `.py`, `.java`, `.js`, `.xml`, `.yaml`, `.json`, `.html`), `.csv` (read with pandas, then `DataFrame.to_string()`), `.xlsx` / `.xls` (pandas + openpyxl/xlrd), `.docx` (paragraph text via `python-docx`). Filenames must use safe characters and match the allowed pattern (see `main.py`). Maximum size: **5 MB** per file.
- **Behavior:** Text is passed through the same **chunking → MySQL + FAISS** pipeline as the original text-only uploader. Success returns JSON including `status`, `document_id`, `filename`, and `chunks` count.

### `POST /api/ask`

JSON body: `{ "query": "your question" }`.

- Requires `GROQ_API_KEY` on the server. Retrieves top chunks from FAISS, assembles context with metadata, invokes Groq with the RAG system prompt, and returns structured answer and sources. Unanswerable or insufficient-context cases may be logged to `unanswered_questions` (see `models`).

### Other

- `GET /` — Short service message and link to OpenAPI.
- `GET /api/health` — Liveness/DB check.

## Run locally

```bash
cd ktguru_backend
python -m venv .venv
# activate venv, then:
pip install -r requirements.txt
# copy/configure .env
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

Interactive API docs: `http://127.0.0.1:8000/docs`

## Database

Ensure the MySQL schema matches your `models` (migrations/DDL are project-specific; create tables or run your migration tool before first use).

The FAISS directory is created automatically if missing; the index is updated after each successful upload that produces chunks.
