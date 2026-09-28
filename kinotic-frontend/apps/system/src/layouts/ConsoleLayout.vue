<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import { Building2, LayoutGrid, Shield, User } from '@lucide/vue'
import { useRoute } from 'vue-router'
import { ProjectsIcon, SideBar, SidebarScope } from '@kinotic-ai/frontend-common'
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

import Header from './Header.vue'
import { applicationPath, organizationPath } from '@/util/scope'

/** What the sidebar's scope block shows for the current route's sidebar group. */
interface SidebarScopeProps {
    name: string
    kind: string
    icon: Component
    backTo?: string
    backLabel?: string
}

const sidebarRef = ref<InstanceType<typeof SideBar> | null>(null)
const route = useRoute()

// Small screens keep the sidebar in a drawer the header's menu button opens
const navOpen = ref(false)

const isSidebarCollapsed = computed(() => {
    return sidebarRef.value?.collapsed ?? false
})

const isDark = computed(() => darkMode.value)

/**
 * Every scope's sidebar has the portal's shape: the row above the name leads to the parent
 * scope's landing page and is labelled with the parent's name, so the way up reads the same
 * whether the operator is in an organization, an application, or a project.
 */
function scopeFor(group: string | null): SidebarScopeProps {
    const organizationId = route.params.organizationId as string | undefined
    const applicationId = route.params.applicationId as string | undefined
    const projectId = route.params.projectId as string | undefined
    let ret: SidebarScopeProps
    if (group === 'project' && organizationId && applicationId && projectId) {
        ret = {
            name: projectId,
            kind: 'Project',
            icon: ProjectsIcon,
            backTo: applicationPath(organizationId, applicationId),
            backLabel: applicationId
        }
    } else if (group === 'application' && organizationId && applicationId) {
        ret = {
            name: applicationId,
            kind: 'Application',
            icon: LayoutGrid,
            backTo: organizationPath(organizationId),
            backLabel: organizationId
        }
    } else if (group === 'organization' && organizationId) {
        ret = {
            name: organizationId,
            kind: 'Organization',
            icon: Building2,
            backTo: '/organizations',
            backLabel: 'System'
        }
    } else if (group === 'account') {
        ret = {
            name: 'Account',
            kind: 'Operator',
            icon: User,
            backTo: '/dashboard',
            backLabel: 'System'
        }
    } else {
        ret = { name: 'Kinotic', kind: 'System', icon: Shield }
    }
    return ret
}
</script>

<template>
    <div :class="['h-screen w-screen transition-colors', isDark ? 'bg-surface-900' : 'bg-surface-0']">
        <div class="fixed top-0 left-0 right-0 z-50 h-[64px]">
            <Header @toggle-nav="navOpen = !navOpen" />
        </div>
        <SideBar ref="sidebarRef" :mobile-open="navOpen" @close="navOpen = false">
            <template #scope="{ collapsed, group, toggle }">
                <SidebarScope v-bind="scopeFor(group)" :collapsed="collapsed" @toggle="toggle" />
            </template>
        </SideBar>
        <div
            :class="[
                'pt-[64px] h-full transition-all duration-300',
                isSidebarCollapsed ? 'md:pl-[73px]' : 'md:pl-[256px]'
            ]"
        >
            <div :class="['h-[calc(100vh-64px)] overflow-y-auto px-4 py-4 transition-colors md:px-8 md:py-6', isDark ? 'bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
                <!-- flex + flex-1 (rather than min-h-full on the page root) so short pages still
                     stretch to the bottom of the viewport inside this auto-height wrapper. -->
                <div class="flex min-h-full w-full flex-col">
                    <router-view class="min-w-0 flex-1" />
                </div>
            </div>
        </div>
    </div>
</template>
