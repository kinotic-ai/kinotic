import 'reflect-metadata'
import {BootstrapService} from './internal/api/services/BootstrapService.js'

export {vmmNodeManagerConfig, type VmmNodeManagerConfig} from './api/config/VmmNodeManagerConfig.js'
export type {KinoticMicrovmLaunchResult} from './api/domain/KinoticMicrovmLaunchResult.js'
export {KinoticMicrovmLauncherService} from './api/services/KinoticMicrovmLauncherService.js'

const bootstrapService = new BootstrapService()

console.log('Starting VMM Node Manager bootstrap process...')

await bootstrapService.bootstrap()

console.log('VMM Node Manager bootstrap process completed.')
