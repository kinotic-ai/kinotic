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

/** Two destinations off the workload bridge, so what is measured is the rules and not icc. */
const ALPHA = '1.1.1.1'
const BETA = '8.8.8.8'
const METADATA = '169.254.169.254'

const started: string[] = []

/** Starts a workload, applies the policy to the address Docker gave it, and returns both. */
function launch(egress: EgressPolicyManager, name: string, allowedHosts: string[]): string {
    const id = `reach-${RUN}-${name}`
    sh(`docker rm -f ${id}`)
    sh(`docker run -d --name ${id} --runtime ${RUNTIME} alpine:latest sleep 300`)
    started.push(id)
    // Kata boots a kernel and guest image, so the container is not immediately usable
    for (let attempt = 0; attempt < 30 && !sh(`docker exec ${id} true && echo up`); attempt++) {
        execSync('sleep 1')
    }
    const address = sh(`docker inspect -f '{{.NetworkSettings.Networks.bridge.IPAddress}}' ${id}`)
    egress.apply(id, address, allowedHosts)
    return id
}

/** Whether the workload can open a connection to the address. */
function reaches(id: string, address: string, port = 443): boolean {
    return sh(`docker exec ${id} sh -c 'nc -w 4 -z ${address} ${port} && echo yes'`) === 'yes'
}

describe.skipIf(!canRun)('a workload reaches only what its own policy allows', () => {

    const egress = new EgressPolicyManager()

    afterAll(() => {
        for (const id of started) {
            egress.release(id)
            sh(`docker rm -f ${id}`)
        }
    })

    it('keeps one workload grant out of another', () => {
        const alpha = launch(egress, 'alpha', [`${ALPHA}/32`])
        const beta = launch(egress, 'beta', [`${BETA}/32`])
        const none = launch(egress, 'none', [])

        expect(reaches(alpha, ALPHA)).toBe(true)
        expect(reaches(beta, BETA)).toBe(true)

        // The grants are per workload, so neither may use the other's
        expect(reaches(alpha, BETA)).toBe(false)
        expect(reaches(beta, ALPHA)).toBe(false)

        // And a workload that asked for nothing gets nothing, while both others are running
        expect(reaches(none, ALPHA)).toBe(false)
        expect(reaches(none, BETA)).toBe(false)
    }, 300_000)

    it('denies every workload the metadata endpoint whatever else it allows', () => {
        const alpha = started.find(id => id.endsWith('alpha'))!
        const beta = started.find(id => id.endsWith('beta'))!

        expect(reaches(alpha, METADATA, 80)).toBe(false)
        expect(reaches(beta, METADATA, 80)).toBe(false)
    }, 120_000)

    it('does not leave a crashed workload rules behind for the next one', () => {
        const donor = launch(egress, 'donor', [`${ALPHA}/32`])
        const address = sh(`docker inspect -f '{{.NetworkSettings.Networks.bridge.IPAddress}}' ${donor}`)
        expect(reaches(donor, ALPHA)).toBe(true)

        // Removed the way a crashed vm-manager leaves it: the container is gone and its address
        // returns to Docker's pool, but nothing released the rules naming that address
        sh(`docker rm -f ${donor}`)
        expect(Number(sh(`iptables -S DOCKER-USER | grep -c 'kinotic:${donor}'`))).toBeGreaterThan(0)

        // recover() runs this before the node takes any address back
        egress.reconcile(new Set(started.filter(id => id !== donor)))
        expect(sh(`iptables -S DOCKER-USER | grep -c 'kinotic:${donor}' || true`)).toBe('0')

        // Docker hands the freed address to the next container that asks, so this is the
        // workload that would have inherited the grant
        const heir = launch(egress, 'heir', [])
        const inherited = sh(`docker inspect -f '{{.NetworkSettings.Networks.bridge.IPAddress}}' ${heir}`)
        expect(reaches(heir, ALPHA)).toBe(false)
        console.log(`      donor held ${address}, heir got ${inherited}`
                    + `${inherited === address ? ' (the same address)' : ''}`)
    }, 300_000)

})
