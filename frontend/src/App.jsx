import { useState } from 'react';

async function postForm(path, file) {
  const fd = new FormData();
  fd.append('file', file);
  const res = await fetch(path, { method: 'POST', body: fd });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.error || res.statusText);
  }
  return data;
}

export default function App() {
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [ingestMsg, setIngestMsg] = useState('');
  const [ingestErr, setIngestErr] = useState('');

  const sendChat = async () => {
    const text = input.trim();
    if (!text || busy) return;
    setBusy(true);
    setInput('');
    setMessages((m) => [...m, { role: 'user', content: text }]);
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId, message: text }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Chat failed');
      setSessionId(data.sessionId);
      setMessages((m) => [...m, { role: 'assistant', content: data.content, type: data.type }]);
    } catch (e) {
      setMessages((m) => [...m, { role: 'assistant', content: 'Error: ' + e.message }]);
    } finally {
      setBusy(false);
    }
  };

  const onExcel = async (e) => {
    const f = e.target.files?.[0];
    e.target.value = '';
    if (!f) return;
    setIngestErr('');
    setIngestMsg('');
    try {
      const data = await postForm('/api/ingest/excel', f);
      if (!data.ok) throw new Error(data.error || 'Ingest failed');
      setIngestMsg(`Excel: saved ${data.issuesSaved} issues (${data.rowsSkipped} rows skipped).`);
    } catch (err) {
      setIngestErr(err.message);
    }
  };

  const onDoc = async (e) => {
    const f = e.target.files?.[0];
    e.target.value = '';
    if (!f) return;
    setIngestErr('');
    setIngestMsg('');
    try {
      const data = await postForm('/api/ingest/document', f);
      setIngestMsg(`Document: ${data.title} — ${data.chunkCount} chunks indexed.`);
    } catch (err) {
      setIngestErr(err.message);
    }
  };

  const onCode = async (e) => {
    const f = e.target.files?.[0];
    e.target.value = '';
    if (!f) return;
    setIngestErr('');
    setIngestMsg('');
    try {
      const data = await postForm('/api/ingest/code', f);
      setIngestMsg(`Code: ${data.fileName} — ${data.chunkCount} chunks indexed.`);
    } catch (err) {
      setIngestErr(err.message);
    }
  };

  const newSession = () => {
    setSessionId(null);
    setMessages([]);
  };

  return (
    <div className="app">
      <h1>KT Guru</h1>
      <p className="sub">Upload knowledge, then ask questions. Session ID: {sessionId ?? '—'} (new chat clears context).</p>

      <div className="panel">
        <h2>Upload knowledge</h2>
        <div className="row">
          <label>
            Excel (.xlsx){' '}
            <input type="file" accept=".xlsx,.xls" onChange={onExcel} />
          </label>
        </div>
        <div className="row">
          <label>
            Document (DOCX / TXT / ODT){' '}
            <input type="file" accept=".docx,.txt,.odt" onChange={onDoc} />
          </label>
        </div>
        <div className="row">
          <label>
            Code / config{' '}
            <input type="file" onChange={onCode} />
          </label>
        </div>
        {ingestMsg ? <div className="success">{ingestMsg}</div> : null}
        {ingestErr ? <div className="error">{ingestErr}</div> : null}
        <p className="hint">Excel needs columns: Problem/Subject, Resolution, Raised On, Resolved On (optional: Raised By, Resolved By). Rows without both dates are skipped.</p>
      </div>

      <div className="panel">
        <h2>Ask KT Guru</h2>
        <div className="messages">
          {messages.length === 0 ? (
            <div className="hint">No messages yet.</div>
          ) : (
            messages.map((m, i) => (
              <div key={i} className={'msg ' + m.role}>
                <strong>{m.role === 'user' ? 'You' : 'KT Guru'}</strong>
                {m.type ? ` [${m.type}]` : ''}: {m.content}
              </div>
            ))
          )}
        </div>
        <textarea value={input} onChange={(e) => setInput(e.target.value)} placeholder="Describe the issue or ask a follow-up…" />
        <div className="row" style={{ marginTop: '0.5rem' }}>
          <button type="button" disabled={busy} onClick={sendChat}>
            Send
          </button>
          <button type="button" className="secondary" disabled={busy} onClick={newSession}>
            New session
          </button>
        </div>
      </div>
    </div>
  );
}
