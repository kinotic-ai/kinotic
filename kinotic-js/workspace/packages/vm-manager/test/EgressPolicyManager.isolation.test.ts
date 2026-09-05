import { afterAll, describe, expect, it } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { EgressPolicyManager } from '@/internal/api/network/EgressPolicyManager'

// Rules go into the real DOCKER-USER chain, because what is being tested is which rules a
// workload's own operations touch — a question only the kernel can answer. Skipped where the
// chain cannot be read or written, which is any machine without Docker or without root.
const chainIsWritable = (() => {
    if (spawnSync('iptables', ['-S', 'DOCKER-USER'], { encoding: 'utf-8' }).status !== 0) {
        return false
    }
    const probe = ['DOCKER-USER', '-s', '203.0.113.1', '-m', 'comment', '--comment', 'kinotic-probe', '-j', 'RETURN']
    const added = spawnSync('iptables', ['-I', ...probe], { encoding: 'utf-8' }).status === 0
    if (added) {
        spawnSync('iptables', ['-D', ...probe], { encoding: 'utf-8' })
    }
    return added
})()

/** How many rules in the chain belong to this workload, read back from the kernel. */
function ruleCount(workloadId: string): number {
    const listed = spawnSync('iptables', ['-S', 'DOCKER-USER'], { encoding: 'utf-8' }).stdout ?? ''
    return listed.split('\n').filter(line => line.includes(`--comment "kinotic:${workloadId}"`)).length
}

// Ids that share a prefix with one another, which is how a workload's rules end up matching a
// sibling's: wl-1 is a prefix of wl-10. Real ids are UUIDs, but nothing requires them to be.
const IDS = ['iso-1', 'iso-10', 'iso-11', 'iso-2', 'iso-20',
             'iso-3', 'iso-30', 'iso-300', 'iso-4', 'iso-40']

describe.skipIf(!chainIsWritable)('EgressPolicyManager keeps workloads out of each other rules', () => {

    const egress = new EgressPolicyManager('192.0.2.53')
    // Distinct address per workload, as Docker would assign
    const addressOf = (index: number) => `198.51.100.${index + 1}`

    afterAll(() => {
        for (const id of IDS) {
            egress.release(id)
        }
    })

    function applyAll(): void {
        IDS.forEach((id, index) => egress.apply(id, addressOf(index), ['203.0.113.0/24']))
    }

    it('gives every workload its own rules', () => {
        applyAll()

        for (const id of IDS) {
            // One for the resolver, one for the allowed destination
            expect(ruleCount(id)).toBe(2)
        }
    })

    it('releases only the workload asked for, not the ones whose id it prefixes', () => {
        applyAll()

        egress.release('iso-1')

        expect(ruleCount('iso-1')).toBe(0)
        // iso-1 is a prefix of both of these
        expect(ruleCount('iso-10')).toBe(2)
        expect(ruleCount('iso-11')).toBe(2)
        for (const id of IDS.filter(other => other !== 'iso-1')) {
            expect(ruleCount(id)).toBe(2)
        }
    })

    it('re-applying a workload replaces its own rules and leaves the rest alone', () => {
        applyAll()

        egress.apply('iso-3', addressOf(5), ['203.0.113.0/24', '203.0.113.128/25'])

        expect(ruleCount('iso-3')).toBe(3)
        expect(ruleCount('iso-30')).toBe(2)
        expect(ruleCount('iso-300')).toBe(2)
    })

    it('reconcile drops exactly the workloads that are gone', () => {
        applyAll()
        const surviving = new Set(['iso-1', 'iso-2', 'iso-3', 'iso-4', 'iso-40'])

        egress.reconcile(surviving)

        for (const id of IDS) {
            expect(ruleCount(id)).toBe(surviving.has(id) ? 2 : 0)
        }
    })

    it('leaves nothing behind once every workload is released', () => {
        applyAll()

        for (const id of IDS) {
            egress.release(id)
        }

        const listed = spawnSync('iptables', ['-S', 'DOCKER-USER'], { encoding: 'utf-8' }).stdout ?? ''
        expect(listed).not.toContain('--comment "kinotic:iso-')
    })

})
