import { afterAll, describe, expect, it } from 'bun:test'
import { execSync, spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { EgressPolicyManager } from '@/internal/api/network/EgressPolicyManager'

// What a workload can actually reach, rather than how many rules were written for it. Counting
// rules cannot tell one workload's grant from another's, and cannot see a workload inheriting
// the rules of a stopped one whose address it was given.
//
// Needs real containers on a node that denies workload egress by default — anywhere else the
// question has no answer, because nothing is being withheld.
const RUNTIME = process.env.KINOTIC_TEST_RUNTIME ?? 'kata-clh'
const canRun = (() => {
    if (!existsSync('/etc/kinotic/egress-default-deny')) {
        return false
    }
    if (spawnSync('iptables', ['-S', 'DOCKER-USER'], { encoding: 'utf-8' }).status !== 0) {
        return false
    }
    return spawnSync('docker', ['info', '-f', '{{.ServerVersion}}'], { encoding: 'utf-8' }).status === 0
})()

const RUN = Date.now().toString(36)
const sh = (command: string): string => {
    try {
        return execSync(command, { shell: '/bin/bash', encoding: 'utf-8' }).toString().trim()
    } catch {
        return ''
    }
}

// Destinations off the workload bridge, so what is measured is the rules rather than the
// daemon's own inter-container block. Anycast resolvers, reached on TCP 53.
const ALPHA = '1.1.1.1'
const BETA = '8.8.8.8'
const GAMMA = '9.9.9.9'
const METADATA = '169.254.169.254'
const DNS_PORT = 53

/** Whether the workload can open a connection to the address. */
function reaches(id: string, address: string, port = DNS_PORT): boolean {
    return sh(`docker exec ${id} sh -c 'nc -w 4 -z ${address} ${port} && echo yes'`) === 'yes'
}

function waitUntilUp(id: string): void {
    // Kata boots a kernel and guest image, so a container is not usable the moment it is created
    for (let attempt = 0; attempt < 40 && !sh(`docker exec ${id} true && echo up`); attempt++) {
        execSync('sleep 1')
    }
}

function addressOf(id: string): string {
    return sh(`docker inspect -f '{{.NetworkSettings.Networks.bridge.IPAddress}}' ${id}`)
}

/** Creates the containers, waits for all of them, then applies each policy. */
function launchAll(egress: EgressPolicyManager,
                   tracker: string[],
                   specs: Array<{ name: string, allowed: string[] }>): string[] {
    const ids = specs.map(spec => `reach-${RUN}-${spec.name}`)
    // Started before any is awaited, so ten guests boot concurrently rather than in series
    ids.forEach(id => {
        sh(`docker rm -f ${id}`)
        sh(`docker run -d --name ${id} --runtime ${RUNTIME} alpine:latest sleep 900`)
        tracker.push(id)
    })
    ids.forEach(waitUntilUp)
    ids.forEach((id, index) => egress.apply(id, addressOf(id), specs[index]!.allowed))
    return ids
}

/** Where each kind of rule sits, so the order the kernel evaluates them can be asserted. */
function chainOrder(): { metadataDrops: number[], workloadAccepts: number[], defaultDeny: number } {
    const rules = sh('iptables -S DOCKER-USER').split('\n')
    const metadataDrops: number[] = []
    const workloadAccepts: number[] = []
    let defaultDeny = -1
    rules.forEach((rule, index) => {
        if (rule.includes(METADATA) && rule.includes('DROP')) {
            metadataDrops.push(index)
        } else if (rule.includes('--comment "kinotic:') && rule.includes('ACCEPT')) {
            workloadAccepts.push(index)
        } else if (rule.includes('-s ') && !rule.includes('-d ') && rule.includes('DROP')) {
            defaultDeny = index
        }
    })
    return { metadataDrops, workloadAccepts, defaultDeny }
}

describe.skipIf(!canRun)('a workload reaches only what its own policy allows', () => {

    const egress = new EgressPolicyManager()
    const started: string[] = []

    afterAll(() => {
        for (const id of started) {
            egress.release(id)
            sh(`docker rm -f ${id}`)
        }
    })

    it('keeps one workload grant out of another', () => {
        const [alpha, beta, none] = launchAll(egress, started, [
            { name: 'alpha', allowed: [`${ALPHA}/32`] },
            { name: 'beta', allowed: [`${BETA}/32`] },
            { name: 'none', allowed: [] },
        ])

        expect(reaches(alpha!, ALPHA)).toBe(true)
        expect(reaches(beta!, BETA)).toBe(true)

        // The grants are per workload, so neither may use the other's
        expect(reaches(alpha!, BETA)).toBe(false)
        expect(reaches(beta!, ALPHA)).toBe(false)

        // And a workload that asked for nothing gets nothing, while both others are running
        expect(reaches(none!, ALPHA)).toBe(false)
        expect(reaches(none!, BETA)).toBe(false)
    }, 300_000)

    it('denies every workload the metadata endpoint whatever else it allows', () => {
        const alpha = started.find(id => id.endsWith('alpha'))!
        const beta = started.find(id => id.endsWith('beta'))!

        expect(reaches(alpha, METADATA, 80)).toBe(false)
        expect(reaches(beta, METADATA, 80)).toBe(false)
    }, 120_000)

    it('does not leave a crashed workload rules behind for the next one', () => {
        const [donor] = launchAll(egress, started, [{ name: 'donor', allowed: [`${ALPHA}/32`] }])
        const address = addressOf(donor!)
        expect(reaches(donor!, ALPHA)).toBe(true)

        // Removed the way a crashed vm-manager leaves it: the container is gone and its address
        // returns to Docker's pool, but nothing released the rules naming that address
        sh(`docker rm -f ${donor}`)
        expect(Number(sh(`iptables -S DOCKER-USER | grep -c 'kinotic:${donor}' || true`))).toBeGreaterThan(0)

        // recover() runs this before the node takes any address back
        egress.reconcile(new Set(started.filter(id => id !== donor)))
        expect(sh(`iptables -S DOCKER-USER | grep -c 'kinotic:${donor}' || true`)).toBe('0')

        // Docker hands the freed address to the next container that asks, so this is the
        // workload that would have inherited the grant
        const [heir] = launchAll(egress, started, [{ name: 'heir', allowed: [] }])
        expect(reaches(heir!, ALPHA)).toBe(false)
        console.log(`      donor held ${address}, heir got ${addressOf(heir!)}`
                    + `${addressOf(heir!) === address ? ' (the same address)' : ''}`)
    }, 300_000)

})

describe.skipIf(!canRun)('many workloads on one node stay independent', () => {

    const egress = new EgressPolicyManager()
    const started: string[] = []
    // Three destinations across ten workloads, so every workload has both a destination of its
    // own and neighbours' it must not reach
    const DESTINATIONS = [ALPHA, BETA, GAMMA]
    const SPECS = Array.from({ length: 10 }, (_, index) => {
        const mine = DESTINATIONS[index % DESTINATIONS.length]!
        return {
            name: `many${index}`,
            allowed: [`${mine}/32`],
            mine,
            neighbour: DESTINATIONS.filter(destination => destination !== mine)[index % 2]!,
        }
    })

    afterAll(() => {
        for (const id of started) {
            egress.release(id)
            sh(`docker rm -f ${id}`)
        }
    })

    it('gives ten concurrent workloads their own destination and no one elses', () => {
        const ids = launchAll(egress, started, SPECS)

        ids.forEach((id, index) => {
            const spec = SPECS[index]!
            // Reported with the workload name so a failure says which one crossed over
            expect({ workload: spec.name, reached: reaches(id, spec.mine) })
                .toEqual({ workload: spec.name, reached: true })
            expect({ workload: spec.name, reached: reaches(id, spec.neighbour) })
                .toEqual({ workload: spec.name, reached: false })
        })
    }, 900_000)

    it('keeps the nodes own drops above every workload rule', () => {
        const { metadataDrops, workloadAccepts, defaultDeny } = chainOrder()

        expect(metadataDrops.length).toBeGreaterThan(0)
        expect(workloadAccepts.length).toBeGreaterThanOrEqual(SPECS.length)
        expect(defaultDeny).toBeGreaterThan(-1)

        // Evaluated top to bottom, first match wins: the node's own drops have to be reached
        // before any workload's exception, and the default-deny after all of them
        expect(Math.max(...metadataDrops)).toBeLessThan(Math.min(...workloadAccepts))
        expect(defaultDeny).toBeGreaterThan(Math.max(...workloadAccepts))
    }, 120_000)

    it('leaves the survivors untouched when half the workloads go away', () => {
        const leaving = started.filter((_, index) => index % 2 === 0)
        const surviving = started.filter((_, index) => index % 2 === 1)
        const specOf = (id: string) => SPECS[started.indexOf(id)]!

        for (const id of leaving) {
            egress.release(id)
            sh(`docker rm -f ${id}`)
        }

        // The order has to hold after the churn, not only on a freshly built chain
        const { metadataDrops, workloadAccepts, defaultDeny } = chainOrder()
        expect(Math.max(...metadataDrops)).toBeLessThan(Math.min(...workloadAccepts))
        expect(defaultDeny).toBeGreaterThan(Math.max(...workloadAccepts))

        for (const id of surviving) {
            const spec = specOf(id)
            expect({ workload: spec.name, reached: reaches(id, spec.mine) })
                .toEqual({ workload: spec.name, reached: true })
            expect({ workload: spec.name, reached: reaches(id, spec.neighbour) })
                .toEqual({ workload: spec.name, reached: false })
        }
    }, 600_000)

})
