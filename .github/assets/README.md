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
