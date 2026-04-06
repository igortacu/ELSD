/* ELSD Online Compiler — Monaco integration and API wiring */

require(['vs/editor/editor.main'], function () {

  // 1. Register ELSD language
  monaco.languages.register({ id: 'elsd' });

  // 2. Monarch tokenizer (keywords from ELSDLexer.g4)
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
      'linkage', 'recombination', 'distance',
      'bloodgroup', 'system', 'carries',
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
          cases: {
            '@keywords': 'keyword',
            '@default': 'identifier',
          }
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
  });

  // 3. Language configuration (brackets, auto-close)
  monaco.languages.setLanguageConfiguration('elsd', {
    comments: {
      lineComment: '//',
      blockComment: ['/*', '*/'],
    },
    brackets: [['(', ')'], ['[', ']']],
    autoClosingPairs: [
      { open: '(', close: ')' },
      { open: '[', close: ']' },
      { open: '"', close: '"' },
    ],
    surroundingPairs: [
      { open: '(', close: ')' },
      { open: '[', close: ']' },
      { open: '"', close: '"' },
    ],
  });

  // 4. Default editor content
  var DEFAULT_CODE = [
    '// ELSD Genetics DSL — try editing and clicking Run',
    '',
    '// Declarations',
    'gene A;',
    'parent mom;',
    'parent dad;',
    '',
    '// Set a simple genotype',
    'set genotype A = "Aa";',
    '',
    '// Perform a basic cross',
    'cross mom x dad -> child ratio 1, 1;',
    '',
    '// Queries and output',
    'find genotype A;',
    'probability genotype(A);',
    'print genotype A;',
    'print "done";',
  ].join('\n');

  // 5. Create editor instance
  var editor = monaco.editor.create(
    document.getElementById('editor-container'),
    {
      value: DEFAULT_CODE,
      language: 'elsd',
      theme: 'vs-dark',
      fontSize: 14,
      automaticLayout: true,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      renderLineHighlight: 'line',
      lineNumbers: 'on',
      wordWrap: 'off',
    }
  );

  // 6. Output element references
  var stdoutEl    = document.getElementById('output-stdout');
  var stderrEl    = document.getElementById('output-stderr');
  var statusBadge = document.getElementById('status-badge');
  var btnRun      = document.getElementById('btn-run');
  var btnClear    = document.getElementById('btn-clear');

  // 7. Run the current editor content
  function runCode() {
    var code = editor.getValue();

    stdoutEl.textContent = '';
    stderrEl.textContent = '';
    statusBadge.className = 'badge';
    statusBadge.textContent = '';
    monaco.editor.setModelMarkers(editor.getModel(), 'elsd', []);

    btnRun.disabled = true;
    btnRun.textContent = 'Running\u2026';

    fetch('/api/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: code }),
    })
      .then(function (res) {
        if (!res.ok) { throw new Error('Server error: ' + res.status); }
        return res.json();
      })
      .then(function (data) {
        stdoutEl.textContent = data.stdout || '';
        stderrEl.textContent = data.stderr || '';

        var hasError = data.returncode !== 0 || (data.stderr && data.stderr.trim().length > 0);
        if (hasError) {
          statusBadge.className = 'badge error';
          statusBadge.textContent = 'Error';
          mapErrorsToMarkers(data.stderr || '');
        } else {
          statusBadge.className = 'badge ok';
          statusBadge.textContent = 'OK';
        }
      })
      .catch(function (err) {
        stderrEl.textContent = 'Failed to reach server: ' + err.message;
        statusBadge.className = 'badge error';
        statusBadge.textContent = 'Error';
      })
      .finally(function () {
        btnRun.disabled = false;
        btnRun.innerHTML = '&#9654; Run';
      });
  }

  // 8. Map stderr error lines to Monaco editor markers
  // Error format from DiagnosticErrorListener:
  //   "  ERROR  line N:M  \u2013  <message>"
  function mapErrorsToMarkers(stderr) {
    var model = editor.getModel();
    if (!model) { return; }

    var markers = [];
    var pattern = /ERROR\s+line\s+(\d+):(\d+)\s+[\u2013\-]+\s+(.*)/g;
    var match;

    while ((match = pattern.exec(stderr)) !== null) {
      var lineNum = parseInt(match[1], 10);
      var col     = parseInt(match[2], 10) + 1;
      var message = match[3].trim();
      var lineContent = model.getLineContent(lineNum) || '';
      var endCol = Math.max(col + 1, lineContent.length + 1);

      markers.push({
        startLineNumber: lineNum,
        startColumn: col,
        endLineNumber: lineNum,
        endColumn: endCol,
        message: message,
        severity: monaco.MarkerSeverity.Error,
      });
    }

    if (markers.length > 0) {
      monaco.editor.setModelMarkers(model, 'elsd', markers);
    }
  }

  // 9. Button handlers and keyboard shortcut
  btnRun.addEventListener('click', runCode);

  btnClear.addEventListener('click', function () {
    stdoutEl.textContent = '';
    stderrEl.textContent = '';
    statusBadge.className = 'badge';
    statusBadge.textContent = '';
    monaco.editor.setModelMarkers(editor.getModel(), 'elsd', []);
  });

  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, runCode);

  // 10. Drag-to-resize the two panels
  var handle      = document.getElementById('resize-handle');
  var panelEditor = document.querySelector('.panel-editor');
  var panelOutput = document.querySelector('.panel-output');
  var panels      = document.querySelector('.panels');

  var dragging        = false;
  var startX          = 0;
  var startEditorFlex = 55;
  var startOutputFlex = 45;

  handle.addEventListener('mousedown', function (e) {
    dragging = true;
    startX = e.clientX;
    startEditorFlex = parseFloat(getComputedStyle(panelEditor).flexGrow) || 55;
    startOutputFlex = parseFloat(getComputedStyle(panelOutput).flexGrow) || 45;
    handle.classList.add('dragging');
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    e.preventDefault();
  });

  document.addEventListener('mousemove', function (e) {
    if (!dragging) { return; }
    var totalWidth  = panels.offsetWidth;
    var dx          = e.clientX - startX;
    var totalFlex   = startEditorFlex + startOutputFlex;
    var newEditor   = Math.max(20, Math.min(80, startEditorFlex + (dx / totalWidth) * totalFlex));
    panelEditor.style.flex = String(newEditor);
    panelOutput.style.flex = String(totalFlex - newEditor);
  });

  document.addEventListener('mouseup', function () {
    if (!dragging) { return; }
    dragging = false;
    handle.classList.remove('dragging');
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
  });

});
