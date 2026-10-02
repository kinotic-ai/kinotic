import { markRaw } from 'vue'
import { CircleAlert, CircleCheck, CirclePlay, Rocket, Trash2, TriangleAlert, XCircle } from '@lucide/vue'
import { TINTS } from '@kinotic-ai/frontend-common'
import type { CategoryInfo } from './CategoryInfo'
import { NotificationCategory } from './NotificationCategory'

// Warnings in amber, as the platform marks them everywhere
const AMBER = 'bg-amber-100 text-amber-600 dark:bg-amber-500/15 dark:text-amber-300'

/** Every category's presentation, in the order the settings list them. */
export const CATEGORIES: Record<NotificationCategory, CategoryInfo> = {
    [NotificationCategory.RUN_FAILED]: {
        label: 'Failed job runs', description: 'A deployment or other job run stopped on an error.',
        group: 'Job runs', icon: markRaw(XCircle), tint: TINTS.red, failure: true, defaultOn: true
    },
    [NotificationCategory.RUN_COMPLETED]: {
        label: 'Completed job runs', description: 'A job run finished every step.',
        group: 'Job runs', icon: markRaw(CircleCheck), tint: TINTS.green, failure: false, defaultOn: true
    },
    [NotificationCategory.RUN_STARTED]: {
        label: 'Started job runs', description: 'A job run began, for example after a push to a project.',
        group: 'Job runs', icon: markRaw(CirclePlay), tint: TINTS.sky, failure: false, defaultOn: false
    },
    [NotificationCategory.DEPLOYMENT_FAILED]: {
        label: 'Failed deployments', description: 'A project, microservice or UI reported its deployment failed.',
        group: 'Deployments', icon: markRaw(CircleAlert), tint: TINTS.red, failure: true, defaultOn: true
    },
    [NotificationCategory.DEPLOYMENT_SUCCEEDED]: {
        label: 'Successful deployments', description: 'A project, microservice or UI reported it is deployed and ready.',
        group: 'Deployments', icon: markRaw(Rocket), tint: TINTS.green, failure: false, defaultOn: true
    },
    [NotificationCategory.DEPLOYMENT_WARNING]: {
        label: 'Warnings', description: 'Something needs attention, such as a node that stopped answering.',
        group: 'Deployments', icon: markRaw(TriangleAlert), tint: AMBER, failure: false, defaultOn: true
    },
    [NotificationCategory.DEPLOYMENT_REMOVED]: {
        label: 'Removals', description: 'A deployment was asked to be removed.',
        group: 'Deployments', icon: markRaw(Trash2), tint: TINTS.ink, failure: false, defaultOn: true
    }
}
