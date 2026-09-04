<script setup lang="ts">
import { computed, ref } from 'vue'
import { SideBar } from '@kinotic-ai/frontend-common'
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

import Header from './Header.vue'

const sidebarRef = ref<InstanceType<typeof SideBar> | null>(null)

// Small screens keep the sidebar in a drawer the header's menu button opens
const navOpen = ref(false)

const isSidebarCollapsed = computed(() => {
    return sidebarRef.value?.collapsed ?? false
})

const isDark = computed(() => darkMode.value)
</script>

<template>
    <div :class="['h-screen w-screen transition-colors', isDark ? 'bg-surface-900' : 'bg-surface-0']">
        <div class="fixed top-0 left-0 right-0 z-50 h-[64px]">
            <Header @toggle-nav="navOpen = !navOpen" />
        </div>
        <SideBar ref="sidebarRef" :mobile-open="navOpen" @close="navOpen = false" />
        <div
            :class="[
                'pt-[64px] h-full transition-all duration-300',
                isSidebarCollapsed ? 'md:pl-[64px]' : 'md:pl-[256px]'
            ]"
        >
            <div :class="['h-[calc(100vh-64px)] overflow-y-auto px-4 py-4 transition-colors md:px-8 md:py-6', isDark ? 'bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
                <!-- flex + flex-1 (rather than min-h-full on the page root) so short pages still
                     stretch to the bottom of the viewport inside this auto-height wrapper. -->
                <div class="mx-auto flex min-h-full w-full max-w-[1200px] flex-col">
                    <router-view class="flex-1" />
                </div>
            </div>
        </div>
    </div>
</template>
