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
        redirect: '/dashboard',
        children: [
            {
                name: 'dashboard',
                path: 'dashboard',

                component: () => import('./pages/Dashboard.vue'),
                meta: { sidebar: consoleSidebarItem('Dashboard', 'pi-objects-column', 10) }
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
                name: 'jobs',
                path: 'jobs',
                component: () => import('./pages/JobsPage.vue'),
                meta: { sidebar: consoleSidebarItem('Jobs', 'pi-list-check', 40) }
            },
            {
                name: 'job-run',
                path: 'jobs/:jobRunId',
                component: () => import('./pages/JobRunPage.vue'),
                props: true
            },
            {
                name: 'organization-detail',
                path: 'organizations/:organizationId',
                redirect: to => ({ name: 'org-overview', params: to.params })
            },
            {
                // Navigation-only record: gives the organization sidebar a sectionless
                // Dashboard entry, above the Organization heading, leading back to the
                // system dashboard
                name: 'org-to-dashboard',
                path: 'organizations/:organizationId/dashboard',
                redirect: { name: 'dashboard' },
                meta: { sidebar: { group: 'organization', label: 'Dashboard', icon: 'pi-objects-column', order: 1 } satisfies SidebarItemMeta }
            },
            {
                name: 'org-overview',
                path: 'organizations/:organizationId/overview',
                component: () => import('./pages/org/OrgOverview.vue'),
                props: true,
                meta: { sidebar: organizationSidebarItem('Overview', 'pi-chart-bar', 5) }
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
        redirect: '/dashboard'
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes,
})

export default router
