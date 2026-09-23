# README assets

Everything the repository README embeds. Every raster image ships on a light and a dark
ground, selected with `<picture>` on `prefers-color-scheme`, and is rendered at a multiple
of the width the README displays it at so it stays sharp on a high-density screen. Diagrams
add a second axis — a narrow and a wide layout, chosen on viewport width — so each of them
is four files.

## Logo

`kinotic-logo-light.svg` and `kinotic-logo-dark.svg` are the wordmark from
`website/public/icons/logo.svg`. The site's wordmark draws its text in white for a dark
background; the light variant is the same file with that text at `#08090A`. The `.png`
files beside them are those SVGs rasterized at 680px wide.

## Diagrams

`diagrams/diagrams.py` draws all five diagrams as HTML and inline SVG; `diagrams/render.js`
screenshots them into the `.png` files the README embeds. They are committed as images
rather than ```` ```mermaid ```` code blocks because only GitHub's web UI renders mermaid —
in the mobile apps a mermaid block shows up as raw code.

Each diagram ships in four files: a narrow and a wide layout, each on a light and a dark
ground. Regenerate all twenty after editing the source:

```bash
cd .github/assets/diagrams
python3 diagrams.py   # writes build/*.html
node render.js        # screenshots them at 2x into the PNGs beside it
```

`render.js` finds Chromium through `CHROME`, then `PLAYWRIGHT_BROWSERS_PATH`, then the usual
system locations. `build/` is scratch and is not committed.

Before it screenshots a page, `render.js` compares each `<svg>`'s `getBBox()` against its
`viewBox` and refuses to render if any geometry falls outside. An svg clips to its own
viewport, so a slab drawn past the bottom edge is cropped silently at the full declared
height — the page measures correctly and the PNG is wrong. That is how the abstraction
layer shipped with 12px of its ground slab missing.

### Why the narrow variant renders at 3x and the wide one at 2x

Each is rendered at the density the screen it lands on actually asks for:

```
narrow, on a 390px phone at DPR 3   358 css × 3 = 1074 device px   → 3x of 440 = 1320  ✓
wide, on a retina desktop at DPR 2  880 css × 2 = 1760 device px   → 2x of 880 = 1760  ✓
```

At 2x the narrow variant would supply 880px into a box wanting 1074, and the browser would
upscale its text by 1.22x. The wide variant never lands on a phone, so 2x is already native
there and 3x would be 300KB of pixels nobody sees. `diagrams.py` carries the ratio per job
in `build/jobs.json` and `render.js` reads it.

### Why two layouts

GitHub gives a README about 358px on a 390px phone and scales anything wider down to fit,
and that scale multiplies straight through to the type inside the image. A 12px label in an
860px-wide diagram lands at 5px on a phone, which is texture rather than text.

So the README serves the narrow layout by default and the wide one only above a 1000px
viewport:

```html
<source media="(min-width: 1000px)" srcset="…-wide-light.png" width="620">
<img alt="…" src="…-light.png" width="440">
```

`width` on `<source>` is what sizes the wide variant — GitHub's sanitizer keeps `media`,
`srcset` and `width` on `<source>`, but strips `srcset` from the `<img>`, so the `<img>`
carries the narrow layout and its own width as the fallback every client can render.

### Constraints the source respects

**A 15px floor on labels, 12px on sub-labels.** At the 0.81 scale a 440px-wide diagram takes
on a phone, those land at 12.2px and 9.8px. Anything smaller stops being readable there.
`environments` is 300px wide — under the phone's column — so it never scales at all.

**Type is the repo's own.** `diagrams.py` loads `website/public/fonts/figtree-latin.woff2`
and `fira-code-latin.woff2` by absolute path, so the PNGs carry Figtree and Fira Code
exactly. Package names are set in the mono face; everything else is Figtree.

**Colors come from the brand palette.** Mint `#28FEB4` marks what Kinotic provides, the
neutral ramp what a caller brings, `#2B2A32` the infrastructure underneath — dark in both
themes, because it is not ours. Red appears only on the AI-agent cube, matching
`marks/humans-and-agents.svg`; it never marks a component.

**18px of air on top, 6px underneath.** GitHub leaves 16px between a paragraph and the
image after it but 24px or more before the heading that follows, so a diagram with no
padding of its own sits closer to the text above than the text below. The padding is inside
`.d`, which is `border-box`, so every declared height carries the 24px.

**Isometry only where it means something.** The abstraction layer is a stack, so it is drawn
as isometric slabs; promotion moves forward, so its environments are cubes on a line.
Diagrams that are really lists — core concepts, architecture — use panels with a cube glyph
instead.

## Heading marks and cubes

`marks/*.svg` are the fourteen section marks the README's headings carry; `cubes/*.svg` are the
three cubes — mint beside the wordmark, red where the first divider was, red at the footer.

Both are drawn from the site: the cubes are the geometry and fills of `CtaComponent.vue`, and the
marks are built from that same cube as a primitive, repeated and arranged — three slabs for layers,
four assembled for what you can build, three on a platform for architecture. `why.svg` is the mark
from `website/public/icons/logo.svg`.

### What keeps them legible

**Filled, never stroked.** Every edge of an isometric drawing is a diagonal, so a 1.5px outline
aliases into mush at 22px. The marks use the mint cube's three fills as solid faces —
`#4BF2B0` top, `#17B87C` right, `#0E9E68` left — which is why the site's cubes read at any size.

**Silhouettes differ, not details.** At 22px a reader sees an outline, so marks are distinguished by
count and arrangement rather than internal drawing. Where two would still collide, colour separates
them: humans-and-agents is one mint cube and one red, development is a cube and its ghost.

**One file per mark, no theme variants.** Mint holds its contrast on white and on GitHub's dark
ground, so these need no `<picture>`. Files are drawn at 44px and set to `width="22"` in the README,
which keeps them sharp on a high-density screen.

**SVG by relative path renders in the GitHub mobile app**, confirmed on Android — the heading
marks, the wordmark and the cubes all come through, and the cubes' CSS animation runs there
too. That is why the marks stay SVG while the diagrams are PNG: the diagrams are raster for
the type they carry, not because SVG is unsafe. Mermaid is the thing the app will not render,
and it shows up there as raw code.

### The beat marks

`marks/beat.svg` and `marks/beat-mint.svg` are one cube each, and are the only marks that
appear in body copy rather than on a heading. The abstraction layer section is three things
other people built followed by the turn, so the three carry the neutral cube and the turn
carries the mint one — the colour is what says which line is the answer.

They sit on four separate paragraphs rather than on `<br>`-separated lines. A wrapped line
returns to the left margin, so with `<br>` the three beats run together into one block at
phone width and the markers stop delimiting anything; paragraph gaps hold at any width.
A markdown list cannot carry them at all: GitHub strips `list-style`, so an image inside a
list item renders the disc as well as the mark.

### Motion

The cubes carry the home page's float (7s) and tumble (9s) as CSS inside the file, which animates
inside an `<img>` because it is style rather than script. Travel is expressed as a percentage of the
cube rather than in pixels, at the proportion the site moves its own — roughly a quarter of the cube
for a tumble, a fifth for a float — and each viewBox carries headroom above the cube so the top of
that travel is not clipped. Each one honours
`prefers-reduced-motion: reduce` and holds still for a reader who asked for that. Nothing else in
the README moves — the marks are deliberately static.

Nothing here reaches the size of the home page's largest cube, which renders about 148px. The page
stays the loud one.
