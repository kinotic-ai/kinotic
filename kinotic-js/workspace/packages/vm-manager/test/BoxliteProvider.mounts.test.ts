import { afterEach, beforeEach, describe, expect, it } from 'bun:test'
import { accessSync, constants, existsSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { Workload, WorkloadStatus } from '@kinotic-ai/management-api'
import { BoxliteProvider } from '@/internal/api/providers/BoxliteProvider'

// Constructing the provider instantiates the boxlite runtime, which aborts the whole process
// on a host without virtualization, so the gate is checked without touching boxlite. It must
// check rw access rather than existence: GitHub Actions runners have /dev/kvm but deny the
// runner user permission to open it.
function kvmUsable(): boolean {
    try {
        accessSync('/dev/kvm', constants.R_OK | constants.W_OK)
        return true
    } catch {
        return false
    }
}
const itBoxlite = process.platform === 'darwin' || kvmUsable() ? it : it.skip

// A bound host interface is refused while the box options are built, which ends the start
// once the mounts have been prepared and before any VM boots
const NO_BOOT_PORT = { guestPort: 8080, hostIp: '127.0.0.1' }

describe('BoxliteProvider volume mount preparation', () => {

    let base: string
    let dataDir: string
    let provider: BoxliteProvider

    beforeEach(() => {
        base = mkdtempSync(join(tmpdir(), 'vm-manager-mounts-'))
        dataDir = join(base, 'data')
        provider = new BoxliteProvider(join(base, 'boxlite'), join(base, 'logs'), join(base, 'state'), dataDir)
    })

    afterEach(() => {
        rmSync(base, { recursive: true, force: true })
    })

    function workload(hostPath: string): Workload {
        const w = new Workload('checkout', 'alpine:3.22')
        w.id = 'wl-1'
        w.volumeMounts = [{ hostPath, guestPath: '/workspace', readOnly: false }]
        w.portMappings = [NO_BOOT_PORT]
        return w
    }

    itBoxlite('rejects a mount outside the workload data directory', async () => {
        const w = workload('/etc')

        await expect(provider.start(w)).rejects.toThrow(/inside the workload data directory/)
        expect(w.status).toBe(WorkloadStatus.FAILED)
    })

    // A boxlite node is not provisioned with project quotas — on macOS it cannot have them at
    // all — so a cap it cannot enforce is a capability the node lacks, not a reason to refuse
    // the workload; it says so once at startup instead
    itBoxlite('starts a workload whose cap this node cannot enforce', async () => {
        const w = workload(join(dataDir, 'projects', 'p1'))
        w.volumeMounts[0]!.sizeLimitMb = 4096

        await expect(provider.start(w)).rejects.toThrow(/host interface/)
    })

    // The checkout directory of a project's first deployment: boxlite refuses a box whose
    // volume host path is missing, so the provider has to create it
    itBoxlite('creates a missing writable mount directory before booting the VM', async () => {
        const hostPath = join(dataDir, 'projects', 'p1')

        await expect(provider.start(workload(hostPath))).rejects.toThrow()
        expect(existsSync(hostPath)).toBe(true)
    })
})
