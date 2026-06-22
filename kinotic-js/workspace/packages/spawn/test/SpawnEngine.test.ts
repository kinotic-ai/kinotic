import {describe, expect, it, vi} from 'bun:test'
import {SpawnEngine} from '../src/index'
import type {PropertyResolver, SpawnTree} from '../src/index'

describe('Kinotic JS', () => {
  describe('packages/spawn', () => {
    describe('SpawnEngine', () => {

      it('renders liquid content and strips the .liquid suffix', async () => {
        const spawn: SpawnTree = {
          'package.json.liquid': '{"name": "{{projectName}}"}',
        }

        const result = await new SpawnEngine().renderSpawn(spawn, {context: {projectName: 'my-app'}})

        expect(result.files).toEqual({'package.json': '{"name": "my-app"}'})
      })

      it('renders liquid expressions in paths', async () => {
        const spawn: SpawnTree = {
          'src/{{ package | packageToPath }}/{{ name | upperFirst }}.ts.liquid': 'export class {{ name | upperFirst }} {}',
        }

        const result = await new SpawnEngine().renderSpawn(spawn, {context: {package: 'org.kinotic.demo', name: 'widget'}})

        expect(result.files).toEqual({'src/org/kinotic/demo/Widget.ts': 'export class Widget {}'})
        // sources maps the rendered destination back to its template path
        expect(result.sources).toEqual({
          'src/org/kinotic/demo/Widget.ts': 'src/{{ package | packageToPath }}/{{ name | upperFirst }}.ts.liquid',
        })
      })

      it('applies the camelCase and encodePackage filters', async () => {
        const spawn: SpawnTree = {
          'out.txt.liquid': '{{ "my-cool name" | camelCase }} {{ "my-pkg.2.demo" | encodePackage }}',
        }

        const result = await new SpawnEngine().renderSpawn(spawn)

        expect(result.files['out.txt']).toBe('myCoolName my_pkg._2.demo')
      })

      it('copies non-template files verbatim, including binary content', async () => {
        const binary = new Uint8Array([0, 159, 146, 150])
        const spawn: SpawnTree = {
          'tsconfig.json': '{"strict": true}',
          'logo.png': binary,
        }

        const result = await new SpawnEngine().renderSpawn(spawn)

        expect(result.files['tsconfig.json']).toBe('{"strict": true}')
        expect(result.files['logo.png']).toBe(binary)
      })

      it('excludes spawn.json and .DS_Store from the rendered files', async () => {
        const spawn: SpawnTree = {
          'spawn.json': '{}',
          '.DS_Store': new Uint8Array([1]),
          'nested/.DS_Store': new Uint8Array([1]),
          'keep.txt': 'kept',
        }

        const result = await new SpawnEngine().renderSpawn(spawn)

        expect(Object.keys(result.files)).toEqual(['keep.txt'])
      })

      it('merges spawn.json globals under the provided context', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({globals: {apiVersion: '^1.0.9', projectName: 'from-globals'}}),
          'out.txt.liquid': '{{ projectName }} {{ apiVersion }}',
        }

        const result = await new SpawnEngine().renderSpawn(spawn, {context: {projectName: 'from-context'}})

        expect(result.files['out.txt']).toBe('from-context ^1.0.9')
        expect(result.context).toEqual({projectName: 'from-context', apiVersion: '^1.0.9'})
      })

      it('fails on a propertySchema entry missing from the context when no resolver is given', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({propertySchema: {projectName: {type: 'string'}}}),
        }

        await expect(new SpawnEngine().renderSpawn(spawn))
          .rejects.toThrow("No value provided for required property 'projectName'")
      })

      it('obtains missing properties from the resolver with rendered message and default', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({
            globals: {projectName: 'demo'},
            propertySchema: {
              libraryName: {
                type: 'string',
                description: 'Library name for {{ projectName }}',
                default: '{{ projectName }}-lib',
              },
            },
          }),
          'out.txt.liquid': '{{ libraryName }}',
        }
        const resolver: PropertyResolver = {
          resolve: vi.fn(async (key, schema, message, defaultValue) => defaultValue),
        }

        const result = await new SpawnEngine().renderSpawn(spawn, {propertyResolver: resolver})

        expect(resolver.resolve).toHaveBeenCalledWith(
          'libraryName',
          expect.objectContaining({type: 'string'}),
          'Library name for demo',
          'demo-lib',
        )
        expect(result.files['out.txt']).toBe('demo-lib')
      })

      it('does not invoke the resolver for properties already in the context', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({propertySchema: {projectName: {type: 'string'}}}),
          'out.txt.liquid': '{{ projectName }}',
        }
        const resolver: PropertyResolver = {resolve: vi.fn()}

        const result = await new SpawnEngine().renderSpawn(spawn, {
          context: {projectName: 'provided'},
          propertyResolver: resolver,
        })

        expect(resolver.resolve).not.toHaveBeenCalled()
        expect(result.files['out.txt']).toBe('provided')
      })

      it('follows inheritance: derived files and globals override the base', async () => {
        const base: SpawnTree = {
          'spawn.json': JSON.stringify({globals: {flavor: 'base', baseOnly: 'yes'}}),
          'shared.txt.liquid': 'base {{ flavor }}',
          'base.txt': 'base file',
        }
        const derived: SpawnTree = {
          'spawn.json': JSON.stringify({inherits: '../base', globals: {flavor: 'derived'}}),
          'shared.txt.liquid': 'derived {{ flavor }} {{ baseOnly }}',
        }
        const loadInherited = vi.fn(async (_ref: string) => base)

        const result = await new SpawnEngine().renderSpawn(derived, {loadInherited})

        expect(loadInherited).toHaveBeenCalledWith('../base')
        expect(result.files).toEqual({
          'shared.txt': 'derived derived yes',
          'base.txt': 'base file',
        })
      })

      it('fails when a spawn inherits but no loadInherited callback is provided', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({inherits: '../base'}),
        }

        await expect(new SpawnEngine().renderSpawn(spawn))
          .rejects.toThrow("Spawn inherits '../base' but no loadInherited callback was provided")
      })

      it('renders the bundled project spawn shape end to end', async () => {
        const spawn: SpawnTree = {
          'spawn.json': JSON.stringify({
            globals: {kinoticApiVersion: '^1.0.9'},
            propertySchema: {projectName: {type: 'string', description: 'The name of the project you want to create'}},
          }),
          'package.json.liquid': '{"name": "{{projectName}}", "catalog": {"@kinotic-ai/core": "{{ kinoticApiVersion }}"}}',
          'packages/domain/package.json.liquid': '{"name": "{{ projectName }}/domain"}',
          'packages/domain/model/.gitkeep': '',
          '.gitignore': 'node_modules\n',
        }

        const result = await new SpawnEngine().renderSpawn(spawn, {context: {projectName: 'acme'}})

        expect(result.files).toEqual({
          'package.json': '{"name": "acme", "catalog": {"@kinotic-ai/core": "^1.0.9"}}',
          'packages/domain/package.json': '{"name": "acme/domain"}',
          'packages/domain/model/.gitkeep': '',
          '.gitignore': 'node_modules\n',
        })
      })

      it('throws on an undeclared variable instead of rendering empty (strictVariables)', async () => {
        await expect(new SpawnEngine().renderSpawn({'a.txt.liquid': '{{ missing }}'}, {context: {}}))
          .rejects.toThrow(/undefined variable: missing/)
      })

      it('throws on an undeclared variable in a path', async () => {
        await expect(new SpawnEngine().renderSpawn({'{{ missing }}/a.txt': 'hi'}, {context: {}}))
          .rejects.toThrow(/undefined variable: missing/)
      })

      it('throws on an undeclared variable even inside a conditional or with a default filter', async () => {
        // strictVariables looks the variable up before the filter/branch runs, so neither
        // {% if %} nor `| default:` rescues an undeclared variable — declare optionals as
        // globals with a real value instead.
        await expect(new SpawnEngine().renderSpawn({'a.txt.liquid': '{% if missing %}y{% endif %}'}, {context: {}}))
          .rejects.toThrow(/undefined variable: missing/)
        await expect(new SpawnEngine().renderSpawn({'a.txt.liquid': '{{ missing | default: "x" }}'}, {context: {}}))
          .rejects.toThrow(/undefined variable: missing/)
      })

    })
  })
})
