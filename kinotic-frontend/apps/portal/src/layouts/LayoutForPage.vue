<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { SideBar } from '@kinotic-ai/frontend-common'
import Header from './Header.vue'
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

const sidebarRef = ref<InstanceType<typeof SideBar> | null>(null)
const route = useRoute()

const isSidebarCollapsed = computed(() => {
    return sidebarRef.value?.collapsed ?? false
})

const isDark = computed(() => darkMode.value)

const isFullWidth = computed(() => route.meta.fullWidth === true)

</script>

<template>
    <div :class="['h-screen w-screen transition-colors', isDark ? 'bg-surface-900' : 'bg-surface-0']">
        <div class="fixed top-0 left-0 right-0 z-50 h-[64px]">
            <Header />
        </div>
        <SideBar ref="sidebarRef" />
        <div
            :class="[
                'pt-[64px] h-full transition-all duration-300',
                isSidebarCollapsed ? 'pl-[64px]' : 'pl-[256px]'
            ]"
        >
            <div :class="['h-[calc(100vh-64px)] overflow-y-auto px-8 py-6 transition-colors', isDark ? 'bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
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
