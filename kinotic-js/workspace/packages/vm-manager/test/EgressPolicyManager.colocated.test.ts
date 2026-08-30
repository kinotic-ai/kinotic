import { afterAll, describe, expect, it } from 'bun:test'
import { execSync, spawnSync } from 'node:child_process'
import { createServer } from 'node:http'
import Docker from 'dockerode'
import { EgressPolicyManager } from '@/internal/api/network/EgressPolicyManager'
import { NetnsAnchorManager } from '@/internal/api/network/NetnsAnchorManager'
import { NodeMode } from '@/api/NodeMode'

// A service on the node is refused by the INPUT chain rather than the egress chain, so whether
// a workload reaches one is only answerable against a real firewall and a real guest.
const RUNTIME = process.env.KINOTIC_TEST_RUNTIME ?? 'kata-clh'
const canRun = (() => {
    if (process.getuid?.() !== 0) {
        return false
    }
    if (spawnSync('iptables', ['-S', 'INPUT'], { encoding: 'utf-8' }).status !== 0) {
        return false
    }
    return spawnSync('docker', ['info', '-f', '{{.ServerVersion}}'], { encoding: 'utf-8' }).status === 0
})()

const RUN = Date.now().toString(36)
const PORT = 18_099
const BOOT_TIMEOUT_MS = 300_000

const sh = (command: string): string => {
    try {
        return execSync(command, { shell: '/bin/bash', encoding: 'utf-8' }).toString().trim()
    } catch {
        return ''
    }
}

/** The node's address on the workload bridge, which is where a colocated server is reached. */
const gateway = (): string =>
    sh(`docker network inspect bridge -f '{{range .IPAM.Config}}{{.Gateway}}{{end}}'`)

const reachesNode = (id: string, address: string): boolean =>
    sh(`docker exec ${id} sh -c 'nc -w 4 -z ${address} ${PORT} && echo yes'`) === 'yes'

describe.skipIf(!canRun)('a destination naming the node itself', () => {

    const server = createServer((_, response) => response.end('ok'))
    const anchors = new NetnsAnchorManager(new Docker())
    const launched: string[] = []
    const applied: Array<[EgressPolicyManager, string]> = []

    server.listen(PORT, '0.0.0.0')

    afterAll(async () => {
        for (const [manager, id] of applied) {
            manager.release(id)
        }
        for (const id of launched) {
            sh(`docker rm -f ${id}`)
            await anchors.release(id)
        }
        server.close()
    })

    // Every workload gets a namespace anchor, which is what a node needing this accommodation
    // runs anyway — so both cases below differ only in the environment, not in their network
    async function launch(name: string): Promise<{ id: string, address: string }> {
        const id = `${name}-${RUN}`
        sh(`docker rm -f ${id}`)
        const anchorId = await anchors.ensure(id, {}, {})
        sh(`docker run -d --name ${id} --runtime ${RUNTIME} --network container:${anchorId} alpine:latest sleep 300`)
        for (let attempt = 0; attempt < 60 && !sh(`docker exec ${id} true && echo up`); attempt++) {
            execSync('sleep 1')
        }
        launched.push(id)
        // The guest shares the anchor's namespace, so the anchor's address is the guest's
        const address = sh(`docker inspect -f '{{.NetworkSettings.Networks.bridge.IPAddress}}' ${anchorId}`)
        if (address === '') {
            throw new Error(`anchor for ${id} has no address on the bridge`)
        }
        return { id, address }
    }

    function grant(manager: EgressPolicyManager, id: string, address: string, hosts: string[]): void {
        applied.push([manager, id])
        manager.apply(id, address, hosts)
    }

    it('is refused under PRODUCTION even though the rule is written', async () => {
        const egress = new EgressPolicyManager(null, NodeMode.PRODUCTION)
        const { id, address } = await launch('prod')
        const node = gateway()

        grant(egress, id, address, [`${node}/32`])

        // The policy was honoured in the only chain a production node writes, and that chain
        // cannot carry this traffic: DOCKER-USER is reached from FORWARD, the node's own
        // address never is. The workload has a working NIC and still cannot get there.
        expect(Number(sh(`iptables -S DOCKER-USER | grep -c 'kinotic:${id}' || true`))).toBeGreaterThan(0)
        expect(Number(sh(`iptables -S INPUT | grep -c 'kinotic:${id}' || true`))).toBe(0)
        expect(reachesNode(id, node)).toBe(false)
    }, BOOT_TIMEOUT_MS)

    it('is reachable under DEVELOPMENT, and only by the workload that named it', async () => {
        const egress = new EgressPolicyManager(null, NodeMode.DEVELOPMENT)
        const named = await launch('dev-named')
        const silent = await launch('dev-silent')
        const node = gateway()

        grant(egress, named.id, named.address, [`${node}/32`])
        grant(egress, silent.id, silent.address, [])

        expect(reachesNode(named.id, node)).toBe(true)
        expect(reachesNode(silent.id, node)).toBe(false)

        egress.release(named.id)
        expect(Number(sh(`iptables -S INPUT | grep -c 'kinotic:${named.id}' || true`))).toBe(0)
        expect(reachesNode(named.id, node)).toBe(false)
    }, BOOT_TIMEOUT_MS)
})
