import debug from 'debug'

/**
 * Creates a debug logger for a specific component/module
 * Usage: const debug = createDebug('component-name')
 * 
 * To enable logging in browser console:
 * localStorage.setItem('debug', 'kinotic-ui:*')
 * 
 * To enable specific modules:
 * localStorage.setItem('debug', 'kinotic-ui:entity-list,kinotic-ui:login')
 */
export function createDebug(name: string) {
  return debug(`kinotic-ui:${name}`)
}
