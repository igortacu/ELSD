import { useCallback, useRef, useState } from 'react'
import { Editor } from './components/Editor'
import { OutputPanel } from './components/OutputPanel'

export interface RunResult {
  output: string
  stderr: string
  returncode: number
  tokens: number
  parseErrors: number
}

interface ErrorMarker {
  startLineNumber: number; startColumn: number
  endLineNumber: number; endColumn: number
  message: string; severity: 8
}

interface TabState { code: string; result: RunResult | null }

interface Tab {
  id: string; label: string; icon: string
  description: string; code: string
}

// ── Example programs for each tab ─────────────────────────────────────────────

const TABS: Tab[] = [
  {
    id: 'monohybrid',
    label: 'Monohybrid',
    icon: '🧬',
    description: 'Single-locus Aa × Aa cross — classic 3:1 Mendelian ratio',
    code: `
// Classic Mendelian Aa × Aa — expect 3:1 phenotype ratio

gene A;
parent father; parent mother;

// Define dominance rule: A is dominant over a
set dom : A -> a;

// Assign parent genotypes
set genotype father = "Aa";
set genotype mother = "Aa";

// Perform the cross — produces Punnett grid + distributions
cross father x mother -> child;

// Probabilities for the offspring
probability genotype(child);
probability phenotype(child);
`,
  },
  {
    id: 'dihybrid',
    label: 'Dihybrid',
    icon: '🔬',
    description: 'Two-locus AaBb × AaBb — independent assortment (9:3:3:1)',
    code: `
// AaBb × AaBb — independent assortment gives 9:3:3:1 ratio

gene A; gene B;
parent p1; parent p2;

set dom : A -> a;
set dom : B -> b;

set genotype p1 = "AaBb";
set genotype p2 = "AaBb";

// 4×4 Punnett square (16 cells)
cross p1 x p2 -> offspring;

probability phenotype(offspring);
`,
  },
  {
    id: 'bloodgroup',
    label: 'Blood Groups',
    icon: '🩸',
    description: 'ABO and Rh blood type analysis with Punnett cross',
    code: `

// ABO system — IAi (Type A) × IBi (Type B)
parent mom; parent dad;
set genotype mom = "IAi";
set genotype dad = "IBi";

// Shows individual blood types + 2×2 Punnett cross for offspring
bloodgroup mom, dad system ABO;


// Rh system — heterozygous carrier × homozygous recessive
parent rhMom; parent rhDad;
set genotype rhMom = "Dd";
set genotype rhDad = "dd";

bloodgroup rhMom, rhDad system Rh;
`,
  },
  {
    id: 'linkage',
    label: 'Linkage',
    icon: '🔗',
    description: 'Gene linkage with recombination frequency and map distance',
    code: `
// Two genes on the same chromosome — not independently assorting

gene traitA; gene traitB;
parent p1; parent p2;

set dom : A -> a;
set dom : B -> b;
set genotype p1 = "AaBb";
set genotype p2 = "AaBb";

// Declare linkage: 15% recombination = 15 cM apart
linkage traitA, traitB recombination 0.15 distance 15;

// Cross applies linkage-adjusted gamete frequencies
cross p1 x p2 -> child;

probability phenotype(child);
`,
  },
  {
    id: 'prediction',
    label: 'Prediction',
    icon: '📈',
    description: 'Multi-generation self-crossing prediction',
    code: `
// Predict how genotype frequencies shift across generations
// of repeated self-crossing (inbreeding)

gene A;
set dom : A -> a;
set genotype A = "Aa";

// Genotype frequencies after G1, G2, and G3 self-crosses
pred A generation 1;
pred A generation 2;
pred A generation 3;

// Current probability distribution (before any crosses)
probability genotype(A);
`,
  },
  {
    id: 'inference',
    label: 'Inference',
    icon: '🔍',
    description: 'Infer parent genotypes and estimate allele frequencies',
    code: `

gene A;
parent p1; parent p2;

set dom : A -> a;
set genotype p1 = "Aa";
set genotype p2 = "Aa";

cross p1 x p2 -> offspring;

// Infer which alleles each parent contributed to offspring
infer parents from offspring;

// Statistical estimate of allele frequency with CI
estimate A 0.75 confidence 0.95;
estimate A 0.75 confidence 0.99;
`,
  },
]

// ── App ───────────────────────────────────────────────────────────────────────

export default function App() {
  const [activeTab, setActiveTab] = useState<string>(TABS[0].id)
  const [tabStates, setTabStates] = useState<Record<string, TabState>>(
    () => Object.fromEntries(TABS.map(t => [t.id, { code: t.code, result: null }]))
  )
  const [running, setRunning] = useState(false)
  const editorRef = useRef<{ setMarkers: (m: ErrorMarker[]) => void } | null>(null)

  const current = tabStates[activeTab]

  const setCode = useCallback((code: string) => {
    setTabStates(prev => ({ ...prev, [activeTab]: { ...prev[activeTab], code } }))
  }, [activeTab])

  const setResult = useCallback((result: RunResult | null) => {
    setTabStates(prev => ({ ...prev, [activeTab]: { ...prev[activeTab], result } }))
  }, [activeTab])

  const switchTab = useCallback((id: string) => {
    editorRef.current?.setMarkers([])
    setActiveTab(id)
  }, [])

  const run = useCallback(async () => {
    if (running) return
    setRunning(true)
    try {
      const res = await fetch('/api/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: current.code }),
      })
      if (!res.ok) throw new Error(`Server error ${res.status}`)
      const data: RunResult = await res.json()
      setResult(data)
      editorRef.current?.setMarkers(parseStderrMarkers(data.stderr))
    } catch (err) {
      setResult({ output: '', stderr: String(err), returncode: -1, tokens: 0, parseErrors: 0 })
    } finally {
      setRunning(false)
    }
  }, [current.code, running, setResult])

  const clear = useCallback(() => {
    setResult(null)
    editorRef.current?.setMarkers([])
  }, [setResult])

  const result = current.result
  const ok = result?.returncode === 0 && result.parseErrors === 0

  return (
    <div className="h-screen flex flex-col bg-[#080812] text-slate-200 font-sans select-none">

      {/* Toolbar */}
      <header className="flex items-center justify-between h-12 px-5 shrink-0
                         border-b border-white/[0.06] bg-[#0d0d1a]/80 backdrop-blur">
        <div className="flex items-center gap-3">
          <span className="text-lg leading-none">🧬</span>
          <span className="font-semibold text-sm text-white">ELSD</span>
          <span className="text-slate-500 text-sm">Genetics Compiler</span>
          {result && (
            <div className="flex items-center gap-1.5 ml-1">
              <Chip label={`${result.tokens} tokens`} variant="neutral" />
              <Chip
                label={result.parseErrors === 0 ? 'No errors' : `${result.parseErrors} error${result.parseErrors > 1 ? 's' : ''}`}
                variant={result.parseErrors === 0 ? 'green' : 'red'}
              />
            </div>
          )}
        </div>
        <div className="flex items-center gap-2">
          {result && (
            <button onClick={clear}
              className="px-3 py-1.5 text-xs text-slate-400 hover:text-white rounded-md
                         border border-white/[0.07] hover:border-white/20 transition-all">
              Clear
            </button>
          )}
          <RunButton running={running} onClick={run} />
        </div>
      </header>

      {/* Tab bar */}
      <TabBar tabs={TABS} active={activeTab} onSelect={switchTab} />

      {/* Panels */}
      <div className="flex-1 flex overflow-hidden">
        <Editor
          key={activeTab}
          value={current.code}
          onChange={setCode}
          onRun={run}
          editorRef={editorRef}
        />
        <div className="w-px bg-white/[0.05] shrink-0" />
        <OutputPanel result={result} running={running} />
      </div>

      {/* Statusbar */}
      <div className="h-6 flex items-center px-4 gap-3 shrink-0 text-[11px] text-slate-600
                      border-t border-white/[0.04] bg-[#0a0a16]/60">
        <span>ELSD v1.0</span>
        <span>·</span>
        <span>Ctrl / ⌘ + Enter to run</span>
        {result && (
          <>
            <span>·</span>
            <span className={ok ? 'text-emerald-500' : 'text-red-500'}>
              {ok ? '✓ OK' : `✗ Exit ${result.returncode}`}
            </span>
          </>
        )}
      </div>
    </div>
  )
}

// ── Tab bar ───────────────────────────────────────────────────────────────────

function TabBar({ tabs, active, onSelect }: {
  tabs: Tab[]; active: string; onSelect: (id: string) => void
}) {
  return (
    <div className="flex overflow-x-auto shrink-0 bg-[#0a0a16] border-b border-white/[0.05]">
      {tabs.map(tab => {
        const isActive = tab.id === active
        return (
          <button
            key={tab.id}
            title={tab.description}
            onClick={() => onSelect(tab.id)}
            className={`
              flex items-center gap-1.5 px-5 py-2.5 text-xs font-medium whitespace-nowrap
              border-b-2 transition-all duration-150 cursor-pointer
              ${isActive
                ? 'border-emerald-400 text-white bg-white/[0.04]'
                : 'border-transparent text-slate-500 hover:text-slate-300 hover:bg-white/[0.02]'}
            `}
          >
            <span className="text-sm leading-none">{tab.icon}</span>
            <span>{tab.label}</span>
          </button>
        )
      })}
    </div>
  )
}

// ── Small UI atoms ────────────────────────────────────────────────────────────

function Chip({ label, variant }: { label: string; variant: 'green' | 'red' | 'neutral' }) {
  const cls = {
    green:   'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    red:     'bg-red-500/10 text-red-400 border-red-500/20',
    neutral: 'bg-slate-500/10 text-slate-400 border-slate-600/30',
  }[variant]
  return <span className={`px-2 py-0.5 text-[10px] font-medium rounded border ${cls}`}>{label}</span>
}

function RunButton({ running, onClick }: { running: boolean; onClick: () => void }) {
  return (
    <button onClick={onClick} disabled={running}
      className="flex items-center gap-2 px-4 py-1.5 text-sm font-medium rounded-md
                 bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-black
                 shadow-[0_0_16px_rgba(74,222,128,0.25)] hover:shadow-[0_0_24px_rgba(74,222,128,0.4)]
                 transition-all duration-150 cursor-pointer disabled:cursor-not-allowed">
      {running
        ? <><Spinner /> Running…</>
        : <><span className="text-xs">▶</span> Run</>
      }
    </button>
  )
}

function Spinner() {
  return (
    <svg className="w-3 h-3 animate-spin" viewBox="0 0 24 24" fill="none">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  )
}

// ── Error markers from stderr ─────────────────────────────────────────────────

function parseStderrMarkers(stderr: string): ErrorMarker[] {
  const out: ErrorMarker[] = []
  const re = /ERROR\s+line\s+(\d+):(\d+)\s+[\u2013\-]+\s+(.*)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(stderr)) !== null) {
    const ln = parseInt(m[1]), col = parseInt(m[2]) + 1
    out.push({ startLineNumber: ln, startColumn: col, endLineNumber: ln, endColumn: col + 6, message: m[3].trim(), severity: 8 })
  }
  return out
}
