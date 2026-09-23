# -*- coding: utf-8 -*-
"""Draws the README diagrams, each at a narrow and a wide width, on both of GitHub's grounds.

Run this to write build/*.html, then render.js to screenshot them into PNGs beside
this file. Type is sized so the narrow variant stays legible after GitHub scales it
to a phone's ~358px column: a 15px label lands at 12px, a 12px sub-label at 9.8px.
"""
import json, os

TOP, RIGHT, LEFT = '#4BF2B0', '#17B87C', '#0E9E68'


def slab(cx, cy, w, h, top=TOP, right=RIGHT, left=LEFT, op=''):
    """One isometric box. cx, cy is the centre of the top face, w its horizontal
    half-width, h the depth. 2:1 isometric, so a top face of half-width w is w tall."""
    hw = w / 2.0
    t = f'M {cx-w} {cy} L {cx} {cy-hw} L {cx+w} {cy} L {cx} {cy+hw} Z'
    l = f'M {cx-w} {cy} L {cx-w} {cy+h} L {cx} {cy+hw+h} L {cx} {cy+hw} Z'
    r = f'M {cx+w} {cy} L {cx+w} {cy+h} L {cx} {cy+hw+h} L {cx} {cy+hw} Z'
    return (f'<path d="{t}" fill="{top}"{op}/>'
            f'<path d="{l}" fill="{left}"{op}/>'
            f'<path d="{r}" fill="{right}"{op}/>')

DARK = dict(
    name='dark', ground='#0D1117', panel='#0C0D0F', chip='#101012', line='#2B2A32',
    heading='#FCFCFD', text='#EDEDEF', body='#B4B4BB', muted='#9A9AA2', dim='#8A8A92',
    mint='#28FEB4', mintText='#28FEB4', mintSoft='rgba(40,254,180,.10)',
    mintEdge='rgba(40,254,180,.34)', mintInk='#08090A', rail='#3A3944',
    grayT='#3A3944', grayR='#2B2A32', grayL='#212029', label='GitHub dark',
)
LIGHT = dict(
    name='light', ground='#FFFFFF', panel='#F6F8FA', chip='#FFFFFF', line='#D1D9E0',
    heading='#08090A', text='#1F2328', body='#4A5259', muted='#59636E', dim='#6B7681',
    mint='#28FEB4', mintText='#0A6B47', mintSoft='rgba(40,254,180,.18)',
    mintEdge='rgba(14,158,104,.45)', mintInk='#08090A', rail='#C4CDD5',
    grayT='#5D5D66', grayR='#4A4A53', grayL='#3A3944', label='GitHub light',
)

# ---------------------------------------------------------------- primitives

def cube(cx, cy, w, h, kind='mint'):
    if kind == 'mint':
        return slab(cx, cy, w, h)
    if kind == 'red':
        return slab(cx, cy, w, h, '#F5889E', '#D8556F', '#B0324C')
    if kind == 'pale':
        return slab(cx, cy, w, h, '#8CF7CE', '#4FD8A4', '#2FB985')
    return slab(cx, cy, w, h, '#3A3944', '#2B2A32', '#212029')

def glyph(size, kind='mint'):
    """A single cube as an inline glyph, sized in px."""
    return (f'<svg class="g" width="{size}" height="{size}" viewBox="0 0 24 24" aria-hidden="true">'
            + cube(12, 10, 9, 8, kind) + '</svg>')

def cluster(size):
    """Four cubes: the Application glyph."""
    body = cube(12, 15, 10, 3) + cube(12, 6, 5, 4) + cube(6.5, 10, 5, 4) + cube(17.5, 10, 5, 4)
    return f'<svg class="g" width="{size}" height="{size}" viewBox="0 0 24 24" aria-hidden="true">{body}</svg>'

# ---------------------------------------------------------------- diagram 1

def abstraction_layer(t):
    """An isometric stack: who builds, what they build against, what carries it."""
    g = (
        cube(112, 250, 100, 15, 'gray')
        + cube(112, 175, 87, 34)
        + cube(112, 135, 72, 15, 'pale')
        + cube(70, 72, 20, 17)
        + cube(154, 72, 20, 17, 'red')
    )
    feed = ''.join(
        f'<path d="M {x0} 99 L {x1} 117" stroke="{t["dim"]}" stroke-width="1.5" '
        f'stroke-dasharray="2 3" fill="none"/>' for x0, x1 in ((70, 88), (154, 136)))
    leaders = ''.join(
        f'<path d="M {x} {y} L 232 {y}" stroke="{t["line"]}" stroke-width="1"/>'
        f'<circle cx="232" cy="{y}" r="2" fill="{t["line"]}"/>'
        for x, y in ((184, 135), (199, 175), (212, 250)))
    svg = (f'<svg width="440" height="292" viewBox="0 30 440 292" role="img" '
           f'aria-label="Developers and AI agents work through the Kinotic application model, '
           f'which sits on Kinotic OS, which sits on Kubernetes, cloud, networking and storage">'
           f'{leaders}{g}{feed}'
           f'<text x="70" y="44" class="t-cap" text-anchor="middle">Developers</text>'
           f'<text x="154" y="44" class="t-cap" text-anchor="middle">AI agents</text>'
           f'<text x="244" y="131" class="t-name">Kinotic application model</text>'
           f'<text x="244" y="148" class="t-sub">applications · projects · services</text>'
           f'<text x="244" y="171" class="t-name">Kinotic OS</text>'
           f'<text x="244" y="188" class="t-sub">persistence · identity</text>'
           f'<text x="244" y="203" class="t-sub">service communication</text>'
           f'<text x="244" y="218" class="t-sub">delivery · observability</text>'
           f'<text x="244" y="246" class="t-name-dim">Kubernetes · cloud</text>'
           f'<text x="244" y="263" class="t-sub">networking · storage</text>'
           f'</svg>')
    return f'<div class="d" style="width:440px;height:292px">{svg}</div>'

# ---------------------------------------------------------------- diagram 2

def core_concepts(t):
    projects = [['Microservices', 'Persistence models'],
                ['Frontends', 'UI components'],
                ['Batch jobs', 'MCP tools']]
    cards = ''.join(
        f'<div class="card">'
        f'<div class="card-h">{glyph(17)}<span class="card-t">Project</span></div>'
        f'<div class="chips">' + ''.join(f'<span class="chip">{a}</span>' for a in arts) + '</div>'
        f'</div>' for arts in projects)
    return (f'<div class="d" style="width:440px;height:256px">'
            f'<div class="outer">'
            f'<div class="outer-h">{cluster(26)}'
            f'<span class="outer-t">Application</span></div>'
            f'<div class="outer-s">the boundary of a complete software system</div>'
            f'<div class="stack">{cards}</div>'
            f'</div></div>')

# ---------------------------------------------------------------- diagram 3

def environments(t):
    """One vertical timeline. Rings mark the branch and its throwaway environment,
    cubes mark the three environments a change is promoted through."""
    ring = (lambda y: f'<circle cx="30" cy="{y}" r="5" fill="{t["ground"]}" '
                      f'stroke="{t["mintEdge"]}" stroke-width="1.5"/>')
    steps = [('Development', 148), ('Staging', 204), ('Production', 260)]
    cubes = ''.join(cube(30, y - 6, 14, 12) for _, y in steps)
    names = ''.join(f'<text x="64" y="{y + 5}" class="t-name">{n}</text>' for n, y in steps)
    line = (f'<path d="M 30 24 L 30 138" stroke="{t["mintEdge"]}" stroke-width="1.5" '
            f'stroke-dasharray="4 4"/>'
            f'<path d="M 30 138 L 30 256" stroke="{t["rail"]}" stroke-width="1.5"/>'
            f'<path d="M 30 264 l -3.5 -8 h 7 z" fill="{t["rail"]}"/>')
    promote = ''.join(f'<text x="64" y="{y}" class="t-cap-up">promote</text>' for y in (180, 236))
    svg = (f'<svg width="300" height="280" viewBox="0 8 300 280" role="img" '
           f'aria-label="A feature branch gets an isolated environment that is built and tested, '
           f'then promoted through development, staging and production">'
           f'{line}{ring(24)}{ring(76)}{cubes}'
           f'<text x="64" y="29" class="t-name">Feature branch</text>'
           f'<text x="64" y="81" class="t-name">Isolated environment</text>'
           f'<text x="64" y="97" class="t-sub">built and tested</text>'
           f'{names}{promote}</svg>')
    return f'<div class="d" style="width:300px;height:280px">{svg}</div>'

# ---------------------------------------------------------------- diagram 4

def architecture(t):
    clients = ['Consoles and UIs', 'CLI and SDKs', 'AI agents']
    client_tiles = ''.join(f'<div class="tile tile-c"><div class="tile-t">{c}</div></div>'
                           for c in clients)
    mods = [('kinotic-domain', 'organizations · identities'),
            ('kinotic-persistence<br>kinotic-sql', 'entities · queries'),
            ('kinotic-management-api<br>kinotic-system-api<br>kinotic-grind', 'operations · builds')]
    mod_tiles = ''.join(
        f'<div class="tile tile-in"><div class="mono mono-b">{a}</div>'
        f'<div class="tile-s">{b}</div></div>' for a, b in mods)
    infra = [('Elasticsearch', 'entities · state'),
             ('Loki · Mimir · Tempo', 'logs · metrics · traces'),
             ('VM nodes', 'Firecracker · builds and microservices')]
    infra_tiles = ''.join(
        f'<div class="tile tile-dark"><div class="tile-t">{a}</div><div class="tile-s">{b}</div></div>'
        for a, b in infra)

    def drops(xs, h, w=440):
        p = ''.join(f'<path d="M {x} 0 L {x} {h-6}" stroke="{t["line"]}" stroke-width="1"/>'
                    f'<path d="M {x} {h} l -3.5 -6 h 7 z" fill="{t["line"]}"/>' for x in xs)
        return f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" aria-hidden="true">{p}</svg>'

    # inside the server panel a left spine taps each module in turn
    spine = (f'<svg class="spine" width="22" height="100%" viewBox="0 0 22 260" '
             f'preserveAspectRatio="none" aria-hidden="true">'
             f'<path d="M 11 0 L 11 236" stroke="{t["line"]}" stroke-width="1" fill="none"/>'
             f'</svg>')
    return (f'<div class="d" style="width:440px;height:739px">'
            f'<div class="row3 g10">{client_tiles}</div>'
            f'{drops([72, 220, 368], 20)}'
            f'<div class="gw"><span class="mono mono-gw">kinotic-api-gateway</span>'
            f'<span class="gw-s">STOMP over WebSocket · REST · MCP</span></div>'
            f'{drops([220], 18)}'
            f'<div class="server"><span class="server-tab">kinotic-server</span>'
            f'<div class="tile tile-in tile-core"><span class="mono mono-b">kinotic-core</span>'
            f'<span class="tile-s">runtime kernel · service registry · RPC</span></div>'
            f'{drops([220], 16, 408)}'
            f'<div class="stack">{mod_tiles}</div></div>'
            f'{drops([220], 20)}'
            f'<div class="stack">{infra_tiles}</div>'
            f'</div>')

# ---------------------------------------------------------------- diagram 5

def vision(t):
    def lane(title, steps, dashed):
        rows = ''
        for i, (s, hot) in enumerate(steps):
            if i:
                rows += f'<span class="v-down{" v-dash" if dashed else ""}">&#8595;</span>'
            cls = 'v-chip v-hot' if hot else ('v-chip v-cold' if dashed else 'v-chip')
            rows += f'<span class="{cls}">{s}</span>'
        return (f'<div class="lane"><div class="lane-l">{title}</div>'
                f'<div class="lane-c">{rows}</div></div>')
    old = lane('For decades', [('Human', 0), ('Source code', 0), ('Infrastructure', 0),
                               ('Production', 0)], True)
    new = lane('Increasingly', [('Human', 0), ('Intent', 1), ('AI agent', 1), ('Software', 0),
                                ('Production', 0)], False)
    return f'<div class="d" style="width:440px;height:274px"><div class="lanes">{old}{new}</div></div>'

# ================================================================ wide variants
# Same content and type scale as the narrow set, laid out for a desktop column.

def w_abstraction_layer(t):
    g = (cube(165, 282, 120, 18, 'gray') + cube(165, 196, 104, 40)
         + cube(165, 150, 86, 18, 'pale')
         + cube(126, 78, 24, 20) + cube(204, 78, 24, 20, 'red'))
    feed = ''.join(
        f'<path d="M {x0} 112 L {x1} 130" stroke="{t["dim"]}" stroke-width="1.5" '
        f'stroke-dasharray="2 3" fill="none"/>' for x0, x1 in ((126, 144), (204, 186)))
    leaders = ''.join(
        f'<path d="M {x} {y} L 300 {y}" stroke="{t["line"]}" stroke-width="1"/>'
        f'<circle cx="300" cy="{y}" r="2" fill="{t["line"]}"/>'
        for x, y in ((251, 150), (269, 196), (285, 282)))
    svg = (f'<svg width="560" height="322" viewBox="0 26 560 322" role="img" '
           f'aria-label="Developers and AI agents work through the Kinotic application model, '
           f'which sits on Kinotic OS, which sits on Kubernetes, cloud, networking and storage">'
           f'{leaders}{g}{feed}'
           f'<text x="126" y="48" class="t-cap" text-anchor="middle">Developers</text>'
           f'<text x="204" y="48" class="t-cap" text-anchor="middle">AI agents</text>'
           f'<text x="312" y="146" class="t-name">Kinotic application model</text>'
           f'<text x="312" y="163" class="t-sub">applications · projects · services</text>'
           f'<text x="312" y="186" class="t-name">Kinotic OS</text>'
           f'<text x="312" y="203" class="t-sub">persistence · identity</text>'
           f'<text x="312" y="218" class="t-sub">service communication</text>'
           f'<text x="312" y="233" class="t-sub">delivery · observability</text>'
           f'<text x="312" y="278" class="t-name-dim">Kubernetes · cloud</text>'
           f'<text x="312" y="295" class="t-sub">networking · storage</text>'
           f'</svg>')
    return f'<div class="d" style="width:560px;height:322px">{svg}</div>'


def w_core_concepts(t):
    projects = [['Microservices', 'Persistence models'], ['Frontends', 'UI components'],
                ['Batch jobs', 'MCP tools']]
    cards = ''.join(
        f'<div class="card card-col">'
        f'<div class="card-h">{glyph(17)}<span class="card-t">Project</span></div>'
        f'<div class="chips chips-col">' + ''.join(f'<span class="chip">{a}</span>' for a in arts)
        + '</div></div>' for arts in projects)
    return (f'<div class="d" style="width:620px;height:165px">'
            f'<div class="outer">'
            f'<div class="outer-h">{cluster(26)}<span class="outer-t">Application</span>'
            f'<span class="outer-s outer-s-inline">the boundary of a complete software system</span></div>'
            f'<div class="row3 g12">{cards}</div></div></div>')


def w_environments(t):
    stations = [('Development', 150), ('Staging', 330), ('Production', 500)]
    cubes = ''.join(cube(x, 146, 17, 15) for _, x in stations)
    names = ''.join(f'<text x="{x}" y="200" class="t-name" text-anchor="middle">{n}</text>'
                    for n, x in stations)
    rail = (f'<path d="M 108 170 L 566 170" stroke="{t["rail"]}" stroke-width="1.5"/>'
            f'<path d="M 574 170 l -8 -3.5 v 7 z" fill="{t["rail"]}"/>')
    promote = ''.join(f'<text x="{x}" y="162" class="t-cap-up" text-anchor="middle">promote</text>'
                      for x in (240, 415))
    drop = (f'<path d="M 232 4 C 232 70 150 64 150 122" fill="none" stroke="{t["mintEdge"]}" '
            f'stroke-width="1.5" stroke-dasharray="4 4"/>'
            f'<path d="M 150 131 l -4 -8 h 8 z" fill="{t["mintEdge"]}"/>')
    svg = (f'<svg width="620" height="214" viewBox="0 0 620 214" role="img" '
           f'aria-label="A feature branch gets an isolated environment that is built and tested, '
           f'then promoted through development, staging and production">'
           f'{drop}{rail}{cubes}{names}{promote}</svg>')
    return (f'<div class="d" style="width:620px;height:237px">'
            f'<div class="branch branch-row">'
            f'<span class="pill">Feature branch</span><span class="arrow">&#8594;</span>'
            f'<span class="pill pill-mint pill-row">Isolated environment'
            f'<span class="pill-s">built and tested</span></span></div>{svg}</div>')


def w_architecture(t):
    clients = [('Consoles and UIs', 'kinotic-frontend'), ('CLI and SDKs', 'kinotic-js'),
               ('AI agents', 'MCP hosts')]
    client_tiles = ''.join(
        f'<div class="tile"><div class="tile-t">{a}</div><div class="mono">{b}</div></div>'
        for a, b in clients)
    mods = [('kinotic-domain', 'organizations · identities'),
            ('kinotic-persistence<br>kinotic-sql', 'entities · queries'),
            ('kinotic-management-api<br>kinotic-system-api<br>kinotic-grind', 'operations · builds')]
    mod_tiles = ''.join(
        f'<div class="tile tile-in"><div class="mono mono-b">{a}</div>'
        f'<div class="tile-s">{b}</div></div>' for a, b in mods)
    infra = [('Elasticsearch', 'entities · state'),
             ('Loki · Mimir · Tempo', 'logs · metrics · traces'),
             ('VM nodes', 'Firecracker · builds and microservices')]
    infra_tiles = ''.join(
        f'<div class="tile tile-dark"><div class="tile-t">{a}</div><div class="tile-s">{b}</div></div>'
        for a, b in infra)

    W = 880

    def drops(xs, h, w=W):
        p = ''.join(f'<path d="M {x} 0 L {x} {h-6}" stroke="{t["line"]}" stroke-width="1"/>'
                    f'<path d="M {x} {h} l -3.5 -6 h 7 z" fill="{t["line"]}"/>' for x in xs)
        return f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" aria-hidden="true">{p}</svg>'

    def fan(pairs, h, w=W):
        p = ''
        for x0, x1 in pairs:
            p += (f'<path d="M {x0} 0 C {x0} {h*0.55} {x1} {h*0.45} {x1} {h-6}" fill="none" '
                  f'stroke="{t["line"]}" stroke-width="1"/>'
                  f'<path d="M {x1} {h} l -3.5 -6 h 7 z" fill="{t["line"]}"/>')
        return f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" aria-hidden="true">{p}</svg>'

    # module tiles are 274 wide inside a panel padded 16 → centres 153, 440, 727
    return (f'<div class="d" style="width:{W}px;height:470px">'
            f'<div class="row3 g16">{client_tiles}</div>'
            f'{drops([148, 440, 732], 22)}'
            f'<div class="gw gw-row"><span class="mono mono-gw">kinotic-api-gateway</span>'
            f'<span class="gw-s">STOMP over WebSocket · REST · MCP</span></div>'
            f'{drops([440], 20)}'
            f'<div class="server"><span class="server-tab">kinotic-server</span>'
            f'<div class="tile tile-in tile-core tile-core-row">'
            f'<span class="mono mono-b">kinotic-core</span>'
            f'<span class="tile-s">runtime kernel · service registry · RPC over the clustered event bus</span></div>'
            f'{fan([(440, 153), (440, 440), (440, 727)], 22, 848)}'
            f'<div class="row3 g12">{mod_tiles}</div></div>'
            f'{fan([(153, 148), (440, 148), (727, 440), (727, 732)], 44)}'
            f'<div class="row3 g16">{infra_tiles}</div></div>')


def w_vision(t):
    def lane(title, steps, dashed):
        chips = ''
        for i, (s, hot) in enumerate(steps):
            if i:
                chips += f'<span class="v-arrow{" v-dash" if dashed else ""}">&#8594;</span>'
            cls = 'v-chip v-hot' if hot else ('v-chip v-cold' if dashed else 'v-chip')
            chips += f'<span class="{cls} v-chip-row">{s}</span>'
        return (f'<div class="lane lane-row"><div class="lane-l lane-l-row">{title}</div>'
                f'<div class="lane-r">{chips}</div></div>')
    old = lane('For decades', [('Human', 0), ('Source code', 0), ('Infrastructure', 0),
                               ('Production', 0)], True)
    new = lane('Increasingly', [('Human', 0), ('Intent', 1), ('AI agent', 1), ('Software', 0),
                                ('Production', 0)], False)
    return (f'<div class="d" style="width:660px;height:84px">'
            f'<div class="lanes lanes-row">{old}{new}</div></div>')

# `.pane-l` / `.pane-d` are only a scoping hook: every colour lives in scoped() below,
# so one stylesheet can carry both themes.
CSS = """
*{box-sizing:border-box}
body{margin:0;font-family:'Figtree',system-ui,sans-serif}
.d{position:relative}
.d>svg{display:block}
.g{display:block;flex:none}
"""

def scoped(t):
    """Theme-scoped rules. Every colour token resolves here, so the markup stays shape.

    Type is sized for the phone: the README scales a 440px image to about 358px, so a
    15px label lands at 12px and a 12px sub-label at 9.8px.
    """
    p = f'.pane-{t["name"][0]}'
    return f"""
{p} .t-cap{{font:500 12px 'Figtree',sans-serif;fill:{t['body']}}}
{p} .t-cap-up{{font:500 11px 'Figtree',sans-serif;letter-spacing:.12em;text-transform:uppercase;fill:{t['dim']}}}
{p} .t-name{{font:600 15px 'Figtree',sans-serif;fill:{t['heading']}}}
{p} .t-name-dim{{font:600 15px 'Figtree',sans-serif;fill:{t['body']}}}
{p} .t-sub{{font:400 12px 'Figtree',sans-serif;fill:{t['muted']}}}
{p} .outer{{border:1px solid {t['line']};border-radius:12px;padding:16px;background:{t['panel']}}}
{p} .outer-h{{display:flex;align-items:center;gap:9px}}
{p} .outer-t{{font:600 16px 'Figtree',sans-serif;color:{t['heading']}}}
{p} .outer-s{{font:400 12px 'Figtree',sans-serif;color:{t['muted']};margin:5px 0 13px 35px}}
{p} .stack{{display:flex;flex-direction:column;gap:10px}}
{p} .row3{{display:grid;grid-template-columns:repeat(3,minmax(0,1fr))}}
{p} .g10{{gap:10px}}
{p} .card{{border:1px solid {t['line']};border-radius:10px;padding:11px 12px;background:{t['chip']};
display:flex;align-items:center;gap:14px}}
{p} .card-h{{display:flex;align-items:center;gap:8px;flex:none;width:104px}}
{p} .card-t{{font:600 14px 'Figtree',sans-serif;color:{t['heading']}}}
{p} .chips{{display:flex;gap:7px;flex-wrap:wrap}}
{p} .chip{{font:500 12px 'Figtree',sans-serif;color:{t['mintText']};background:{t['mintSoft']};
border:1px solid {t['mintEdge']};border-radius:6px;padding:4px 9px}}
{p} .branch{{display:flex;flex-direction:column;align-items:flex-start;gap:2px;margin-left:16px}}
{p} .pill{{font:600 13px 'Figtree',sans-serif;color:{t['text']};border:1px solid {t['line']};
border-radius:999px;padding:8px 15px;background:{t['chip']}}}
{p} .pill-mint{{border-color:{t['mintEdge']};background:{t['mintSoft']};color:{t['mintText']};
display:inline-flex;flex-direction:column;align-items:flex-start;gap:1px;border-radius:14px}}
{p} .pill-s{{font:400 11px 'Figtree',sans-serif;color:{t['muted']}}}
{p} .arrow{{color:{t['dim']};font-size:15px}}
{p} .tile{{border:1px solid {t['line']};border-radius:10px;padding:11px 13px;background:{t['chip']};
display:flex;flex-direction:column;gap:3px;justify-content:center}}
{p} .tile-t{{font:600 14px 'Figtree',sans-serif;color:{t['heading']}}}
{p} .tile-s{{font:400 12px/1.4 'Figtree',sans-serif;color:{t['muted']}}}
{p} .tile-c{{align-items:center;text-align:center;padding:11px 6px}}
{p} .tile-in{{background:{t['chip']}}}
{p} .tile-core{{flex-direction:column;align-items:flex-start;gap:3px}}
{p} .tile-dark{{background:{t['grayL']};border-color:{t['grayT']}}}
{p} .tile-dark .tile-t{{color:#F2F2F4}}
{p} .tile-dark .tile-s{{color:#A9A9B2}}
{p} .mono{{font:400 12px/1.45 'Fira Code',monospace;color:{t['body']}}}
{p} .mono-b{{font-weight:500;font-size:13px;color:{t['heading']}}}
{p} .mono-gw{{font-weight:500;font-size:15px;color:{t['mintInk']}}}
{p} .gw{{background:{t['mint']};border-radius:10px;padding:11px 14px;display:flex;
flex-direction:column;gap:2px}}
{p} .gw-s{{font:400 12px 'Figtree',sans-serif;color:rgba(8,9,10,.72)}}
{p} .server{{border:1px solid {t['line']};border-radius:12px;padding:14px;position:relative;
background:{t['panel']}}}
{p} .server-tab{{position:absolute;top:-8px;left:14px;background:{t['ground']};padding:0 6px;
font:500 11px 'Fira Code',monospace;letter-spacing:.04em;color:{t['dim']}}}
{p} .lanes{{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}}
{p} .lane{{display:flex;flex-direction:column;gap:10px}}
{p} .lane-l{{font:500 11px 'Figtree',sans-serif;letter-spacing:.12em;text-transform:uppercase;
color:{t['dim']}}}
{p} .lane-c{{display:flex;flex-direction:column;align-items:center;gap:3px}}
{p} .v-chip{{font:600 13px 'Figtree',sans-serif;color:{t['text']};border:1px solid {t['line']};
border-radius:8px;padding:8px 12px;background:{t['chip']};width:100%;text-align:center}}
{p} .v-cold{{color:{t['muted']};border-style:dashed}}
{p} .v-hot{{background:{t['mint']};border-color:{t['mint']};color:{t['mintInk']}}}
{p} .v-down{{color:{t['dim']};font-size:14px;line-height:1.1}}
{p} .v-dash{{color:{t['rail']}}}
{p} .card-col{{flex-direction:column;align-items:stretch;gap:10px}}
{p} .card-col .card-h{{width:auto}}
{p} .chips-col{{flex-direction:column;gap:6px}}
{p} .outer-s-inline{{margin:0 0 0 2px}}
{p} .branch-row{{flex-direction:row;align-items:center;gap:10px;margin-left:4px;margin-bottom:-10px}}
{p} .pill-row{{flex-direction:row;align-items:baseline;gap:9px;border-radius:999px}}
{p} .gw-row{{flex-direction:row;align-items:baseline;gap:12px}}
{p} .tile-core-row{{flex-direction:row;align-items:baseline;gap:12px}}
{p} .lanes-row{{display:flex;flex-direction:column;gap:18px}}
{p} .lane-row{{flex-direction:row;align-items:center;gap:14px}}
{p} .lane-l-row{{width:104px;flex:none;text-align:right}}
{p} .lane-r{{display:flex;align-items:center;gap:8px}}
{p} .v-arrow{{color:{t['dim']};font-size:14px}}
{p} .v-chip-row{{width:auto}}
"""

# key, title, narrow (fn, w, h), wide (fn, w, h)
SPECS = [
    ('abstraction-layer', 'A different abstraction layer',
     (abstraction_layer, 440, 292), (w_abstraction_layer, 560, 322)),
    ('core-concepts', 'Core concepts',
     (core_concepts, 440, 256), (w_core_concepts, 620, 165)),
    ('environments', 'CI/CD environments',
     (environments, 300, 280), (w_environments, 620, 237)),
    ('architecture', 'Architecture',
     (architecture, 440, 739), (w_architecture, 880, 452)),
    ('vision', 'The vision',
     (vision, 440, 274), (w_vision, 660, 84)),
]

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__)))))
FONTS = '\n'.join(
    f"@font-face{{font-family:'{family}';font-weight:400 800;"
    f"src:url('file://{ROOT}/website/public/fonts/{f}.woff2') format('woff2')}}"
    for family, f in (('Figtree', 'figtree-latin'), ('Fira Code', 'fira-code-latin')))


def main():
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'build')
    os.makedirs(out, exist_ok=True)
    jobs = []
    for key, _title, narrow, wide in SPECS:
        for suffix, (fn, w, h) in (('', narrow), ('-wide', wide)):
            for t in (LIGHT, DARK):
                stem = f'{key}{suffix}-{t["name"]}'
                open(os.path.join(out, stem + '.html'), 'w').write(
                    f'<!doctype html><html><head><meta charset="utf-8"><style>'
                    f'{FONTS}{CSS}{scoped(t)}body{{margin:0;background:{t["ground"]}}}'
                    f'</style></head><body><div class="pane pane-{t["name"][0]}" '
                    f'style="width:{w}px;padding:0">{fn(t)}</div></body></html>')
                jobs.append({'html': stem + '.html', 'png': stem + '.png', 'w': w, 'h': h})
    open(os.path.join(out, 'jobs.json'), 'w').write(json.dumps(jobs, indent=2))
    print(f'{len(jobs)} pages in {out}; now run: node render.js')


if __name__ == '__main__':
    main()
