# design-sync notes

- kinotic-frontend is Vue 3 + PrimeVue. The design-sync converter only packages React design systems,
  so the Claude Design project "Kinotic Design System" keeps its hand-written React components and
  only its colour tokens track this repo.
- `node .design-sync/generate-tokens.mjs` writes `out/tokens/kinotic-theme.css` from `KinoticPreset`
  (colour tokens only, `--p-` prefix stripped so the names override `tokens/fig-tokens.css`).
  Dimension tokens are skipped: the project's components treat them as unitless px floats.
- Upload `out/tokens/kinotic-theme.css` and `out/styles.css` (imports kinotic-theme.css after
  fig-tokens.css, before brand.css), then write `_ds_needs_recompile`.
