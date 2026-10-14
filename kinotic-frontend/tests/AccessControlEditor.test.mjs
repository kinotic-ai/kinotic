import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
import {pathToFileURL} from 'node:url'
import {Window} from 'happy-dom'
import {fileURLToPath} from 'node:url'

const portal = fileURLToPath(new URL('../apps/portal/', import.meta.url))
const require = createRequire(path.join(portal, 'package.json'))
const window = new Window({url: 'http://localhost/'})
for (const name of ['window', 'document', 'navigator', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'MutationObserver', 'getComputedStyle']) {
    Object.defineProperty(globalThis, name, {value: name === 'window' ? window : window[name], configurable: true})
}
globalThis.requestAnimationFrame = window.requestAnimationFrame.bind(window)
globalThis.cancelAnimationFrame = window.cancelAnimationFrame.bind(window)
globalThis.matchMedia = window.matchMedia.bind(window)
const {parse, compileScript} = require('vue/compiler-sfc')
const ts = require('typescript')
const file = path.resolve(portal, '../../packages/common/src/components/AccessControlEditor.vue')
const {descriptor} = parse(fs.readFileSync(file, 'utf8'), {filename: file})
const script = compileScript(descriptor, {id: 'access-control-smoke', inlineTemplate: true})
const output = ts.transpileModule(script.content, {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023}}).outputText
const modulePath = path.join(portal, 'node_modules/.access-control-smoke.mjs')
fs.writeFileSync(modulePath, output)
const {createApp, nextTick, h, ref} = await import(pathToFileURL(require.resolve('vue')))
const {default: PrimeVue} = await import(pathToFileURL(require.resolve('primevue/config')))
const {default: Editor} = await import(pathToFileURL(modulePath))
const scope = {kind: 'ORGANIZATION', organizationId: 'acme', applicationId: null, tenantId: null}
let policy = {id: 'policy', scope, revision: 3, administrators: ['alice'], roles: [], groups: [], assignments: []}
const permissions = [{permission: 'projects.update', resourceType: 'project', label: 'Update projects', tenantDelegable: false}]
const calls = []
let rejectSave = false
const view = () => ({policy: structuredClone(policy), permissions, pending: false, modelRevision: 3})
const service = {
    async load(requested) { assert.deepEqual(requested, scope); return view() },
    async findIdentities(requested) { return {content: requested.organizationId === 'acme' ? [{id: 'alice', label: 'Alice'}] : [{id: 'bob', label: 'Bob'}], cursor: null} },
    async save(value, revision) {
        calls.push(['save', JSON.parse(JSON.stringify(value)), revision])
        if (rejectSave) throw new Error('Policy revision conflict; reload before saving')
        policy = {...JSON.parse(JSON.stringify(value)), revision: revision + 1}
        return view()
    },
    async republish(requested, revision) { calls.push(['republish', requested, revision]); return view() }
}
const host = document.createElement('div')
document.body.append(host)
const currentScope = ref(scope)
const app = createApp({setup() { return () => h(Editor, {scope: currentScope.value, service}) }})
app.use(PrimeVue, {unstyled: true})
app.mount(host)
async function settle() { await new Promise(resolve => setTimeout(resolve, 30)); await nextTick() }
async function click(label) {
    const button = [...host.querySelectorAll('button')].find(node => node.textContent.trim() === label)
    assert.ok(button, `Missing button: ${label}`)
    button.click(); await settle()
}
try {
    await settle()
    assert.match(host.textContent, /Revision 3/)
    await click('Add role')
    const name = host.querySelector('input[aria-label="Role name"]')
    name.value = 'Project editor'; name.dispatchEvent(new window.Event('input', {bubbles: true}))
    await click('Add group')
    await click('Add assignment')
    await click('Save access')
    assert.equal(calls[0][2], 3)
    assert.equal(calls[0][1].roles[0].name, 'Project editor')
    assert.equal(calls[0][1].groups.length, 1)
    assert.equal(calls[0][1].assignments.length, 1)
    assert.match(host.textContent, /Access changes published/)
    assert.match(host.textContent, /Revision 4/)
    await click('Republish permissions')
    assert.equal(calls[1][0], 'republish')
    assert.equal(calls[1][2], 4)
    await click('Remove role')
    await click('Remove group')
    rejectSave = true
    await click('Save access')
    assert.match(host.textContent, /Policy revision conflict/)
    assert.equal(calls[2][1].roles.length, 0)
    assert.equal(calls[2][1].groups.length, 0)
    let resolveOld
    service.load = async requested => requested.organizationId === 'acme'
        ? new Promise(resolve => { resolveOld = resolve })
        : {...view(), policy: {...policy, scope: requested, revision: 11, administrators: ['bob']}}
    await click('Reload')
    assert.ok(resolveOld)
    currentScope.value = {...scope, organizationId: 'other'}
    await settle()
    assert.match(host.textContent, /Revision 11/)
    resolveOld({...view(), policy: {...policy, revision: 99}})
    await settle()
    assert.match(host.textContent, /Revision 11/)
    assert.doesNotMatch(host.textContent, /Revision 99/)
    assert.doesNotMatch(host.textContent, /Policy revision conflict/)
    console.log('AccessControlEditor DOM smoke passed: scoped loading, edit/save, revisions, republish, failure feedback and stale scope responses')
} finally {
    app.unmount(); window.happyDOM.abort(); fs.unlinkSync(modulePath)
}
