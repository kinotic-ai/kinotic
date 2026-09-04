import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import type { SidebarItemMeta } from '@kinotic-ai/frontend-common'

/**
 * The console has four scopes, each with its own sidebar group: the platform, one organization,
 * one of its applications, and one of its projects. A page opened from a list (a node, a
 * workload, a job run, a trace) nests under the list's path and declares the group alone, so
 * the sidebar keeps the list highlighted and the page header can point back to it.
 */

function consoleItem(label: string, icon: string, order: number, section?: string): SidebarItemMeta {
    return { group: 'console', section, label, icon, order }
}

function organizationItem(label: string, icon: string, order: number, section?: string): SidebarItemMeta {
    return { group: 'organization', section, label, icon, order }
}

function applicationItem(label: string, icon: string, order: number, section?: string): SidebarItemMeta {
    return { group: 'application', section, label, icon, order }
}

function projectItem(label: string, icon: string, order: number, section?: string): SidebarItemMeta {
    return { group: 'project', section, label, icon, order }
}

const ORGANIZATION = 'organizations/:organizationId'
const APPLICATION = `${ORGANIZATION}/applications/:applicationId`
const PROJECT = `${APPLICATION}/project/:projectId`

/**
 * The pages every scope has, under the scope's path: the workloads and job runs it owns with
 * their detail pages, and, where given, its observability with the traces opened from it.
 */
function runtimeRoutes(prefix: string, name: string, item: (label: string, icon: string, order: number, section?: string) => SidebarItemMeta,
                       group: string, order: number, observability: boolean): RouteRecordRaw[] {
    const at = (path: string) => prefix ? `${prefix}/${path}` : path
    const ret: RouteRecordRaw[] = [
        {
            name: `${name}workloads`,
            path: at('workloads'),
            component: () => import('./pages/WorkloadsPage.vue'),
            props: true,
            meta: { sidebar: item('Workloads', 'pi-box', order, 'Runtime') }
        },
        {
            name: `${name}workload`,
            path: at('workloads/:workloadId'),
            component: () => import('./pages/WorkloadPage.vue'),
            props: true,
            meta: { sidebarGroup: group }
        },
        {
            name: `${name}jobs`,
            path: at('jobs'),
            component: () => import('./pages/JobsPage.vue'),
            props: true,
            meta: { sidebar: item('Jobs', 'pi-list-check', order + 10, 'Runtime') }
        },
        {
            name: `${name}job-run`,
            path: at('jobs/:jobRunId'),
            component: () => import('./pages/JobRunPage.vue'),
            props: true,
            meta: { sidebarGroup: group }
        }
    ]
    if (observability) {
        ret.push(
            {
                name: `${name}observability`,
                path: at('observability'),
                component: () => import('./pages/ObservabilityPage.vue'),
                props: true,
                meta: { sidebar: item('Observability', 'pi-chart-line', order + 20, 'Runtime') }
            },
            {
                name: `${name}trace`,
                path: at('observability/traces/:traceId'),
                component: () => import('./pages/TracePage.vue'),
                props: true,
                meta: { sidebarGroup: group }
            })
    }
    return ret
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
                meta: { sidebar: consoleItem('Dashboard', 'pi-objects-column', 10) }
            },
            {
                name: 'cluster',
                path: 'cluster',
                component: () => import('./pages/ClusterPage.vue'),
                meta: { sidebar: consoleItem('Cluster', 'pi-sitemap', 20, 'Platform') }
            },
            {
                name: 'worker-nodes',
                path: 'worker-nodes',
                component: () => import('./pages/NodesPage.vue'),
                meta: { sidebar: consoleItem('Worker nodes', 'pi-server', 30, 'Platform') }
            },
            {
                name: 'worker-node',
                path: 'worker-nodes/:nodeId',
                component: () => import('./pages/NodePage.vue'),
                props: true,
                meta: { sidebarGroup: 'console' }
            },
            // The platform's runtime sits in the Platform section rather than one of its own
            ...runtimeRoutes('', '', (label, icon, order) => consoleItem(label, icon, order, 'Platform'), 'console', 40, true),
            {
                name: 'organizations',
                path: 'organizations',
                component: () => import('./pages/OrganizationsPage.vue'),
                meta: { sidebar: consoleItem('Organizations', 'pi-building', 70, 'Tenants') }
            },

            {
                name: 'org-overview',
                path: ORGANIZATION,
                component: () => import('./pages/OrgOverview.vue'),
                props: true,
                meta: { sidebar: organizationItem('Overview', 'pi-objects-column', 10) }
            },
            {
                name: 'org-applications',
                path: `${ORGANIZATION}/applications`,
                component: () => import('./pages/OrgApplicationsPage.vue'),
                props: true,
                meta: { sidebar: organizationItem('Applications', 'pi-th-large', 20) }
            },
            {
                name: 'org-projects',
                path: `${ORGANIZATION}/projects`,
                component: () => import('./pages/ProjectsPage.vue'),
                props: true,
                meta: { sidebar: organizationItem('Projects', 'pi-folder', 30) }
            },
            {
                name: 'org-members',
                path: `${ORGANIZATION}/members`,
                component: () => import('./pages/MembersPage.vue'),
                props: true,
                meta: { sidebar: organizationItem('Members', 'pi-users', 40) }
            },
            ...runtimeRoutes(ORGANIZATION, 'org-', organizationItem, 'organization', 50, true),

            {
                name: 'app-overview',
                path: APPLICATION,
                component: () => import('./pages/AppOverview.vue'),
                props: true,
                meta: { sidebar: applicationItem('Overview', 'pi-objects-column', 10) }
            },
            {
                name: 'app-projects',
                path: `${APPLICATION}/projects`,
                component: () => import('./pages/ProjectsPage.vue'),
                props: true,
                meta: { sidebar: applicationItem('Projects', 'pi-folder', 20) }
            },
            {
                name: 'app-users',
                path: `${APPLICATION}/users`,
                component: () => import('./pages/MembersPage.vue'),
                props: true,
                meta: { sidebar: applicationItem('Users', 'pi-users', 30, 'Access') }
            },
            ...runtimeRoutes(APPLICATION, 'app-', applicationItem, 'application', 40, true),

            {
                name: 'project-overview',
                path: PROJECT,
                component: () => import('./pages/ProjectOverview.vue'),
                props: true,
                meta: { sidebar: projectItem('Overview', 'pi-objects-column', 10) }
            },
            {
                name: 'project-deployment',
                path: `${PROJECT}/deployment`,
                component: () => import('./pages/ProjectDeploymentPage.vue'),
                props: true,
                meta: { sidebar: projectItem('Deployment', 'pi-cloud-upload', 20) }
            },
            ...runtimeRoutes(PROJECT, 'project-', projectItem, 'project', 30, false)
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
