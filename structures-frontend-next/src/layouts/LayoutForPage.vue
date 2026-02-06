<script setup lang="ts">
import { ref, computed } from 'vue'
import SideBar from '@/components/SideBar.vue'
import Header from './Header.vue'

const sidebarRef = ref<InstanceType<typeof SideBar> | null>(null)

const isSidebarCollapsed = computed(() => {
    return sidebarRef.value?.collapsed ?? false
})

</script>

<template>
    <div class="h-screen w-screen bg-white">
        <div class="fixed top-0 left-0 right-0 h-[64px] bg-white z-50">
            <Header />
        </div>
        <SideBar ref="sidebarRef" />
        <div
            :class="[
                'pt-[64px] h-full transition-all duration-300',
                isSidebarCollapsed ? 'pl-[64px]' : 'pl-[256px]'
            ]"
        >
            <div class="h-[calc(100vh-64px)] overflow-y-auto px-8 py-6">
                <router-view />
            </div>
        </div>
    </div>
</template>
