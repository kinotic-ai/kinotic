<script setup lang="ts">
import { computed, markRaw, onMounted, ref, type Component } from 'vue'
import { Building2, LayoutGrid, Link, LogOut, Shield, User } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuItem } from 'primevue/menuitem'
import { Kinotic } from '@kinotic-ai/core'
import type { UserParticipantIdentity } from '@kinotic-ai/management-api'
import { avatarInitials, createDebug, ProjectsIcon, SideBar, SidebarScope, SidebarUserMenu } from '@kinotic-ai/frontend-common'
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

import Header from './Header.vue'
import { applicationPath, organizationPath } from '@/util/scope'
import { SYSTEM_USER_STATE } from '@/states/SystemUserState'

/** What the sidebar's scope block shows for the current route's sidebar group. */
interface SidebarScopeProps {
    name: string
    kind: string
    icon: Component
    backTo?: string
    backLabel?: string
}

const debug = createDebug('console-layout')

const sidebarRef = ref<InstanceType<typeof SideBar> | null>(null)
const route = useRoute()
const router = useRouter()

const profile = ref<UserParticipantIdentity | null>(null)
const profileName = computed(() => profile.value?.displayName ?? 'System operator')
const profileDetail = computed(() => profile.value?.email ?? SYSTEM_USER_STATE.connectedInfo?.participant?.id ?? '')
const initials = computed(() => avatarInitials(profile.value?.displayName, profile.value?.email))

// lucideIcon is rendered by SidebarUserMenu's item slot
const accountMenuItems: MenuItem[] = [
    { label: 'Connected apps', lucideIcon: markRaw(Link), command: () => router.push('/account/connected-apps') },
    { separator: true },
    { label: 'Log out', lucideIcon: markRaw(LogOut), command: logout }
]

onMounted(async () => {
    try {
        profile.value = await Kinotic.profile.findMyProfile()
    } catch (error) {
        // the participant id in profileDetail keeps the menu meaningful
        debug('Failed to load profile: %O', error)
    }
})

async function logout() {
    try {
        await SYSTEM_USER_STATE.logout()
    } finally {
        await router.push('/login')
    }
}

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
            <template #footer="{ collapsed }">
                <SidebarUserMenu :collapsed="collapsed" :name="profileName" :detail="profileDetail"
                                 :initials="initials" :items="accountMenuItems" />
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
