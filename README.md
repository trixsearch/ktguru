# KT Guru

**KT Guru** is an intelligent **knowledge transfer** assistant and **senior engineer**–style Q&A system. It ingests your documents into a searchable vector store and answers questions using only that context, so teams can onboard, document handovers, and resolve “how does this work?” without hunting through file shares or chat history.

## Architecture

- **Frontend** (`ktguru_frontend/`): A single-page [React](https://react.dev/) app built with [Vite](https://vitejs.dev/) and [Tailwind CSS v4](https://tailwindcss.com/), using [Axios](https://axios-http.com/) to call the API.
- **Backend** (`ktguru_backend/`): A [FastAPI](https://fastapi.tiangolo.com/) service that:
  - Accepts document uploads, extracts text (plain text, code, CSV, Excel, Word), chunks it with [LangChain](https://www.langchain.com/)’s `RecursiveCharacterTextSplitter`, and stores rows in [SQLAlchemy](https://www.sqlalchemy.org/)-backed [MySQL](https://www.mysql.com/) and embeddings in a local [FAISS](https://github.com/facebookresearch/faiss) index.
  - Answers questions with retrieval-augmented generation: embedding similarity over FAISS, then [Groq](https://groq.com/) (via LangChain) with strict “context only” JSON-style prompts.

## Prerequisites

- **Python 3.10+** and **Node.js 18+** (for the frontend tooling).
- **MySQL** reachable with the connection string in `ktguru_backend/.env` (see [Backend README](ktguru_backend/README.md)).
- A **Groq API key** in `ktguru_backend/.env` for `/api/ask`.
- A modern browser.

## Run the application

### 1. Backend API

```bash
cd ktguru_backend
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
# Configure .env (see ktguru_backend/README.md)
uvicorn main:app --host 0.0.0.0 --port 8000
```

The API will serve interactive docs at `http://127.0.0.1:8000/docs`.

### 2. Frontend (development)

In a separate terminal:

```bash
cd ktguru_frontend
npm install
npm run dev
```

The dev server defaults to [Vite’s](https://vitejs.dev/config/server-options.html) host/port (commonly `http://127.0.0.1:5173`). The frontend is configured to call the backend at `http://127.0.0.1:8000` for upload and Q&A; ensure the backend is running and that `CORS_ORIGINS` in the backend includes your dev URL if you change the port or host.

### End-to-end flow

1. Start **MySQL**, the **FastAPI** server, and the **Vite** dev server.
2. Use **Upload Document** in the app sidebar to add files to the knowledge base.
3. Ask questions in the main chat; answers are grounded in uploaded content via FAISS + Groq.

For environment variables, API details, and SSL notes for the Groq client, see [ktguru_backend/README.md](ktguru_backend/README.md). For UI and frontend tooling, see [ktguru_frontend/README.md](ktguru_frontend/README.md).
