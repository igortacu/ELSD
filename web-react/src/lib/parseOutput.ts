// ── Types ─────────────────────────────────────────────────────────────────────

export interface PunnettCell {
  col: string; row: string; genotype: string; phenotype: string
}
export interface CrossStep {
  num: number; title: string; detail: string
}

export interface CrossItem {
  type: 'cross'
  parent1: string; parent2: string; offspring: string
  genoDist: Record<string, number>
  phenoDist: Record<string, number>
  punnett?: { cols: string[]; rows: string[]; cells: PunnettCell[] }
  steps?: CrossStep[]
}
export interface BloodCrossItem {
  type: 'bloodcross'
  parent1: string; parent2: string
  bloodDist: Record<string, number>
  punnett?: { cols: string[]; rows: string[]; cells: PunnettCell[] }
  steps?: CrossStep[]
}
export interface FindItem {
  type: 'find'
  field: string; id: string; value: string; generation?: number
}
export interface ProbItem {
  type: 'probability'
  expression: string
  distribution?: Record<string, number>
  conditionalValue?: number | 'unknown'
}
export interface EstimateItem {
  type: 'estimate'
  id: string; value: number; lower: number; upper: number; confidence: number
}
export interface LinkageItem {
  type: 'linkage'
  genes: string[]; recombination: number; distance?: number
}
export interface SexLinkedItem {
  type: 'sexlinked'; gene: string
}
export interface BloodGroupItem {
  type: 'bloodgroup'; id: string; system: string; result: string
}
export interface PredItem {
  type: 'pred'
  id: string; generation: number
  genoDist: Record<string, number>; phenoDist: Record<string, number>
  noData?: boolean
}
export interface InferItem {
  type: 'infer'
  mode: 'parents' | 'field'
  subject: string; field?: string; details: string[]; noData?: boolean
}
export interface PrintItem {
  type: 'print'; lines: string[]
}

export type OutputItem =
  | CrossItem | BloodCrossItem | FindItem | ProbItem | EstimateItem
  | LinkageItem | SexLinkedItem | BloodGroupItem
  | PredItem | InferItem | PrintItem

// ── Inline distribution: "  key: 25.0%  |  key2: 50.0%  |" ─────────────────

function parseInlineDist(line: string): Record<string, number> {
  const result: Record<string, number> = {}
  const parts = line.split('|')
  for (const part of parts) {
    const m = part.match(/(\S+):\s+([\d.]+)%/)
    if (m) result[m[1]] = parseFloat(m[2]) / 100
  }
  return result
}

// ── Parse continuation lines shared by cross / blood-cross ───────────────────

interface ContinuationAccumulator {
  steps: CrossStep[]
  punnett: { cols: string[]; rows: string[]; cells: PunnettCell[] }
  dist1: Record<string, number>   // genoDist or bloodDist
  dist2: Record<string, number>   // phenoDist (cross only)
}

function parseContinuationLine(c: string, acc: ContinuationAccumulator) {
  const stepM = c.match(/^\s+step:\s+(\d+)\|(.+?)\|(.*)/)
  if (stepM) {
    acc.steps.push({ num: parseInt(stepM[1]), title: stepM[2].trim(), detail: stepM[3].trim() })
    return
  }
  if (/punnett_cols:/.test(c)) {
    acc.punnett.cols = c.split('punnett_cols:')[1].trim().split(',').map(s => s.trim())
  } else if (/punnett_rows:/.test(c)) {
    acc.punnett.rows = c.split('punnett_rows:')[1].trim().split(',').map(s => s.trim())
  } else if (/punnett_cell:/.test(c)) {
    const parts = c.split('punnett_cell:')[1].trim().split('|')
    if (parts.length >= 4)
      acc.punnett.cells.push({ col: parts[0], row: parts[1], genotype: parts[2], phenotype: parts[3] })
  } else if (c.includes('Genotype distribution') && c.includes('|')) {
    Object.assign(acc.dist1, parseInlineDist(c))
  } else if (c.includes('Phenotype distribution') && c.includes('|')) {
    Object.assign(acc.dist2, parseInlineDist(c))
  } else if (c.includes('distribution') && c.includes('|')) {
    // blood dist: "ABO type distribution" / "Rh type distribution"
    Object.assign(acc.dist1, parseInlineDist(c))
  } else {
    // class-based ratios
    const cm = c.match(/\s+(class\d+)\s+([\d.]+)/)
    if (cm) acc.dist1[cm[1]] = parseFloat(cm[2])
  }
}

// ── Check whether a line starts a new structured block ───────────────────────

function isStructuredLine(line: string): boolean {
  return (
    /^\s*Cross\s+\[/.test(line) ||
    /^\s*Blood cross\s+\[/.test(line) ||
    /^\s*find\s+/.test(line) ||
    /^\s*P\(/.test(line) ||
    /^\s*Estimate\s+/.test(line) ||
    /^\s*Linkage:/.test(line) ||
    /^\s*\w+\s+marked as sex-linked/.test(line) ||
    /^\s*Blood group/.test(line) ||
    /^\s*Pred\s+/.test(line) ||
    /^\s*Infer\s+/.test(line)
  )
}

// ── Main parser ───────────────────────────────────────────────────────────────

export function parseOutput(text: string): OutputItem[] {
  const lines = text.split('\n')
  const items: OutputItem[] = []
  let i = 0
  const printBuffer: string[] = []

  const flushPrint = () => {
    if (printBuffer.length > 0) {
      items.push({ type: 'print', lines: [...printBuffer] })
      printBuffer.length = 0
    }
  }

  while (i < lines.length) {
    const line = lines[i]
    const trimmed = line.trim()
    if (!trimmed) { i++; continue }

    // ── Cross ─────────────────────────────────────────────────────────────
    const crossM = line.match(/^\s*Cross\s+\[(.+?)\s+x\s+(.+?)\]\s+→\s+(\S+)/)
    if (crossM) {
      flushPrint()
      const item: CrossItem = {
        type: 'cross',
        parent1: crossM[1].trim(), parent2: crossM[2].trim(), offspring: crossM[3].trim(),
        genoDist: {}, phenoDist: {},
      }
      i++
      const acc: ContinuationAccumulator = {
        steps: [], punnett: { cols: [], rows: [], cells: [] }, dist1: {}, dist2: {},
      }
      while (i < lines.length && /^\s{4}/.test(lines[i])) {
        parseContinuationLine(lines[i], acc)
        i++
      }
      item.genoDist  = acc.dist1
      item.phenoDist = acc.dist2
      if (acc.steps.length > 0)         item.steps   = acc.steps
      if (acc.punnett.cells.length > 0) item.punnett = acc.punnett
      items.push(item)
      continue
    }

    // ── Blood cross ───────────────────────────────────────────────────────
    const bloodCrossM = line.match(/^\s*Blood cross\s+\[(.+?)\s+x\s+(.+?)\]:/)
    if (bloodCrossM) {
      flushPrint()
      const item: BloodCrossItem = {
        type: 'bloodcross',
        parent1: bloodCrossM[1].trim(), parent2: bloodCrossM[2].trim(),
        bloodDist: {},
      }
      i++
      const acc: ContinuationAccumulator = {
        steps: [], punnett: { cols: [], rows: [], cells: [] }, dist1: {}, dist2: {},
      }
      while (i < lines.length && /^\s{4}/.test(lines[i])) {
        parseContinuationLine(lines[i], acc)
        i++
      }
      item.bloodDist = acc.dist1
      if (acc.steps.length > 0)         item.steps   = acc.steps
      if (acc.punnett.cells.length > 0) item.punnett = acc.punnett
      items.push(item)
      continue
    }

    // ── Find ──────────────────────────────────────────────────────────────
    const findM = line.match(/^\s*find\s+(\w+)\s+(\w+)(?:\s+\[generation\s+(\d+)\])?\s+→\s+(.+)/)
    if (findM) {
      flushPrint()
      items.push({
        type: 'find',
        field: findM[1], id: findM[2],
        generation: findM[3] ? parseInt(findM[3]) : undefined,
        value: findM[4].trim(),
      })
      i++; continue
    }

    // ── Probability ───────────────────────────────────────────────────────
    if (/^\s*P\(/.test(line)) {
      flushPrint()
      const condM = line.match(/^\s*(P\(.+?\))\s+=\s+(\S+)/)
      if (condM) {
        const raw = condM[2]
        const val = raw === 'unknown' ? 'unknown' : parseFloat(raw)
        items.push({ type: 'probability', expression: condM[1], conditionalValue: val as number | 'unknown' })
        i++; continue
      }
      const exprM = line.match(/^\s*(P\(.+?\)):/)
      const expression = exprM ? exprM[1] : trimmed.replace(/:$/, '')
      const dist: Record<string, number> = {}
      i++
      while (i < lines.length && /^\s{4}/.test(lines[i])) {
        const em = lines[i].match(/^\s+(\S+)\s+=\s+([\d.]+)/)
        if (em) dist[em[1]] = parseFloat(em[2])
        i++
      }
      items.push({ type: 'probability', expression, distribution: dist })
      continue
    }

    // ── Estimate ──────────────────────────────────────────────────────────
    const estM = line.match(/^\s*Estimate\s+(\w+):\s+([\d.]+)\s+\(CI\s+([\d.]+)[–\-]([\d.]+)\s+at\s+([\d.]+)%/)
    if (estM) {
      flushPrint()
      items.push({
        type: 'estimate',
        id: estM[1], value: parseFloat(estM[2]),
        lower: parseFloat(estM[3]), upper: parseFloat(estM[4]),
        confidence: parseFloat(estM[5]),
      })
      i++; continue
    }

    // ── Linkage ───────────────────────────────────────────────────────────
    const linkM = line.match(/^\s*Linkage:\s+\[(.+?)\]\s+recombination\s+=\s+([\d.]+)\s+distance\s+=\s+(.+)/)
    if (linkM) {
      flushPrint()
      const genes = linkM[1].split(/\s*[—–\-]+\s*/).map(s => s.trim()).filter(Boolean)
      const distStr = linkM[3].trim()
      items.push({
        type: 'linkage', genes,
        recombination: parseFloat(linkM[2]),
        distance: distStr !== 'N/A' ? parseFloat(distStr) : undefined,
      })
      i++; continue
    }

    // ── Sex-linked ────────────────────────────────────────────────────────
    const sexM = line.match(/^\s*(\w+)\s+marked as sex-linked/)
    if (sexM) {
      flushPrint()
      items.push({ type: 'sexlinked', gene: sexM[1] })
      i++; continue
    }

    // ── Blood group ───────────────────────────────────────────────────────
    const bloodM = line.match(/^\s*Blood group\s+(\w+)\s+\[(ABO|Rh)\]:\s+(.+)/)
    if (bloodM) {
      flushPrint()
      items.push({ type: 'bloodgroup', id: bloodM[1], system: bloodM[2], result: bloodM[3].trim() })
      i++; continue
    }

    // ── Pred ──────────────────────────────────────────────────────────────
    const predM = line.match(/^\s*Pred\s+(\w+)\s+generation\s+(\d+):\s*(.*)/)
    if (predM) {
      flushPrint()
      const noDataMsg = predM[3].trim()
      const item: PredItem = {
        type: 'pred', id: predM[1], generation: parseInt(predM[2]),
        genoDist: {}, phenoDist: {}, noData: noDataMsg.length > 0,
      }
      i++
      if (!noDataMsg) {
        while (i < lines.length && /^\s{4}/.test(lines[i])) {
          const c = lines[i]
          if (/genotype/.test(c) && c.includes('|')) item.genoDist = parseInlineDist(c)
          else if (/phenotype/.test(c) && c.includes('|')) item.phenoDist = parseInlineDist(c)
          i++
        }
      }
      items.push(item)
      continue
    }

    // ── Infer parents ─────────────────────────────────────────────────────
    const inferPM = line.match(/^\s*Infer parents of\s+(\w+)(?:\s+\([^)]*\))?:\s*(.*)/)
    if (inferPM) {
      flushPrint()
      const noDataMsg = inferPM[2].trim()
      const item: InferItem = {
        type: 'infer', mode: 'parents', subject: inferPM[1], details: [],
        noData: noDataMsg.includes('no genotype'),
      }
      i++
      if (!item.noData) {
        while (i < lines.length && /^\s{4}/.test(lines[i])) {
          item.details.push(lines[i].trim())
          i++
        }
      }
      items.push(item)
      continue
    }

    // ── Infer field ───────────────────────────────────────────────────────
    const inferFM = line.match(/^\s*Infer\s+(\w+)\s+from:\s+(.+)/)
    if (inferFM) {
      flushPrint()
      const item: InferItem = {
        type: 'infer', mode: 'field', subject: inferFM[2].trim(),
        field: inferFM[1], details: [],
      }
      i++
      while (i < lines.length && /^\s{4}/.test(lines[i])) {
        item.details.push(lines[i].trim())
        i++
      }
      items.push(item)
      continue
    }

    // ── Raw print output ──────────────────────────────────────────────────
    if (trimmed && !isStructuredLine(line)) {
      printBuffer.push(trimmed)
    }
    i++
  }

  flushPrint()
  return items
}
