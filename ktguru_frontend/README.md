# KT Guru — Frontend

The **KT Guru** web UI: a **React** app for **uploading** documents into the knowledge base (sidebar) and **chatting** with the RAG backend (main area), built for a **modern** developer workflow with **Vite** and **Tailwind CSS v4**.

## Stack

| Area | Technology |
|------|------------|
| UI library | [React 19](https://react.dev/) |
| Build & dev server | [Vite 8](https://vitejs.dev/) with [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react) |
| Styling | [Tailwind CSS v4](https://tailwindcss.com/) via `@tailwindcss/postcss` and [PostCSS](https://postcss.org/) (see `postcss.config.js`, `src/index.css`) |
| HTTP | [Axios](https://axios-http.com/) for `POST` calls to the FastAPI backend |
| Icons | [Lucide React](https://lucide.dev/) |

[ESLint](https://eslint.org/) is set up for code quality; run `npm run lint` in this directory.

## Layout and behavior

- **Sidebar (left)**
  - **Branding** — “KT Guru” and icon.
  - **New chat** — Resets the conversation to the default assistant greeting.
  - **Knowledge base** — **Upload Document** opens a file picker; the selected file is sent as `multipart/form-data` to the backend’s `POST /api/upload` endpoint. On success, the file is ingested and indexed (see backend README).

- **Main area (right)**
  - **Message list** — User messages and assistant replies, with a scroll anchor at the latest message.
  - **Composer** — Text input and send button. Messages are sent to `POST /api/ask` with `{ "query": "<text>" }` and the returned `answer` is shown in the thread.

The default hard-coded API base URL in `src/App.jsx` is `http://127.0.0.1:8000`. For different hosts or production, adjust that or move it to a `Vite` `import.meta.env` variable in `vite.config` / `.env` as you prefer.

## Vite setup

- **ES modules** — `"type": "module"` in `package.json` for native ESM.
- **Entry** — `index.html` at the project root; Vite processes `src/main.jsx` and hot-reloads during `npm run dev`.
- **Tailwind v4** — Use `@import "tailwindcss";` in `src/index.css` and utility classes in JSX (e.g. flex layout, rounded cards, `indigo` accents).

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start the Vite dev server (HMR, fast refresh) |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview the production build locally |
| `npm run lint` | Run ESLint |

## Prerequisites and install

- **Node.js 18+** (recommended: current LTS) and **npm** (or compatible client).

```bash
cd ktguru_frontend
npm install
npm run dev
```

Open the URL Vite prints (commonly `http://127.0.0.1:5173`). The backend must be running and must allow the frontend origin in `CORS_ORIGINS` (see `ktguru_backend` README) so the browser can call the API.

## Project map

- `index.html` — Vite entry HTML
- `vite.config.js` — Vite and React plugin configuration
- `src/main.jsx` — App mount
- `src/App.jsx` — Main UI: sidebar, upload, chat, and API calls
- `src/index.css` — Global styles and Tailwind import

For system-wide architecture and how to start both servers, see the [root README](../README.md).
