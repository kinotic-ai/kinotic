import './style.css'
import { createApp } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import { ManagementApiPlugin } from '@kinotic-ai/management-api'
import App from './App.vue'

// Installs the organization-facing services the dashboard reads: applications, projects,
// members and the signed-in user's profile
Kinotic.use(ManagementApiPlugin)

createApp(App).mount('#app')
