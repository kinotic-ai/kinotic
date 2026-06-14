import {afterEach, describe, expect, it} from 'vitest'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {assertPathWithin, NodeSpawnRenderer} from '../src/node/index'

const tmpDirs: string[] = []

function spawnDirWith(files: Record<string, string>): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-node-src-'))
  tmpDirs.push(dir)
  for (const [rel, content] of Object.entries(files)) {
    const p = path.join(dir, rel)
    fs.mkdirSync(path.dirname(p), {recursive: true})
    fs.writeFileSync(p, content)
  }
  return dir
}

function freshDestination(): string {
  const base = fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-node-dst-'))
  tmpDirs.push(base)
  return path.join(base, 'out')
}

afterEach(() => {
  for (const dir of tmpDirs.splice(0)) {
    fs.rmSync(dir, {recursive: true, force: true})
  }
})

describe('NodeSpawnRenderer', () => {

  it('renders a spawn from disk to disk, excluding spawn.json', async () => {
    const spawnDir = spawnDirWith({
      'spawn.json': JSON.stringify({globals: {flavor: 'x'}}),
      'package.json.liquid': '{"name": "{{ projectName }}", "flavor": "{{ flavor }}"}',
      'src/{{ projectName | upperFirst }}.ts.liquid': 'export class {{ projectName | upperFirst }} {}',
      '.gitignore': 'node_modules\n',
    })
    const destination = freshDestination()

    const context = await new NodeSpawnRenderer().render(spawnDir, destination, {context: {projectName: 'acme'}})

    expect(JSON.parse(fs.readFileSync(path.join(destination, 'package.json'), 'utf8')))
      .toEqual({name: 'acme', flavor: 'x'})
    expect(fs.readFileSync(path.join(destination, 'src/Acme.ts'), 'utf8')).toBe('export class Acme {}')
    expect(fs.readFileSync(path.join(destination, '.gitignore'), 'utf8')).toBe('node_modules\n')
    expect(fs.existsSync(path.join(destination, 'spawn.json'))).toBe(false)
    expect(context.flavor).toBe('x')
  })

  it('throws when the destination already exists', async () => {
    const spawnDir = spawnDirWith({'a.txt': 'x'})
    const destination = fs.mkdtempSync(path.join(os.tmpdir(), 'spawn-node-dst-'))
    tmpDirs.push(destination)

    await expect(new NodeSpawnRenderer().render(spawnDir, destination, {context: {}}))
      .rejects.toThrow(/already exists/)
  })

  it('refuses to write outside the destination when a path escapes the root', async () => {
    const spawnDir = spawnDirWith({'{{ out }}.txt': 'pwned'})
    const destination = freshDestination()

    await expect(new NodeSpawnRenderer().render(spawnDir, destination, {context: {out: '../../../escape'}}))
      .rejects.toThrow(/escapes/)
    expect(fs.existsSync(path.resolve(destination, '../../../escape.txt'))).toBe(false)
  })

  it('refuses an inheritance ref that escapes the root', async () => {
    const spawnDir = spawnDirWith({'spawn.json': JSON.stringify({inherits: '../../../../etc'})})
    const destination = freshDestination()

    await expect(new NodeSpawnRenderer().render(spawnDir, destination, {context: {}}))
      .rejects.toThrow(/escapes/)
  })

  it('assertPathWithin allows internal ../ but rejects escapes and absolutes', () => {
    const root = path.resolve('/tmp/root')
    expect(assertPathWithin(root, 'a/b.txt')).toBe(path.join(root, 'a/b.txt'))
    expect(assertPathWithin(root, 'a/../b.txt')).toBe(path.join(root, 'b.txt'))
    expect(() => assertPathWithin(root, '../escape')).toThrow(/escapes/)
    expect(() => assertPathWithin(root, '/etc/passwd')).toThrow(/escapes/)
  })

})
