#!/usr/bin/env tsx
// eslint-disable-next-line node/shebang
(async () => {
  const oclif = await import('@oclif/core')
  await oclif.execute({type: 'esm', development: true, dir: import.meta.url})
})()
