import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import type { SidebarItemMeta } from '@kinotic-ai/frontend-common'

function consoleSidebarItem(label: string, icon: string, order: number): SidebarItemMeta {
    return { group: 'console', label, icon, order }
}

const routes: RouteRecordRaw[] = [
    {
        path: '/login',
        meta: {
            authenticationRequired: false
        },
        component: () => import('./pages/Login.vue')
    },
    {
        path: '/',
        component: () => import('./layouts/ConsoleLayout.vue'),
        redirect: '/overview',
        children: [
            {
                name: 'overview',
                path: 'overview',
                component: () => import('./pages/Overview.vue'),
                meta: { sidebar: consoleSidebarItem('Overview', 'pi-objects-column', 10) }
            },
            {
                name: 'organizations',
                path: 'organizations',
                component: () => import('./pages/OrganizationsPage.vue'),
                meta: { sidebar: consoleSidebarItem('Organizations', 'pi-building', 20) }
            },
            {
                name: 'worker-nodes',
                path: 'worker-nodes',
                component: () => import('./pages/NodesPage.vue'),
                meta: { sidebar: consoleSidebarItem('Worker nodes', 'pi-server', 30) }
            },
            {
                name: 'organization-detail',
                path: 'organizations/:organizationId',
                component: () => import('./pages/OrganizationDetailPage.vue'),
                props: true,
                // Detail page: shows the console sidebar without being an item in it
                meta: { sidebarGroup: 'console' }
            }
        ]
    },
    {
        path: '/:catchAll(.*)',
        redirect: '/overview'
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes,
})

export default router
