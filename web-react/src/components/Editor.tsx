import { MutableRefObject, useCallback } from 'react'
import MonacoEditor, { BeforeMount, OnMount } from '@monaco-editor/react'
import { defineELSDLanguage } from '../lib/elsdLanguage'
import type * as MonacoType from 'monaco-editor'

interface ErrorMarker {
  startLineNumber: number; startColumn: number
  endLineNumber: number; endColumn: number
  message: string; severity: 8
}

interface Props {
  value: string
  onChange: (v: string) => void
  onRun: () => void
  editorRef: MutableRefObject<{ setMarkers: (m: ErrorMarker[]) => void } | null>
}

const beforeMount: BeforeMount = (monaco) => {
  defineELSDLanguage(monaco)
}

export function Editor({ value, onChange, onRun, editorRef }: Props) {
  const handleMount: OnMount = useCallback((editor, monaco) => {
    // Ctrl / Cmd + Enter → run
    editor.addCommand(
      monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter,
      onRun,
    )

    // Expose setMarkers so App can push error decorations
    editorRef.current = {
      setMarkers(markers: ErrorMarker[]) {
        const model = editor.getModel()
        if (!model) return
        monaco.editor.setModelMarkers(
          model,
          'elsd',
          markers as MonacoType.editor.IMarkerData[],
        )
      },
    }
  }, [onRun, editorRef])

  return (
    <div className="flex-[55] min-w-0 flex flex-col overflow-hidden">
      <PanelLabel>Editor</PanelLabel>
      <div className="flex-1 overflow-hidden">
        <MonacoEditor
          height="100%"
          language="elsd"
          theme="vs-dark"
          value={value}
          onChange={(v) => onChange(v ?? '')}
          beforeMount={beforeMount}
          onMount={handleMount}
          options={{
            fontSize: 14,
            lineHeight: 22,
            fontFamily: '"JetBrains Mono", "Fira Code", Consolas, monospace',
            fontLigatures: true,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            automaticLayout: true,
            renderLineHighlight: 'gutter',
            lineNumbers: 'on',
            wordWrap: 'off',
            padding: { top: 12, bottom: 12 },
            smoothScrolling: true,
            cursorBlinking: 'smooth',
            cursorSmoothCaretAnimation: 'on',
          }}
        />
      </div>
    </div>
  )
}

function PanelLabel({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex items-center h-8 px-4 text-[11px] font-medium tracking-widest
                    uppercase text-slate-500 border-b border-white/[0.05] bg-[#0d0d1a]/60
                    shrink-0">
      {children}
    </div>
  )
}
