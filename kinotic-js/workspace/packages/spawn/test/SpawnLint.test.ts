import {describe, expect, it, vi} from 'bun:test'
import {SpawnEngine} from '../src/index'
import type {SpawnTree} from '../src/index'

describe('Kinotic JS', () => {
  describe('packages/spawn', () => {
    describe('SpawnEngine.lint', () => {

      it('reports a variable used but not declared, with the files it appears in', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({
            globals: {kinoticApiVersion: '^1.0.9'},
            propertySchema: {projectName: {type: 'string'}, organization: {type: 'string'}},
          }),
          'package.json.liquid': '{"name": "{{ projectName }}", "core": "{{ kinoticApiVersion }}"}',
          'src/{{ application | upperFirst }}.ts.liquid': '// {{ projectName }} for {{ organization }}',
        }

        const result = await new SpawnEngine().lint(spawn)

        // application is used only in the path template and is declared nowhere
        expect(result.undeclared).toEqual([
          {name: 'application', files: ['src/{{ application | upperFirst }}.ts.liquid']},
        ])
      })

      it('is clean when every variable is declared in propertySchema or globals', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({
            globals: {kinoticApiVersion: '^1.0.9'},
            propertySchema: {projectName: {type: 'string'}},
          }),
          'package.json.liquid': '{"name": "{{ projectName }}", "core": "{{ kinoticApiVersion }}"}',
        }

        const result = await new SpawnEngine().lint(spawn)

        expect(result.undeclared).toEqual([])
      })

      it('does not flag registered filters or loop/assign locals as variables', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({propertySchema: {items: {type: 'string'}}}),
          'out.txt.liquid': '{% for entry in items %}{{ entry | upperFirst }}{% endfor %}{% assign x = 1 %}{{ x }}',
        }

        const result = await new SpawnEngine().lint(spawn)

        // entry (loop local), x (assign local), and upperFirst (filter) are not undeclared; items is declared
        expect(result.undeclared).toEqual([])
      })

      it('treats a var declared in an inherited spawn as declared', async () => {
        const base: SpawnTree = {
          'spawn.json': JSON.stringify({propertySchema: {projectName: {type: 'string'}}}),
        }
        const derived: SpawnTree = {
          'spawn.json': JSON.stringify({inherits: '../base'}),
          'out.txt.liquid': '{{ projectName }}',
        }
        const loadInherited = vi.fn(async () => base)

        const result = await new SpawnEngine().lint(derived, {loadInherited})

        expect(result.undeclared).toEqual([])
      })

      it('collects every file a variable appears in, sorted', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({propertySchema: {}}),
          'b.txt.liquid': '{{ missing }}',
          'a.txt.liquid': '{{ missing }}',
        }

        const result = await new SpawnEngine().lint(spawn)

        expect(result.undeclared).toEqual([{name: 'missing', files: ['a.txt.liquid', 'b.txt.liquid']}])
      })

    })
  })
})
