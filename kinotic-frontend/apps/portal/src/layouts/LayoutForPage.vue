<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { SideBar } from '@kinotic-ai/frontend-common'
import Header from './Header.vue'
import SidebarScope from '@/components/SidebarScope.vue'
import { isDark as darkMode } from '@kinotic-ai/frontend-common'
import { PROFILE_STATE } from '@/states/IProfileState'
import { USER_STATE } from '@/states/IUserState'

/** What the sidebar's scope block shows for the current route's sidebar group. */
interface SidebarScopeProps {
    name: string
    kind: string
    icon?: string
    initials?: string
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

const isFullWidth = computed(() => route.meta.fullWidth === true)

/**
 * Every scope's sidebar has the same shape: the row above the name leads to the parent
 * scope's landing page and is labelled with the parent's name, so the way up reads the
 * same whether the user is in an application, a project, or their account.
 */
function scopeFor(group: string | null): SidebarScopeProps {
    const organizationId = USER_STATE.getOrganizationId()
    const applicationId = route.params.applicationId as string | undefined
    const projectId = route.params.projectId as string | undefined
    let ret: SidebarScopeProps
    if (group === 'application' && applicationId) {
        ret = {
            name: applicationId,
            kind: 'Application',
            icon: 'pi-th-large',
            backTo: '/applications',
            backLabel: organizationId
        }
    } else if (group === 'project' && applicationId && projectId) {
        ret = {
            name: projectId,
            kind: 'Project',
            icon: 'pi-folder',
            backTo: `/application/${encodeURIComponent(applicationId)}`,
            backLabel: applicationId
        }
    } else if (group === 'account') {
        ret = {
            name: PROFILE_STATE.profile?.displayName ?? PROFILE_STATE.profile?.email ?? 'Account',
            kind: 'Account',
            initials: PROFILE_STATE.initials || undefined,
            icon: 'pi-user',
            backTo: '/applications',
            backLabel: organizationId
        }
    } else {
        ret = { name: organizationId, kind: 'Organization', icon: 'pi-building' }
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
            <template #scope="{ collapsed, group }">
                <SidebarScope v-bind="scopeFor(group)" :collapsed="collapsed" />
            </template>
        </SideBar>
        <div
            :class="[
                'pt-[64px] h-full transition-all duration-300',
                isSidebarCollapsed ? 'md:pl-[64px]' : 'md:pl-[256px]'
            ]"
        >
            <div :class="['h-[calc(100vh-64px)] overflow-y-auto px-4 py-4 transition-colors md:px-8 md:py-6', isDark ? 'bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
                <router-view v-if="isFullWidth" />
                <!-- h-full (not min-h-full) gives the page a definite height to divide up, so a
                     page that scrolls a region internally — a table keeping its paginator in
                     place — can size that region. min-h-0 lets the page shrink to it; taller
                     pages overflow and scroll in the wrapper above as before. -->
                <div v-else class="mx-auto flex h-full w-full max-w-[1200px] flex-col">
                    <router-view class="min-h-0 flex-1" />
                </div>
            </div>
        </div>
    </div>
</template>
