import {
	vmmNodeManagerConfig,
	type VmmNodeManagerConfig,
} from '@/api/config/VmmNodeManagerConfig.js'
import {KinoticMicrovmLauncherService} from '@/api/services/KinoticMicrovmLauncherService.js'
import {VmmManagerService} from '@/api/services/VmmManagerService.js'
import {Continuum} from '@mindignited/continuum-client'

export class BootstrapService {
	private vmmManagerService!: VmmManagerService
	private kinoticMicrovmLauncherService!: KinoticMicrovmLauncherService

	public async bootstrap(): Promise<void> {
		await Continuum.connect({
							  host: 'localhost',
							  port: 58503,
							  connectHeaders: {
								  login: 'guest',
								  passcode: 'guest'
							  }
						  })

		console.log('Connected to the Kinotic Gateway.')

		// 2. Create services
		this.vmmManagerService = new VmmManagerService()
		this.kinoticMicrovmLauncherService = new KinoticMicrovmLauncherService(this.vmmManagerService)
	}

	public getConfig(): VmmNodeManagerConfig {
		return vmmNodeManagerConfig
	}

	public getVmmManagerService(): VmmManagerService {
		return this.vmmManagerService
	}

	public getKinoticMicrovmLauncherService(): KinoticMicrovmLauncherService {
		return this.kinoticMicrovmLauncherService
	}
}
