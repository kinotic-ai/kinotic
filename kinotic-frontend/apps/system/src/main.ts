import './style.css'
import 'primeicons/primeicons.css'
import { createKinoticApp } from '@kinotic-ai/frontend-common'
import { Kinotic } from '@kinotic-ai/core'
import { ManagementApiPlugin } from '@kinotic-ai/management-api'
import { SystemApiPlugin } from '@kinotic-ai/system-api'
import App from './App.vue'
import router from './router'
import { SYSTEM_USER_STATE } from './states/SystemUserState'

Kinotic.use(ManagementApiPlugin)
Kinotic.use(SystemApiPlugin)

createKinoticApp({
    root: App,
    router,
    sessionState: SYSTEM_USER_STATE
}).mount('#app')
