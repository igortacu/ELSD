import { useMemo } from 'react'
import type {
  OutputItem, CrossItem, BloodCrossItem, FindItem, ProbItem, EstimateItem,
  LinkageItem, SexLinkedItem, BloodGroupItem, PredItem, InferItem, PrintItem,
  PunnettCell, CrossStep,
} from '../lib/parseOutput'

// ── Colour palette for distribution bars / phenotype dots ─────────────────────
const PALETTE = [
  '#4ade80', '#60a5fa', '#a78bfa', '#fb923c',
  '#f472b6', '#34d399', '#818cf8', '#fbbf24',
]
function barColor(index: number) { return PALETTE[index % PALETTE.length] }

// ── Entry: dispatch to the right card ─────────────────────────────────────────

export function Cards({ items }: { items: OutputItem[] }) {
  return (
    <>
      {items.map((item, i) => {
        switch (item.type) {
          case 'cross':       return <CrossCard       key={i} item={item} />
          case 'bloodcross':  return <BloodCrossCard  key={i} item={item} />
          case 'find':        return <FindCard        key={i} item={item} />
          case 'probability': return <ProbCard        key={i} item={item} />
          case 'estimate':    return <EstimateCard    key={i} item={item} />
          case 'linkage':     return <LinkageCard     key={i} item={item} />
          case 'sexlinked':   return <SexLinkedCard   key={i} item={item} />
          case 'bloodgroup':  return <BloodGroupCard  key={i} item={item} />
          case 'pred':        return <PredCard        key={i} item={item} />
          case 'infer':       return <InferCard       key={i} item={item} />
          case 'print':       return <PrintCard       key={i} item={item} />
          default:            return null
        }
      })}
    </>
  )
}

// ── Shared card shell ─────────────────────────────────────────────────────────

interface CardShellProps {
  icon: string; title: string; subtitle?: string
  accent: string; glow?: string; children: React.ReactNode
}

function CardShell({ icon, title, subtitle, accent, glow, children }: CardShellProps) {
  return (
    <div
      className={`rounded-xl border ${accent} bg-[#12121f] overflow-hidden animate-slide-up`}
      style={glow ? { boxShadow: `0 0 24px ${glow}` } : undefined}
    >
      <div className={`flex items-center gap-2.5 px-4 py-3 border-b ${accent.replace('border-', 'border-b-')}`}>
        <span className="text-base leading-none">{icon}</span>
        <div>
          <p className="text-xs font-semibold tracking-wide text-white/80">{title}</p>
          {subtitle && <p className="text-[10px] text-slate-500 mt-0.5">{subtitle}</p>}
        </div>
      </div>
      <div className="px-4 py-3">{children}</div>
    </div>
  )
}

// ── Distribution bar ──────────────────────────────────────────────────────────

function DistBar({ label, value, color }: { label: string; value: number; color: string }) {
  const pct = Math.min(100, value * 100)
  return (
    <div className="flex items-center gap-2 group">
      <span className="font-mono text-xs text-slate-300 w-12 shrink-0 text-right">{label}</span>
      <div className="flex-1 h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
        <div className="h-full rounded-full transition-all duration-500"
          style={{ width: `${pct}%`, background: color }} />
      </div>
      <span className="text-[11px] text-slate-400 w-11 text-right font-mono shrink-0">
        {pct.toFixed(1)}%
      </span>
    </div>
  )
}

function DistGroup({ label, dist }: { label: string; dist: Record<string, number> }) {
  const entries = Object.entries(dist)
  if (entries.length === 0) return null
  return (
    <div className="space-y-1.5">
      <p className="text-[10px] uppercase tracking-widest text-slate-600 font-medium mb-2">{label}</p>
      {entries.map(([k, v], i) => (
        <DistBar key={k} label={k} value={v} color={barColor(i)} />
      ))}
    </div>
  )
}

// ── Steps flow ────────────────────────────────────────────────────────────────

function StepsFlow({ steps }: { steps: CrossStep[] }) {
  return (
    <div className="space-y-2 mb-4 pb-4 border-b border-white/[0.05]">
      <p className="text-[10px] uppercase tracking-widest text-slate-600 font-medium mb-3">
        Resolution Steps
      </p>
      {steps.map((s, i) => (
        <div key={i} className="flex items-start gap-2.5">
          <div className="shrink-0 w-5 h-5 rounded-full bg-emerald-500/20 border border-emerald-500/30
                          flex items-center justify-center text-[10px] font-bold text-emerald-400 mt-0.5">
            {s.num}
          </div>
          <div className="flex-1 min-w-0 pt-0.5">
            <span className="text-xs text-white/75 font-medium">{s.title}</span>
            {s.detail && (
              <span className="text-[11px] text-slate-500 ml-2 font-mono break-all">{s.detail}</span>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}

// ── Punnett grid ──────────────────────────────────────────────────────────────

interface PunnettData {
  cols: string[]; rows: string[]; cells: PunnettCell[]
}

function PunnettGrid({ punnett, phenoColors }: {
  punnett: PunnettData
  phenoColors: Record<string, string>
}) {
  const { cols, rows, cells } = punnett
  const cellMap: Record<string, PunnettCell> = {}
  cells.forEach(c => { cellMap[`${c.col}|${c.row}`] = c })
  const phenoEntries = Object.entries(phenoColors)

  return (
    <div className="space-y-3 mb-4 pb-4 border-b border-white/[0.05]">
      <p className="text-[10px] uppercase tracking-widest text-slate-600 font-medium">Punnett Square</p>

      <div className="overflow-x-auto">
        <table className="border-collapse select-none">
          <thead>
            <tr>
              {/* corner */}
              <th className="w-12 h-8" />
              {cols.map(col => (
                <th key={col} className="h-8 px-1 text-center">
                  <span className="inline-block px-2 py-0.5 rounded bg-emerald-500/10
                                   border border-emerald-500/25 text-[11px] font-mono text-emerald-300">
                    {col}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map(row => (
              <tr key={row}>
                <td className="pr-1.5 text-center">
                  <span className="inline-block px-2 py-0.5 rounded bg-blue-500/10
                                   border border-blue-500/25 text-[11px] font-mono text-blue-300">
                    {row}
                  </span>
                </td>
                {cols.map(col => {
                  const cell = cellMap[`${col}|${row}`]
                  const color = cell ? (phenoColors[cell.phenotype] ?? '#94a3b8') : '#94a3b8'
                  return (
                    <td key={col}
                      className="w-16 h-14 border border-white/[0.07] bg-white/[0.02]
                                 text-center align-middle p-1.5 relative
                                 hover:bg-white/[0.05] transition-colors">
                      {cell && (
                        <div className="flex flex-col items-center gap-1">
                          <span className="text-[11px] font-mono text-white/65 leading-none">
                            {cell.genotype}
                          </span>
                          <span
                            className="w-2.5 h-2.5 rounded-full"
                            style={{ background: color, boxShadow: `0 0 5px ${color}90` }}
                          />
                        </div>
                      )}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Legend */}
      {phenoEntries.length > 0 && (
        <div className="flex flex-wrap gap-3">
          {phenoEntries.map(([pheno, color]) => (
            <span key={pheno} className="flex items-center gap-1.5 text-[11px] text-slate-400">
              <span className="w-2.5 h-2.5 rounded-full shrink-0"
                    style={{ background: color, boxShadow: `0 0 4px ${color}80` }} />
              {pheno}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Build phenotype → color map from punnett cells ───────────────────────────

function usePhenoColors(punnett?: PunnettData): Record<string, string> {
  return useMemo(() => {
    if (!punnett) return {}
    const phenos = [...new Set(punnett.cells.map(c => c.phenotype))]
    // Sort: uppercase-dominant phenotypes first (more alleles = brighter)
    phenos.sort((a, b) => {
      const ua = (a.match(/[A-Z]/g) ?? []).length
      const ub = (b.match(/[A-Z]/g) ?? []).length
      return ub - ua
    })
    const map: Record<string, string> = {}
    phenos.forEach((p, i) => { map[p] = PALETTE[i % PALETTE.length] })
    return map
  }, [punnett])
}

// ═════════════════════════════════════════════════════════════════════════════
// Cards
// ═════════════════════════════════════════════════════════════════════════════

// ── Cross ─────────────────────────────────────────────────────────────────────

function CrossCard({ item }: { item: CrossItem }) {
  const hasGeno   = Object.keys(item.genoDist).length > 0
  const hasPheno  = Object.keys(item.phenoDist).length > 0
  const phenoColors = usePhenoColors(item.punnett)

  return (
    <CardShell
      icon="🧬" title="Cross"
      subtitle={`${item.parent1} × ${item.parent2}  →  ${item.offspring}`}
      accent="border-emerald-500/20"
      glow="rgba(74,222,128,0.06)"
    >
      <div className="space-y-0">
        {item.steps && item.steps.length > 0 && <StepsFlow steps={item.steps} />}
        {item.punnett && item.punnett.cells.length > 0 && (
          <PunnettGrid punnett={item.punnett} phenoColors={phenoColors} />
        )}
        <div className="space-y-4">
          {hasGeno  && <DistGroup label="Genotype distribution"  dist={item.genoDist} />}
          {hasPheno && <DistGroup label="Phenotype distribution" dist={item.phenoDist} />}
          {!hasGeno && !hasPheno && !item.punnett && (
            <p className="text-xs text-slate-500 italic">No distribution data.</p>
          )}
        </div>
      </div>
    </CardShell>
  )
}

// ── Blood cross ───────────────────────────────────────────────────────────────

const BLOOD_COLORS: Record<string, string> = {
  A:  '#f87171',
  B:  '#60a5fa',
  AB: '#a78bfa',
  O:  '#94a3b8',
  'Rh+': '#fb923c',
  'Rh-': '#64748b',
}

function BloodCrossCard({ item }: { item: BloodCrossItem }) {
  const phenoColors = useMemo(() => {
    if (!item.punnett) return {}
    const phenos = [...new Set(item.punnett.cells.map(c => c.phenotype))]
    const map: Record<string, string> = {}
    phenos.forEach(p => { map[p] = BLOOD_COLORS[p] ?? PALETTE[Object.keys(map).length % PALETTE.length] })
    return map
  }, [item.punnett])

  const hasBloodDist = Object.keys(item.bloodDist).length > 0

  return (
    <CardShell
      icon="🩸" title="Blood Cross"
      subtitle={`${item.parent1} × ${item.parent2}`}
      accent="border-red-500/20"
      glow="rgba(248,113,113,0.06)"
    >
      <div className="space-y-0">
        {item.steps && item.steps.length > 0 && <StepsFlow steps={item.steps} />}
        {item.punnett && item.punnett.cells.length > 0 && (
          <PunnettGrid punnett={item.punnett} phenoColors={phenoColors} />
        )}
        {hasBloodDist && (
          <div className="space-y-1.5">
            <p className="text-[10px] uppercase tracking-widest text-slate-600 font-medium mb-2">
              Offspring blood type distribution
            </p>
            {Object.entries(item.bloodDist).map(([k, v], i) => (
              <DistBar key={k} label={k} value={v}
                       color={BLOOD_COLORS[k] ?? barColor(i)} />
            ))}
          </div>
        )}
      </div>
    </CardShell>
  )
}

// ── Find ──────────────────────────────────────────────────────────────────────

function FindCard({ item }: { item: FindItem }) {
  const missing = item.value === '(not set)'
  return (
    <CardShell
      icon="🔍" title="Find"
      subtitle={`${item.field}  ·  ${item.id}${item.generation ? `  ·  generation ${item.generation}` : ''}`}
      accent="border-cyan-500/20"
    >
      <div className="flex items-center gap-3">
        <span className="text-[10px] uppercase tracking-wider text-slate-500">{item.field}</span>
        <span className="text-slate-600">→</span>
        <span className={`font-mono text-sm font-medium ${missing ? 'text-slate-600 italic' : 'text-cyan-300'}`}>
          {item.value}
        </span>
      </div>
    </CardShell>
  )
}

// ── Probability ───────────────────────────────────────────────────────────────

function ProbCard({ item }: { item: ProbItem }) {
  if (item.conditionalValue !== undefined) {
    const unknown = item.conditionalValue === 'unknown'
    const val = unknown ? null : item.conditionalValue as number
    return (
      <CardShell icon="%" title="Probability" subtitle={item.expression} accent="border-blue-500/20" glow="rgba(96,165,250,0.05)">
        <div className="flex items-baseline gap-3">
          <span className="text-2xl font-bold font-mono text-blue-300">
            {unknown ? '—' : `${((val as number) * 100).toFixed(1)}%`}
          </span>
          {unknown && <span className="text-xs text-slate-600 italic">no data</span>}
          {!unknown && (
            <div className="flex-1 h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
              <div className="h-full rounded-full bg-blue-400 transition-all duration-500"
                style={{ width: `${(val as number) * 100}%` }} />
            </div>
          )}
        </div>
      </CardShell>
    )
  }

  const dist = item.distribution ?? {}
  const entries = Object.entries(dist)
  return (
    <CardShell icon="%" title="Probability" subtitle={item.expression} accent="border-blue-500/20" glow="rgba(96,165,250,0.05)">
      <div className="space-y-1.5">
        {entries.map(([k, v], i) => (
          <DistBar key={k} label={k} value={v} color={barColor(i)} />
        ))}
        {entries.length === 0 && <p className="text-xs text-slate-600 italic">No data.</p>}
      </div>
    </CardShell>
  )
}

// ── Estimate ──────────────────────────────────────────────────────────────────

function EstimateCard({ item }: { item: EstimateItem }) {
  const lo = Math.round(item.lower * 1000) / 1000
  const hi = Math.round(item.upper * 1000) / 1000
  const trackPos = item.value * 100

  return (
    <CardShell icon="~" title="Estimate" subtitle={`${item.id}  ·  ${item.confidence}% CI`} accent="border-amber-500/20" glow="rgba(251,191,36,0.05)">
      <div className="space-y-3">
        <div className="flex items-baseline gap-2">
          <span className="text-2xl font-bold font-mono text-amber-300">
            {(item.value * 100).toFixed(1)}%
          </span>
          <span className="text-xs text-slate-500">point estimate</span>
        </div>
        <div>
          <div className="relative h-3 bg-white/[0.04] rounded-full overflow-visible">
            <div
              className="absolute top-0 h-3 rounded-full bg-amber-500/20 border border-amber-500/30"
              style={{ left: `${lo * 100}%`, width: `${(hi - lo) * 100}%` }}
            />
            <div
              className="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-amber-400
                         shadow-[0_0_6px_rgba(251,191,36,0.6)]"
              style={{ left: `${trackPos}%`, transform: 'translate(-50%, -50%)' }}
            />
          </div>
          <div className="flex justify-between mt-1 text-[10px] text-slate-600 font-mono">
            <span>{lo.toFixed(4)}</span>
            <span className="text-slate-500">{item.confidence}% confidence interval</span>
            <span>{hi.toFixed(4)}</span>
          </div>
        </div>
      </div>
    </CardShell>
  )
}

// ── Linkage ───────────────────────────────────────────────────────────────────

function LinkageCard({ item }: { item: LinkageItem }) {
  return (
    <CardShell icon="🔗" title="Linkage" subtitle={item.genes.join(' — ')} accent="border-purple-500/20" glow="rgba(167,139,250,0.05)">
      <div className="flex gap-6">
        <Stat label="Recombination" value={`${(item.recombination * 100).toFixed(0)}%`} color="text-purple-300" />
        {item.distance !== undefined && (
          <Stat label="Distance" value={`${item.distance} cM`} color="text-purple-300" />
        )}
        <div className="flex items-center gap-2 flex-1">
          {item.genes.map((g, i) => (
            <span key={i} className="flex items-center gap-1.5">
              {i > 0 && <span className="text-slate-600 text-xs">——</span>}
              <span className="px-2 py-0.5 rounded bg-purple-500/10 border border-purple-500/20 text-xs text-purple-300 font-mono">{g}</span>
            </span>
          ))}
        </div>
      </div>
    </CardShell>
  )
}

// ── Sex-linked ────────────────────────────────────────────────────────────────

function SexLinkedCard({ item }: { item: SexLinkedItem }) {
  return (
    <CardShell icon="🔀" title="Sex-linked" subtitle="X-linked inheritance" accent="border-pink-500/20">
      <div className="flex items-center gap-3">
        <span className="px-3 py-1 rounded-full bg-pink-500/10 border border-pink-500/20
                         text-sm font-mono font-medium text-pink-300">
          {item.gene}
        </span>
        <span className="text-xs text-slate-500">marked as X-linked — hemizygous in males</span>
      </div>
    </CardShell>
  )
}

// ── Blood group ───────────────────────────────────────────────────────────────

function BloodGroupCard({ item }: { item: BloodGroupItem }) {
  const unknown = item.result.toLowerCase().includes('unknown')
  const isABO = item.system === 'ABO'
  const typeMatch = item.result.match(/Type\s+(\S+)/)
  const bloodType = typeMatch?.[1] ?? ''
  const notes = item.result.replace(/Type\s+\S+/, '').trim()

  const typeColors: Record<string, string> = {
    A:  'bg-red-500/15 border-red-500/30 text-red-300',
    B:  'bg-blue-500/15 border-blue-500/30 text-blue-300',
    AB: 'bg-purple-500/15 border-purple-500/30 text-purple-300',
    O:  'bg-slate-500/15 border-slate-500/30 text-slate-300',
  }

  return (
    <CardShell icon="🩸" title="Blood Group" subtitle={`${item.id}  ·  ${item.system} system`}
      accent="border-red-500/20" glow="rgba(248,113,113,0.05)">
      {unknown
        ? <p className="text-xs text-slate-600 italic">Set genotype to determine blood type.</p>
        : isABO
          ? <div className="flex items-center gap-3">
              {bloodType && (
                <span className={`text-lg font-bold font-mono px-3 py-1 rounded border ${typeColors[bloodType] ?? 'bg-white/5 border-white/10 text-white'}`}>
                  {bloodType}
                </span>
              )}
              {notes && <span className="text-xs text-slate-500 font-mono">{notes}</span>}
            </div>
          : <div className="flex items-center gap-3">
              <span className={`text-sm font-bold px-3 py-1 rounded border font-mono
                ${item.result.includes('Rh+') ? 'bg-rose-500/15 border-rose-500/30 text-rose-300' : 'bg-slate-500/10 border-slate-500/20 text-slate-300'}`}>
                {item.result.includes('Rh+') ? 'Rh+' : 'Rh−'}
              </span>
              <span className="text-xs text-slate-500 font-mono">{item.result.replace(/Rh[+−+-]/, '').trim()}</span>
            </div>
      }
    </CardShell>
  )
}

// ── Prediction ────────────────────────────────────────────────────────────────

function PredCard({ item }: { item: PredItem }) {
  const hasGeno  = Object.keys(item.genoDist).length > 0
  const hasPheno = Object.keys(item.phenoDist).length > 0

  return (
    <CardShell icon="📈" title="Prediction"
      subtitle={`${item.id}  ·  generation ${item.generation}`}
      accent="border-teal-500/20">
      {item.noData
        ? <p className="text-xs text-slate-600 italic">No genotype data for {item.id}.</p>
        : <div className="space-y-4">
            {hasGeno  && <DistGroup label="Genotype frequencies"  dist={item.genoDist} />}
            {hasPheno && <DistGroup label="Phenotype frequencies" dist={item.phenoDist} />}
          </div>
      }
    </CardShell>
  )
}

// ── Infer ─────────────────────────────────────────────────────────────────────

function InferCard({ item }: { item: InferItem }) {
  const title = item.mode === 'parents'
    ? `Infer Parents — ${item.subject}`
    : `Infer ${item.field} — ${item.subject}`

  return (
    <CardShell icon="🔬" title={title} accent="border-indigo-500/20">
      {item.noData
        ? <p className="text-xs text-slate-600 italic">No genotype data available.</p>
        : <div className="space-y-1.5">
            {item.details.map((d, i) => (
              <p key={i} className="text-xs font-mono text-slate-400 leading-relaxed
                                   pl-3 border-l border-indigo-500/20">
                {d}
              </p>
            ))}
          </div>
      }
    </CardShell>
  )
}

// ── Print (raw output) ────────────────────────────────────────────────────────

function PrintCard({ item }: { item: PrintItem }) {
  return (
    <div className="rounded-xl border border-white/[0.06] bg-[#0f0f1a] overflow-hidden animate-slide-up">
      <div className="flex items-center gap-2 px-4 py-2 border-b border-white/[0.04]">
        <div className="flex gap-1">
          <div className="w-2 h-2 rounded-full bg-red-400/40" />
          <div className="w-2 h-2 rounded-full bg-amber-400/40" />
          <div className="w-2 h-2 rounded-full bg-emerald-400/40" />
        </div>
        <span className="text-[10px] uppercase tracking-widest text-slate-600 ml-1">Output</span>
      </div>
      <div className="px-4 py-3 space-y-0.5">
        {item.lines.map((l, i) => (
          <p key={i} className="font-mono text-sm text-slate-300 leading-6">{l}</p>
        ))}
      </div>
    </div>
  )
}

// ── Tiny stat display ─────────────────────────────────────────────────────────

function Stat({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div>
      <p className="text-[10px] uppercase tracking-wider text-slate-600 mb-0.5">{label}</p>
      <p className={`text-lg font-bold font-mono ${color}`}>{value}</p>
    </div>
  )
}
