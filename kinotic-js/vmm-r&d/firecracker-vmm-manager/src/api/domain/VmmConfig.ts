import type {VmmType} from './VmmType.js'
import type {DriveConfig} from './DriveConfig.js'
import type {NetworkInterfaceConfig} from './NetworkInterfaceConfig.js'
import type {MachineConfig} from './MachineConfig.js'
import type {MmdsConfig} from './MmdsConfig.js'

export class VmmConfig {

	/** Used to derive the API socket path: /tmp/firecracker-{id}.socket */
	public id!: string | number;
	public vmmType!: VmmType;
	public kernelImagePath!: string;
	public bootArgs?: string;
	public drives!: DriveConfig[];
	public networkInterfaces!: NetworkInterfaceConfig[];
	public machineConfig!: MachineConfig;
	public mmdsConfig?: MmdsConfig;

}
