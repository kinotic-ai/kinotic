// Verifies that a node provisioned by setup-node.sh can actually honour each workload
// requirement, by running real microVMs and observing the host. It asks capability questions
// of the node, not of the vm-manager: it shells out to `docker` rather than driving
// CloudHypervisorProvider, so a failure here is the node's, not the provider's.
//
// Every assertion is against something observed — bytes that landed on disk, a process's
// executable, a line in the container's log file — rather than against a flag we set.
import { execSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const RUN = Date.now().toString(36)
// Volume mounts live under the Docker data root because that is the filesystem carrying
// project quotas; a mount from the ext4 root cannot be capped.
const BASE = `/var/lib/docker/kinotic-req-${RUN}`
const APP = join(BASE, 'app')
const WORK = join(BASE, 'work')

const sh = (cmd: string): string => execSync(cmd, { shell: '/bin/bash', encoding: 'utf-8' }).toString().trim()
// Single quotes, so the host shell cannot expand $(...) or > in a script meant for the guest
const shq = (value: string): string => `'${value.replace(/'/g, `'\\''`)}'`
const quiet = (cmd: string): string => { try { return sh(cmd) } catch { return '' } }

let passed = 0
let failed = 0
const results: string[] = []

function check(requirement: string, claim: string, holds: boolean, evidence: string): void {
    const mark = holds ? '\x1b[32m✓\x1b[0m' : '\x1b[31m✗\x1b[0m'
    console.log(`  ${mark} ${claim.padEnd(44)} ${evidence}`)
    holds ? passed++ : failed++
    results.push(`${holds ? 'PASS' : 'FAIL'} ${requirement} — ${claim}: ${evidence}`)
}

function heading(text: string): void {
    console.log(`\n\x1b[1m${text}\x1b[0m`)
}

/** Runs a workload to completion and returns everything the host observed about it. */
function runWorkload(name: string, script: string, dockerArgs = ''): { logs: string, exitCode: number, id: string } {
    quiet(`docker rm -f ${name} 2>/dev/null`)
    sh(`docker run -d --name ${name} --runtime kata-clh ${dockerArgs} alpine:latest sh -c ${shq(script)}`)
    const id = sh(`docker inspect -f '{{.Id}}' ${name}`)
    // Kata boots a real kernel and guest image, so a workload is not immediately live
    sh(`timeout 90 docker wait ${name} >/dev/null 2>&1 || true`)
    const logPath = sh(`docker inspect -f '{{.LogPath}}' ${name}`)
    const logs = existsSync(logPath)
        ? readFileSync(logPath, 'utf-8').trim().split('\n').filter(Boolean).map(l => JSON.parse(l)).map(e => `[${e.stream}] ${e.log.trimEnd()}`).join('\n')
        : ''
    const exitCode = Number(sh(`docker inspect -f '{{.State.ExitCode}}' ${name}`))
    return { logs, exitCode, id }
}

for (const dir of [APP, WORK]) mkdirSync(dir, { recursive: true })
writeFileSync(join(APP, 'version.txt'), 'v1')
sh('docker pull -q alpine:latest >/dev/null')

// ---------------------------------------------------------------------------------------
heading('R1 — customer code is isolated in a microVM')

const probe = `probe-${RUN}`
quiet(`docker rm -f ${probe}`)
sh(`docker run -d --name ${probe} --runtime kata-clh alpine:latest sleep 120`)
execSync('sleep 10')
const guestKernel = sh(`docker exec ${probe} uname -r`)
const hostKernel = sh('uname -r')
check('R1', 'guest runs its own kernel', guestKernel !== hostKernel, `guest ${guestKernel} vs host ${hostKernel}`)

// A differing kernel proves a VM booted, not which hypervisor booted it. comm is truncated to
// 15 chars so it never equals "cloud-hypervisor", and a pgrep -f pattern matches this
// process's own command line — so compare each process's actual executable.
const vmms = quiet(`for p in /proc/[0-9]*; do readlink -f $p/exe 2>/dev/null; done | grep -c '/cloud-hypervisor$'`)
check('R1', 'a cloud-hypervisor process backs it', Number(vmms) > 0, `${vmms} VMM process(es)`)
const dmi = quiet(`docker exec ${probe} sh -c ${shq('dmesg 2>/dev/null | grep -m1 -i "DMI:"')}`)
check('R1', 'the guest names its own hypervisor', /cloud hypervisor/i.test(dmi), dmi.replace(/^.*DMI:\s*/, '').slice(0, 46) || '(no DMI line)')
quiet(`docker rm -f ${probe}`)

// ---------------------------------------------------------------------------------------
heading('R2 — workload logs are captured host-side for Alloy')

const logged = runWorkload(`logs-${RUN}`,
    'echo LINE-ON-STDOUT; echo LINE-ON-STDERR >&2; exit 0',
    '--log-opt max-size=5m --log-opt max-file=3')
check('R2', 'stdout captured without a mount', logged.logs.includes('[stdout] LINE-ON-STDOUT'), 'json-file')
check('R2', 'stderr captured and distinguished', logged.logs.includes('[stderr] LINE-ON-STDERR'), 'stream label present')
const logOpts = sh(`docker inspect -f '{{json .HostConfig.LogConfig.Config}}' logs-${RUN}`)
check('R2', 'LogPolicy maps to log rotation', logOpts.includes('max-size') && logOpts.includes('max-file'), logOpts)
quiet(`docker rm -f logs-${RUN}`)

// ---------------------------------------------------------------------------------------
heading('R3 — the guest reaches as little as possible')

const netProbe = `net-${RUN}`
quiet(`docker rm -f ${netProbe}`)
sh(`docker run -d --name ${netProbe} --runtime kata-clh alpine:latest sleep 200`)
execSync('sleep 10')
// On a node that denies egress by default, the vm-manager grants each workload its resolver.
// This harness starts containers directly, so it writes the same rule for its own probe —
// otherwise the DNS check below would fail for the absence of a rule, not a broken resolver.
const denyByDefault = existsSync('/etc/kinotic/egress-default-deny')
const probeIp = sh(`docker inspect -f '{{.NetworkSettings.Networks.bridge.IPAddress}}' ${netProbe}`)
if (denyByDefault) {
    sh(`iptables -I DOCKER-USER -s ${probeIp} -d 168.63.129.16 -p udp --dport 53 -m comment --comment 'kinotic-req-test' -j ACCEPT`)
}
const imds = quiet(`docker exec ${netProbe} sh -c ${shq('wget -q -T4 -O- --header=Metadata:true "http://169.254.169.254/metadata/instance/compute/name?api-version=2021-02-01&format=text" 2>/dev/null')}`)
check('R3', 'Azure IMDS unreachable from the guest', imds === '', imds === '' ? 'refused' : `LEAKED: ${imds}`)
const wire = quiet(`docker exec ${netProbe} sh -c ${shq('wget -q -T4 -O- --header=x-ms-version:2012-11-30 "http://168.63.129.16/machine/?comp=goalstate" 2>/dev/null | head -c 20')}`)
check('R3', 'Azure WireServer control port unreachable', wire === '', wire === '' ? 'refused' : `LEAKED: ${wire}`)
const dns = quiet(`docker exec ${netProbe} sh -c ${shq('nslookup example.com >/dev/null 2>&1 && echo ok')}`)
check('R3', 'DNS still resolves', dns === 'ok', dns === 'ok' ? 'resolver reachable' : 'DNS broken')

// Tenant isolation: a second workload must not reach the first's listening port
const victim = `victim-${RUN}`
quiet(`docker rm -f ${victim}`)
sh(`docker run -d --name ${victim} --runtime kata-clh alpine:latest sh -c ${shq('while true; do echo TENANT-SECRET | nc -l -p 8080 >/dev/null 2>&1; done')}`)
execSync('sleep 10')
const victimIp = sh(`docker inspect -f '{{.NetworkSettings.Networks.bridge.IPAddress}}' ${victim}`)
const leak = quiet(`docker exec ${netProbe} sh -c ${shq(`nc -w 4 ${victimIp} 8080 2>/dev/null`)}`)
check('R3', 'one workload cannot reach another', !leak.includes('TENANT-SECRET'), leak ? `LEAKED: ${leak}` : `refused (${victimIp})`)
if (denyByDefault) {
    quiet(`iptables -D DOCKER-USER -s ${probeIp} -d 168.63.129.16 -p udp --dport 53 -m comment --comment 'kinotic-req-test' -j ACCEPT`)
}
quiet(`docker rm -f ${netProbe} ${victim}`)

const disabled = runWorkload(`nonet-${RUN}`,
    'nc -w 4 -z 1.1.1.1 443 >/dev/null 2>&1 && echo REACHED || echo refused',
    '--network none')
check('R3', 'NetworkMode.DISABLED denies everything', disabled.logs.includes('refused'), disabled.logs.trim() || '(no output)')
quiet(`docker rm -f nonet-${RUN}`)

// ---------------------------------------------------------------------------------------
heading('R4/R5 — read-only app code and a writable, capped mount')

const mounts = runWorkload(`mounts-${RUN}`, [
    'echo "app: $(cat /app/version.txt)"',
    'echo "ro-mount: $( (echo x > /app/deny) 2>&1 >/dev/null || true )"',
    'echo written > /work/out.txt',
    'echo "rw-mount: $(cat /work/out.txt)"',
].join('; '), `-v ${APP}:/app:ro -v ${WORK}:/work:rw`)
check('R5', 'both mounts present in one workload', mounts.logs.includes('app: v1') && mounts.logs.includes('rw-mount: written'), '2 mounts, no IRQ limit')
check('R5', 'readOnly is enforced by the guest', /Read-only file system/.test(mounts.logs), 'write refused')
quiet(`docker rm -f mounts-${RUN}`)

// The edit/redeploy loop: a host-side edit is visible to the next workload without rebuilding
writeFileSync(join(APP, 'version.txt'), 'v2')
const redeploy = runWorkload(`redeploy-${RUN}`, 'cat /app/version.txt', `-v ${APP}:/app:ro`)
check('R4', 'host edits reach the next workload', redeploy.logs.includes('v2'), 'shared location, no image rebuild')
quiet(`docker rm -f redeploy-${RUN}`)

// ---------------------------------------------------------------------------------------
heading('R6 — VolumeMount.sizeLimitMb bounds what the guest writes')

const projectId = 10_000 + (Date.now() % 5000)
sh(`xfs_quota -x -c 'project -s -p ${WORK} ${projectId}' /var/lib/docker >/dev/null`)
sh(`xfs_quota -x -c 'limit -p bhard=64m ${projectId}' /var/lib/docker`)
const capped = runWorkload(`mountcap-${RUN}`,
    'dd if=/dev/zero of=/work/fill bs=1M count=200 2>/dev/null; echo "landed: $(wc -c < /work/fill)"',
    `-v ${WORK}:/work:rw`)
const mountBytes = Number(/landed: (\d+)/.exec(capped.logs)?.[1] ?? -1)
check('R6', '200MB write into a 64MB cap is refused', mountBytes > 0 && mountBytes <= 67_108_864,
      `${mountBytes} bytes landed (cap 67108864)`)
sh(`xfs_quota -x -c 'limit -p bhard=0 ${projectId}' /var/lib/docker`)
quiet(`docker rm -f mountcap-${RUN}`)

// ---------------------------------------------------------------------------------------
heading('R7 — the server sets the workload filesystem size')

const rootfs = runWorkload(`diskcap-${RUN}`,
    'dd if=/dev/zero of=/big bs=1M count=1500 2>/dev/null; echo "landed: $(wc -c < /big)"',
    '--storage-opt size=1024m')
const rootBytes = Number(/landed: (\d+)/.exec(rootfs.logs)?.[1] ?? -1)
check('R7', '1500MB write into a 1024MB rootfs is capped', rootBytes > 0 && rootBytes <= 1_073_741_824,
      `${rootBytes} bytes landed (cap 1073741824)`)
quiet(`docker rm -f diskcap-${RUN}`)

// ---------------------------------------------------------------------------------------
heading('Lifecycle the provider depends on')

const life = `life-${RUN}`
quiet(`docker rm -f ${life}`)
sh(`docker run -d --name ${life} --runtime kata-clh alpine:latest sh -c ${shq('echo "boot $(cat /marker 2>/dev/null || echo first)"; date +%s > /marker; sleep 200')}`)
execSync('sleep 10')
sh(`docker stop -t 10 ${life} >/dev/null`)
const stoppedCode = sh(`docker inspect -f '{{.State.ExitCode}}' ${life}`)
check('lifecycle', 'a stopped workload reports an exit code', stoppedCode !== '', `exit ${stoppedCode}`)
sh(`docker start ${life} >/dev/null`)
execSync('sleep 10')
const bootLines = readFileSync(sh(`docker inspect -f '{{.LogPath}}' ${life}`), 'utf-8')
    .trim().split('\n').map(l => JSON.parse(l).log.trim()).filter(l => l.startsWith('boot'))
check('lifecycle', 'restart keeps the writable layer', bootLines.length === 2 && bootLines[1] !== 'boot first',
      JSON.stringify(bootLines))
const byLabel = sh(`docker ps -aq --filter name=${life} | wc -l`)
check('lifecycle', 'a new process can find running workloads', byLabel === '1', 'discoverable via the daemon')
quiet(`docker rm -f ${life}`)

// ---------------------------------------------------------------------------------------
rmSync(BASE, { recursive: true, force: true })
console.log(`\n${failed === 0 ? '\x1b[32mALL REQUIREMENTS MET\x1b[0m' : '\x1b[31mREQUIREMENTS NOT MET\x1b[0m'} — ${passed} passed, ${failed} failed`)
writeFileSync(join(process.cwd(), 'last-run.txt'), results.join('\n') + '\n')
process.exit(failed === 0 ? 0 : 1)
