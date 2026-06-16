import {copyFileSync, mkdirSync} from 'node:fs'
import {join} from 'node:path'
import {vmmNodeManagerConfig} from '../config/VmmNodeManagerConfig.js'
import type {KinoticMicrovmLaunchResult} from '../domain/KinoticMicrovmLaunchResult.js'
import {VmmType} from '../domain/VmmType.js'
import type {VmmConfig} from '../domain/VmmConfig.js'
import type {VmmManagerService} from './VmmManagerService.js'
import {Publish} from '@mindignited/continuum-client'

const NETWORK_BASE = '172.16'
const SUBNET_SIZE = 4
const NETMASK = '255.255.255.252'
const CIDR = 30

@Publish('kinotic.vmm-node-manager')
export class KinoticMicrovmLauncherService {
	private readonly paths = vmmNodeManagerConfig

	constructor(private readonly vmmManager: VmmManagerService) {}

	async launch(vmIndex: number): Promise<KinoticMicrovmLaunchResult> {
		const tapName = `tap${vmIndex}`
		const {tapIp, guestIp} = this.computeIps(SUBNET_SIZE, vmIndex)

		// 1. Create tap
		await this.execIpCommand(['tuntap', 'add', tapName, 'mode', 'tap'])
		await this.execIpCommand(['addr', 'add', `${tapIp}/${CIDR}`, 'dev', tapName])
		await this.execIpCommand(['link', 'set', tapName, 'up'])

		// 2. Copy overlay
		mkdirSync(this.paths.overlayOutputDir, {recursive: true})
		const overlayPath = join(this.paths.overlayOutputDir, `overlay-${vmIndex}.ext4`)
		const masterFile = Bun.file(this.paths.masterOverlayPath)
		if (!(await masterFile.exists())) {
			throw new Error(`Master overlay not found at: ${this.paths.masterOverlayPath}`)
		}
		copyFileSync(this.paths.masterOverlayPath, overlayPath)

		// 3. Build bootArgs
		const bootArgs = `console=ttyS0 reboot=k panic=1 pci=off overlay_root=vdb init=/sbin/overlay-init.sh ip=${guestIp}::${tapIp}:${NETMASK}::eth0:off loglevel=7 earlyprintk=serial`

		// 4. Build VmmConfig
		const vmmConfig: VmmConfig = {
			id: vmIndex,
			vmmType: VmmType.FIRECRACKER,
			kernelImagePath: this.paths.kernelImagePath,
			bootArgs,
			drives: [
				{
					driveId: 'rootfs',
					pathOnHost: this.paths.rootfsPath,
					isRootDevice: true,
					isReadOnly: true,
				},
				{
					driveId: 'overlayfs',
					pathOnHost: overlayPath,
					isRootDevice: false,
					isReadOnly: false,
				},
			],
			networkInterfaces: [{ifaceId: 'eth0', hostDevName: tapName}],
			machineConfig: {vcpuCount: 1, memSizeMib: 128},
		}

		// 5. Launch via VmmManagerService
		await this.vmmManager.launchVmm(vmmConfig)

		return {
			vmIndex,
			tapIp,
			guestIp,
			overlayPath,
			tapName,
		}
	}

	/**
	 * Stops the VM (sends SendCtrlAltDel to request shutdown) and removes the tap device.
	 * Does not delete the overlay file.
	 */
	async stop(vmIndex: number): Promise<void> {
		await this.vmmManager.stopVmm(vmIndex)
		await this.execIpCommand(['link', 'delete', `tap${vmIndex}`])
	}

	/**
	 * Sequential subnet allocation for tap and guest IPs. Each VM gets a /30 (4 addresses):
	 * network, tap (host), guest (VM), broadcast. For VM index O (0-based) and A = subnetSize
	 * (e.g. 4 for /30), we place this VM's /30 at offset (A*O) in the 172.16.0.0/16 range.
	 * Tap = 2nd address (offset A*O+1), guest = 3rd (offset A*O+2). The offset is encoded as
	 * 3rd octet = floor(offset/256), 4th = offset%256. Example: O=0 → tap 172.16.0.1, guest 172.16.0.2;
	 * O=999 → tap 172.16.15.157, guest 172.16.15.158.
	 */
	private computeIps(subnetSize: number, vmIndex: number): {tapIp: string; guestIp: string} {
		const tapThird = Math.floor((subnetSize * vmIndex + 1) / 256)
		const tapFourth = (subnetSize * vmIndex + 1) % 256
		const guestThird = Math.floor((subnetSize * vmIndex + 2) / 256)
		const guestFourth = (subnetSize * vmIndex + 2) % 256
		return {
			tapIp: `${NETWORK_BASE}.${tapThird}.${tapFourth}`,
			guestIp: `${NETWORK_BASE}.${guestThird}.${guestFourth}`,
		}
	}

	private async execIpCommand(args: string[]): Promise<void> {
		const proc = Bun.spawn({
			cmd: ['ip', ...args],
			stdout: 'pipe',
			stderr: 'pipe',
		})
		const exitCode = await proc.exited
		if (exitCode !== 0) {
			const stderr = await new Response(proc.stderr).text()
			throw new Error(`ip ${args.join(' ')} failed (exit ${exitCode}): ${stderr}`)
		}
	}
}
