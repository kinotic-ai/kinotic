import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

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
                component: () => import('./pages/Overview.vue')
            },
            {
                name: 'organizations',
                path: 'organizations',
                component: () => import('./pages/OrganizationsPage.vue')
            },
            {
                name: 'nodes',
                path: 'nodes',
                component: () => import('./pages/NodesPage.vue')
            },
            {
                name: 'organization-detail',
                path: 'organizations/:organizationId',
                component: () => import('./pages/OrganizationDetailPage.vue'),
                props: true
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
