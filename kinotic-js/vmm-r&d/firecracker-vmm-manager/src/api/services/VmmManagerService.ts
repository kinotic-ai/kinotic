import {Publish} from '@mindignited/continuum-client'
import type {VmmConfig} from '../domain/VmmConfig.js'
import {VmmType} from '../domain/VmmType.js'
import {createClient} from '@/internal/api/firecracker/generated/client/index.js'
import {
	putGuestBootSource,
	putGuestDriveById,
	putGuestNetworkInterfaceById,
	putMachineConfiguration,
	putMmdsConfig,
	putMmds,
	createSyncAction,
} from '@/internal/api/firecracker/generated/sdk.gen.js'

const FIRECRACKER_BIN = 'firecracker'

@Publish('kinotic.vmm-node-manager')
export class VmmManagerService {

	public constructor() {
		console.log('VmmManagerService constructed')
	}

	public async launchVmm(vmmConfig: VmmConfig): Promise<void> {
		// Validate VMM type
		if (vmmConfig.vmmType !== VmmType.FIRECRACKER) {
			throw new Error(`Unsupported VMM type: ${vmmConfig.vmmType}. Only ${VmmType.FIRECRACKER} is supported.`);
		}

		// Validate required fields
		if (vmmConfig.id === undefined || vmmConfig.id === null || vmmConfig.id === '') {
			throw new Error('id is required');
		}
		if (!vmmConfig.kernelImagePath) {
			throw new Error('kernelImagePath is required');
		}
		if (!vmmConfig.drives || vmmConfig.drives.length === 0) {
			throw new Error('At least one drive is required');
		}
		if (!vmmConfig.networkInterfaces || vmmConfig.networkInterfaces.length === 0) {
			throw new Error('At least one network interface is required');
		}
		if (!vmmConfig.machineConfig) {
			throw new Error('machineConfig is required');
		}

		// Validate file paths exist
		const kernelFile = Bun.file(vmmConfig.kernelImagePath);
		if (!(await kernelFile.exists())) {
			throw new Error(`Kernel image not found at: ${vmmConfig.kernelImagePath}`);
		}

		for (const drive of vmmConfig.drives) {
			const driveFile = Bun.file(drive.pathOnHost);
			if (!(await driveFile.exists())) {
				throw new Error(`Drive file not found at: ${drive.pathOnHost}`);
			}
		}

		const socketPath = this.socketPathFor(vmmConfig.id);

		// Spawn Firecracker as a detached background process; it stays running after vmm-node-manager exits.
		Bun.spawn({
			cmd: [FIRECRACKER_BIN],
			env: { ...process.env, API_SOCKET: socketPath },
			detached: true,
			stdio: ['ignore', 'ignore', 'ignore'],
		});

		// Wait for the API socket to appear before configuring the VM.
		const socketDeadline = Date.now() + 5000;
		while (!(await Bun.file(socketPath).exists())) {
			if (Date.now() > socketDeadline) {
				throw new Error(`Firecracker socket did not appear at ${socketPath} within 5s`);
			}
			await new Promise((r) => setTimeout(r, 50));
		}

		// Use a dedicated client for this VM's socket to avoid contention with concurrent launch/stop.
		const fc = createClient({ baseUrl: `http://unix:${socketPath}:` });

		try {
			// 1. Configure boot source
			await putGuestBootSource({
				client: fc,
				body: {
					kernel_image_path: vmmConfig.kernelImagePath,
					boot_args: vmmConfig.bootArgs,
				},
			});

			// 2. Configure drives
			for (const drive of vmmConfig.drives) {
				await putGuestDriveById({
					client: fc,
					path: {
						drive_id: drive.driveId,
					},
					body: {
						drive_id: drive.driveId,
						path_on_host: drive.pathOnHost,
						is_root_device: drive.isRootDevice,
						is_read_only: drive.isReadOnly,
					},
				});
			}

			// 3. Configure network interfaces
			for (const iface of vmmConfig.networkInterfaces) {
				await putGuestNetworkInterfaceById({
					client: fc,
					path: {
						iface_id: iface.ifaceId,
					},
					body: {
						iface_id: iface.ifaceId,
						guest_mac: iface.guestMac,
						host_dev_name: iface.hostDevName,
					},
				});
			}

			// 4. Configure machine config
			await putMachineConfiguration({
				client: fc,
				body: {
					vcpu_count: vmmConfig.machineConfig.vcpuCount,
					mem_size_mib: vmmConfig.machineConfig.memSizeMib,
				},
			});

			// 5. Configure MMDS if provided
			if (vmmConfig.mmdsConfig) {
				await putMmdsConfig({
					client: fc,
					body: {
						version: 'V2', // Always use V2, V1 is deprecated
						network_interfaces: vmmConfig.mmdsConfig.networkInterfaces,
						ipv4_address: vmmConfig.mmdsConfig.ipv4Address,
						imds_compat: vmmConfig.mmdsConfig.imdsCompat,
					},
				});

				// Set MMDS data
				await putMmds({
					client: fc,
					body: vmmConfig.mmdsConfig.data,
				});
			}

			// 6. Start VM
			await createSyncAction({
				client: fc,
				body: {
					action_type: 'InstanceStart',
				},
			});
		} catch (error) {
			// Provide more context about which step failed
			let stepContext = '';
			if (error instanceof Error) {
				const message = error.message.toLowerCase();
				if (message.includes('boot') || message.includes('kernel')) {
					stepContext = ' (boot source configuration)';
				} else if (message.includes('drive')) {
					stepContext = ' (drive configuration)';
				} else if (message.includes('network') || message.includes('interface')) {
					stepContext = ' (network interface configuration)';
				} else if (message.includes('machine') || message.includes('config')) {
					stepContext = ' (machine configuration)';
				} else if (message.includes('mmds')) {
					stepContext = ' (MMDS configuration)';
				} else if (message.includes('action') || message.includes('start')) {
					stepContext = ' (VM start)';
				}
			}

			const errorMessage = error instanceof Error ? error.message : String(error);
			throw new Error(`Failed to launch Firecracker VM${stepContext}: ${errorMessage}`);
		}
	}

	/**
	 * Sends SendCtrlAltDel to the VM to request a graceful shutdown.
	 * The socket path is derived from the id: /tmp/firecracker-{id}.socket.
	 */
	public async stopVmm(id: string | number): Promise<void> {
		const sp = this.socketPathFor(id);
		if (!(await Bun.file(sp).exists())) {
			throw new Error(`Firecracker socket not found at ${sp}. Is the VM running?`);
		}
		const fc = createClient({ baseUrl: `http://unix:${sp}:` });
		await createSyncAction({
			client: fc,
			body: {
				action_type: 'SendCtrlAltDel',
			},
		});
	}

	private socketPathFor(id: string | number): string {
		return `/tmp/firecracker-${id}.socket`
	}

}
