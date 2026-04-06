import type * as Monaco from 'monaco-editor'

export function defineELSDLanguage(monaco: typeof Monaco) {
  if (monaco.languages.getLanguages().some(l => l.id === 'elsd')) return

  monaco.languages.register({ id: 'elsd' })

  monaco.languages.setMonarchTokensProvider('elsd', {
    defaultToken: 'invalid',
    keywords: [
      'gene', 'genes', 'parent', 'generation', 'boolean', 'string', 'number',
      'if', 'then', 'else', 'elif', 'while', 'do', 'for', 'in', 'end',
      'and', 'or', 'not', 'true', 'false',
      'set', 'dom', 'label', 'phenotype', 'genotype', 'codominance',
      'location', 'linked', 'sexlinked', 'autosomal', 'ratio',
      'cross', 'find', 'pred', 'estimate', 'print', 'all', 'infer',
      'parents', 'from', 'probability', 'given', 'confidence',
      'linkage', 'recombination', 'distance', 'bloodgroup', 'system', 'carries',
      'ABO', 'Rh', 'x',
    ],
    tokenizer: {
      root: [
        [/[ \t\r\n]+/, 'white'],
        [/\/\/.*$/, 'comment'],
        [/\/\*/, 'comment', '@block_comment'],
        [/"([^"\\]|\\.)*$/, 'string.invalid'],
        [/"/, 'string', '@string_double'],
        [/-?\d+(\.\d+)?/, 'number.float'],
        [/[A-Za-z_][A-Za-z0-9_]*/, {
          cases: { '@keywords': 'keyword', '@default': 'identifier' },
        }],
        [/->/, 'operator'],
        [/[+\-*\/=<>!?:;,.()\[\]]/, 'operator'],
      ],
      string_double: [
        [/[^"\\]+/, 'string'],
        [/\\./, 'string.escape'],
        [/"/, 'string', '@pop'],
      ],
      block_comment: [
        [/[^/*]+/, 'comment'],
        [/\*\//, 'comment', '@pop'],
        [/[/*]/, 'comment'],
      ],
    },
  } as Monaco.languages.IMonarchLanguage)

  monaco.languages.setLanguageConfiguration('elsd', {
    comments: { lineComment: '//', blockComment: ['/*', '*/'] },
    brackets: [['(', ')'], ['[', ']']],
    autoClosingPairs: [
      { open: '(', close: ')' }, { open: '[', close: ']' }, { open: '"', close: '"' },
    ],
    surroundingPairs: [
      { open: '(', close: ')' }, { open: '[', close: ']' }, { open: '"', close: '"' },
    ],
  })
}
