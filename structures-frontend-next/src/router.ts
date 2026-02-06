import {createRouter, createWebHashHistory, type RouteRecordRaw} from 'vue-router'
import pagesRoutes from '@/pages/routes'

const routes: RouteRecordRaw[] = [
    {
        path: '/',
        redirect: '/applications'
    },
    {
        path: '/404',
        meta:{
            authenticationRequired: false
        },
        component: () => import('@/pages/FourOFour.vue')
    },

    ...pagesRoutes,

    {
        path: '/:catchAll(.*)',
        redirect: '/404'
    }
]


const router = createRouter({
    history: createWebHashHistory(),
    routes,
})

export default router
