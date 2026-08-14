import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import type { SidebarItemMeta } from '@kinotic-ai/frontend-common'

function consoleSidebarItem(label: string, icon: string, order: number): SidebarItemMeta {
    return { group: 'console', label, icon, order }
}

// The sidebar swaps to this group while drilled into an organization, mirroring the
// portal's organization sidebar; the header's organization switcher carries the context
function organizationSidebarItem(label: string, icon: string, order: number): SidebarItemMeta {
    return { group: 'organization', section: 'Organization', label, icon, order }
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
                redirect: to => ({ name: 'org-applications', params: to.params })
            },
            {
                name: 'org-applications',
                path: 'organizations/:organizationId/applications',
                component: () => import('./pages/org/OrgApplicationsTable.vue'),
                props: true,
                meta: { sidebar: organizationSidebarItem('Applications', 'pi-th-large', 10) }
            },
            {
                name: 'org-projects',
                path: 'organizations/:organizationId/projects',
                component: () => import('./pages/org/OrgProjectsTable.vue'),
                props: true,
                meta: { sidebar: organizationSidebarItem('Projects', 'pi-folder', 20) }
            },
            {
                name: 'org-members',
                path: 'organizations/:organizationId/members',
                component: () => import('./pages/org/OrgMembersTable.vue'),
                props: true,
                meta: { sidebar: organizationSidebarItem('Members', 'pi-users', 30) }
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
