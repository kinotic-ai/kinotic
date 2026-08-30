import { SimpleBox, getJsBoxlite } from '@boxlite-ai/boxlite'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const HOME = process.env.BOXLITE_HOME!
const IMG = 'alpine:3.22@sha256:14358309a308569c32bdc37e2e0e9694be33a9d99e68afb0f5ff33cc1f695dce'
const runtime = getJsBoxlite().withDefaultConfig()
const name = 'exitprobe2-' + Date.now().toString(36)

function exitJson(vmId: string): string {
    const dir = join(HOME, 'boxes', vmId, 'shared', 'containers')
    try {
        const ids = readdirSync(dir)
        return `${ids.length} container(s): ` + ids.map(c => {
            try { return readFileSync(join(dir, c, 'exit.json'), 'utf-8') } catch { return '<no exit.json>' }
        }).join(' ')
    } catch (e) { return `<unreadable: ${(e as Error).message}>` }
}

const vmId = await new SimpleBox({
    image: IMG, name, runtime, detach: true, autoRemove: false,
    entrypoint: ['sh', '-c', 'sleep 3; exit 42'],
    network: { outbound: { mode: 'enabled', allowNet: ['192.0.2.1'] } },
}).getId()
await (await runtime.get(name))!.start()
for (let i = 0; i < 60; i++) {
    if (!(await runtime.getInfo(name))?.state.running) break
    await new Promise(r => setTimeout(r, 500))
}
console.log(`exited. status=${(await runtime.getInfo(name))?.state.status}  vmId=${vmId}`)
console.log(`exit.json after exit   : ${exitJson(vmId)}`)

console.log('\n-- runtime.get() on an exited box --')
let handle: any = null
try { handle = await runtime.get(name); console.log(`   get() -> ${handle ? 'handle' : 'null'}`) }
catch (e) { console.log(`   get() THREW: ${(e as Error).message}`) }

console.log('-- handle.stop() on an exited box --')
try { await handle.stop(); console.log('   stop() resolved') }
catch (e) { console.log(`   stop() THREW: ${(e as Error).message}`) }

console.log(`exit.json after stop() : ${exitJson(vmId)}`)
console.log(`status after stop()    : ${(await runtime.getInfo(name))?.state.status}`)
await runtime.remove(name, true)
console.log('removed')
