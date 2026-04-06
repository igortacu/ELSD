import { useMemo } from 'react'
import type { RunResult } from '../App'
import { parseOutput, type OutputItem } from '../lib/parseOutput'
import { Cards } from './Cards'

interface Props {
  result: RunResult | null
  running: boolean
}

export function OutputPanel({ result, running }: Props) {
  const items = useMemo<OutputItem[]>(() => {
    if (!result?.output) return []
    return parseOutput(result.output)
  }, [result?.output])

  return (
    <div className="flex-[45] min-w-0 flex flex-col overflow-hidden bg-[#0a0a14]">
      <div className="flex items-center justify-between h-8 px-4 shrink-0
                      border-b border-white/[0.05] bg-[#0d0d1a]/60">
        <span className="text-[11px] font-medium tracking-widest uppercase text-slate-500">
          Output
        </span>
        {result && (
          <span className={`text-[10px] font-medium px-2 py-0.5 rounded border
            ${result.returncode === 0 && result.parseErrors === 0
              ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20'
              : 'text-red-400 bg-red-500/10 border-red-500/20'}`}>
            {result.returncode === 0 && result.parseErrors === 0 ? 'OK' : 'ERROR'}
          </span>
        )}
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {/* Loading state */}
        {running && (
          <div className="flex items-center justify-center h-48">
            <div className="flex flex-col items-center gap-3">
              <div className="flex gap-1">
                {[0, 1, 2].map(i => (
                  <div key={i}
                    className="w-2 h-2 rounded-full bg-emerald-400 animate-bounce"
                    style={{ animationDelay: `${i * 0.15}s` }}
                  />
                ))}
              </div>
              <span className="text-xs text-slate-500">Compiling & running…</span>
            </div>
          </div>
        )}

        {/* Empty state */}
        {!running && !result && (
          <div className="flex flex-col items-center justify-center h-48 gap-3">
            <div className="text-4xl opacity-20">🧬</div>
            <p className="text-sm text-slate-600">Run your program to see results</p>
            <p className="text-xs text-slate-700">Ctrl / ⌘ + Enter</p>
          </div>
        )}

        {/* Stderr / parse errors */}
        {!running && result && result.stderr.trim() && (
          <ErrorBox stderr={result.stderr} />
        )}

        {/* Structured output cards */}
        {!running && items.length > 0 && <Cards items={items} />}

        {/* No semantics output */}
        {!running && result && items.length === 0 && !result.stderr.trim() && (
          <div className="flex items-center justify-center h-24">
            <span className="text-sm text-slate-600">No output produced.</span>
          </div>
        )}
      </div>
    </div>
  )
}

function ErrorBox({ stderr }: { stderr: string }) {
  const lines = stderr.trim().split('\n').filter(Boolean)
  return (
    <div className="rounded-xl border border-red-500/20 bg-red-950/20 overflow-hidden animate-slide-up">
      <div className="flex items-center gap-2 px-4 py-2.5 border-b border-red-500/10">
        <span className="text-red-400 text-sm">⚠</span>
        <span className="text-xs font-semibold text-red-400 uppercase tracking-wider">Parse Errors</span>
      </div>
      <div className="px-4 py-3 space-y-1">
        {lines.map((l, i) => (
          <p key={i} className="text-xs font-mono text-red-300/80 leading-relaxed">{l}</p>
        ))}
      </div>
    </div>
  )
}
