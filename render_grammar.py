#!/usr/bin/env python3
"""
Render ANTLR parser grammar as a DOT graph showing rules -> alternatives -> symbols.
Usage: python3 render_grammar.py ELSDParser.g4 [out.dot] [out.png]
"""
import sys
import re
import subprocess

GRAMMAR_PATH = sys.argv[1] if len(sys.argv) > 1 else 'ELSDParser.g4'
OUT_DOT = sys.argv[2] if len(sys.argv) > 2 else 'grammar.dot'
OUT_PNG = sys.argv[3] if len(sys.argv) > 3 else 'grammar.png'

with open(GRAMMAR_PATH, 'r', encoding='utf-8') as f:
    text = f.read()

# remove C-style and // comments
text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
text = re.sub(r"//.*?$", "", text, flags=re.M)

# find rule definitions: name : body ;
pattern = re.compile(r"^([a-zA-Z_][a-zA-Z0-9_]*)\s*:\s*(.*?)\s*;", re.S | re.M)
rules = []
for m in pattern.finditer(text):
    name = m.group(1)
    body = m.group(2).strip()
    rules.append((name, body))

if not rules:
    print('No parser rules found in', GRAMMAR_PATH)
    sys.exit(1)

# helper to split alternatives by top-level | (ignoring parentheses/brackets/braces)

def split_alternatives(body):
    alts = []
    cur = []
    depth = 0
    for ch in body:
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth = max(depth - 1, 0)
        if ch == '|' and depth == 0:
            alts.append(''.join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if cur:
        alts.append(''.join(cur).strip())
    return alts

# tokenize an alternative into symbols (split by whitespace but keep punctuation tokens)

def tokenize_alt(alt):
    # remove surrounding grouping parentheses for clarity
    a = alt
    # split respecting parentheses groups roughly
    tokens = re.findall(r"\w+|\'[^']*\'|\(|\)|->|:|,|;|\.|\[|\]|\\S", a)
    # fallback: simple split
    if not tokens:
        tokens = [t for t in re.split(r"\s+", a) if t]
    return tokens

# produce DOT
nodes = []
edges = []
node_id = 0

safe = lambda s: re.sub(r"[^a-zA-Z0-9_]+", "_", s)

for rname, body in rules:
    rid = f"rule_{safe(rname)}"
    nodes.append((rid, f"rule: {rname}", 'box'))
    alts = split_alternatives(body)
    for i, alt in enumerate(alts):
        aid = f"{rid}_alt_{i}"
        label = alt
        if len(label) > 80:
            label = label[:77] + '...'
        nodes.append((aid, label, 'ellipse'))
        edges.append((rid, aid))
        toks = tokenize_alt(alt)
        # create symbol nodes for tokens
        for j, tok in enumerate(toks):
            if tok.strip() == '':
                continue
            sid = f"{aid}_sym_{j}_{safe(tok)}"
            nodes.append((sid, tok, 'note'))
            edges.append((aid, sid))

# deduplicate nodes while preserving style
unique = {}
for nid, label, style in nodes:
    if nid not in unique:
        unique[nid] = (label, style)

with open(OUT_DOT, 'w', encoding='utf-8') as f:
    f.write('digraph grammar {\n')
    f.write('  rankdir=LR;\n')
    f.write('  node [fontname="Helvetica"];\n')
    for nid, (label, style) in unique.items():
        lbl = label.replace('"', '\\"')
        shape = 'box' if style == 'box' else ('note' if style == 'note' else 'ellipse')
        f.write(f'  "{nid}" [label="{lbl}", shape={shape}];\n')
    f.write('\n')
    for a, b in edges:
        f.write(f'  "{a}" -> "{b}";\n')
    f.write('}\n')

print('Wrote', OUT_DOT)

# try to render using dot if available
try:
    subprocess.run(['dot', '-V'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print('Rendering to', OUT_PNG)
    subprocess.check_call(['dot', '-Tpng', OUT_DOT, '-o', OUT_PNG])
    print('Rendered', OUT_PNG)
except Exception:
    print('Graphviz `dot` not found or rendering failed. You can render manually: dot -Tpng', OUT_DOT, '-o', OUT_PNG)
