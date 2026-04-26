import { useState, useRef, useEffect } from 'react'
import axios from 'axios'
import { Sparkles, Plus, Upload, Send, BookOpen } from 'lucide-react'

const INITIAL_MESSAGE = {
  role: 'ai',
  content: 'Hello! I am KT Guru. How can I help you today?',
}

function App() {
  const [messages, setMessages] = useState([INITIAL_MESSAGE])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const fileInputRef = useRef(null)
  const chatEndRef = useRef(null)

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, isLoading])

  const handleFileUpload = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    const formData = new FormData()
    formData.append('file', file)
    try {
      await axios.post('http://127.0.0.1:8000/api/upload', formData)
      alert('Document uploaded successfully.')
    } catch {
      alert('Document upload failed. Please try again.')
    } finally {
      e.target.value = ''
    }
  }

  const handleUploadButtonClick = () => {
    fileInputRef.current?.click()
  }

  const handleSend = async () => {
    const query = input.trim()
    if (!query || isLoading) return

    setMessages((m) => [...m, { role: 'user', content: query }])
    setInput('')
    setIsLoading(true)

    try {
      const response = await axios.post('http://127.0.0.1:8000/api/ask', { query })
      const answer = response.data?.answer
      setMessages((m) => [
        ...m,
        {
          role: 'ai',
          content:
            answer != null && String(answer).length > 0
              ? String(answer)
              : 'No answer returned.',
        },
      ])
    } catch {
      setMessages((m) => [
        ...m,
        {
          role: 'ai',
          content:
            'Sorry — the request failed. Check that the server is running and try again.',
        },
      ])
    } finally {
      setIsLoading(false)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void handleSend()
    }
  }

  const handleNewChat = () => {
    setMessages([INITIAL_MESSAGE])
    setInput('')
  }

  return (
    <div className="flex h-screen w-full overflow-hidden bg-gray-100">
      <aside className="flex w-64 flex-col bg-gray-900 text-white">
        <div className="shrink-0 border-b border-gray-800 p-4">
          <div className="flex items-center gap-2.5">
            <Sparkles className="h-7 w-7 shrink-0 text-cyan-400" aria-hidden />
            <span className="text-xl font-semibold tracking-tight">KT Guru</span>
          </div>
        </div>

        <div className="min-h-0 flex-1 p-4">
          <button
            type="button"
            onClick={handleNewChat}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-gray-800 py-2.5 text-sm font-medium text-white transition hover:bg-gray-700"
          >
            <Plus className="h-4 w-4" />
            New Chat
          </button>
        </div>

        <div className="mt-auto border-t border-gray-800 p-4">
          <p className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-gray-400">
            <BookOpen className="h-4 w-4" />
            Knowledge Base
          </p>
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            onChange={handleFileUpload}
            aria-hidden
            tabIndex={-1}
          />
          <button
            type="button"
            onClick={handleUploadButtonClick}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 py-2.5 text-sm font-medium text-white transition hover:bg-indigo-500"
          >
            <Upload className="h-4 w-4" />
            Upload Document
          </button>
        </div>
      </aside>

      <main className="flex min-h-0 min-w-0 flex-1 flex-col bg-gray-50">
        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-6">
          {messages.map((msg, i) => {
            const isUser = msg.role === 'user'
            return (
              <div
                key={i}
                className={`flex w-full ${isUser ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={
                    isUser
                      ? 'max-w-[min(42rem,100%)] rounded-xl bg-blue-600 px-4 py-3 text-sm leading-relaxed text-white shadow-sm'
                      : 'max-w-[min(42rem,100%)] rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm leading-relaxed text-gray-800 shadow-sm'
                  }
                >
                  {msg.content}
                </div>
              </div>
            )
          })}

          {isLoading && (
            <div className="flex w-full justify-start">
              <div className="rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm text-gray-500 shadow-sm">
                Thinking...
              </div>
            </div>
          )}

          <div ref={chatEndRef} />
        </div>

        <div className="shrink-0 border-t border-gray-200 bg-gray-50 p-4">
          <div className="mx-auto flex max-w-4xl items-end gap-2">
            <input
              type="text"
              className="min-w-0 flex-1 rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm text-gray-900 shadow-sm placeholder:text-gray-400 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-100 disabled:cursor-not-allowed disabled:bg-gray-100"
              placeholder="Ask KT Guru..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={isLoading}
              autoComplete="off"
              aria-label="Message to KT Guru"
            />
            <button
              type="button"
              onClick={() => void handleSend()}
              disabled={!input.trim() || isLoading}
              className="inline-flex shrink-0 items-center justify-center rounded-xl bg-indigo-600 p-3 text-white shadow-sm transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
              aria-label="Send message"
            >
              <Send className="h-5 w-5" />
            </button>
          </div>
        </div>
      </main>
    </div>
  )
}

export default App
