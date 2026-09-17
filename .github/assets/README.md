# README assets

Everything the repository README embeds. Each image ships as a light and a dark variant,
selected with `<picture>` on `prefers-color-scheme`, and is rendered at 2x with the README
setting `<img width>` to half the pixel width so it stays sharp on a high-density screen.

## Logo

`kinotic-logo-light.svg` and `kinotic-logo-dark.svg` are the wordmark from
`website/public/icons/logo.svg`. The site's wordmark draws its text in white for a dark
background; the light variant is the same file with that text at `#08090A`. The `.png`
files beside them are those SVGs rasterized at 680px wide.

## Diagrams

`diagrams/*.mmd` are mermaid sources; the `.png` files beside them are what the README
embeds. They are committed as images rather than ```` ```mermaid ```` code blocks because
only GitHub's web UI renders mermaid — in the mobile apps a mermaid block shows up as raw
code.

Regenerate both themes after editing a source:

```bash
cd .github/assets/diagrams
npx -y @mermaid-js/mermaid-cli@11 -i architecture.mmd -o architecture-light.png -s 2 -b transparent -c mermaid-light.json
npx -y @mermaid-js/mermaid-cli@11 -i architecture.mmd -o architecture-dark.png  -s 2 -b transparent -c mermaid-dark.json
```

Then set the README's `<img width>` for that diagram to half the new pixel width.

### Constraints the sources respect

**`htmlLabels` is off** in `mermaid-light.json` and `mermaid-dark.json`. With HTML labels
mermaid emits `<foreignObject>`, which browsers refuse to render inside an `<img>`, so every
label comes out blank. Bold text therefore uses mermaid's markdown strings — backticks
inside the quotes, `**like this**` — instead of `<b>`.

**Nothing wider than ~850px.** A wider diagram is scaled down to illegibility on a phone.
Width is driven by the widest rank, so one long single-line label costs more than an extra
line does.

**Colors come from the brand palette**, declared per diagram with `classDef`: mint `#28FEB4`
for Kinotic's own layers, `#EDEDEF` for what a caller brings, `#2B2A32` for the
infrastructure underneath. Each pairs a fill with an explicit text color, so contrast holds
whichever theme the reader is in.

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
