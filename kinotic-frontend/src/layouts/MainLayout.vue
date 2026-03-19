<template>
    <div class="h-screen bg-surface-0  w-full">
        <!-- <div id="app-sidebar" class="h-full hidden lg:block lg:sticky absolute left-0 top-0 z-50">
            <div :class="[
                isCollapsed ? 'w-[72px] px-3' : 'w-[258px] px-8',
                'h-full flex flex-col py-6 transition-all duration-300 ease-in-out'
            ]" style="background-color: #101010">
                <div :class="[isCollapsed ? 'flex-col items-center justify-center gap-4' : 'flex-row']" class="mb-10 flex justify-between">
                    <RouterLink to="/">
                        <img src="@/assets/sidebar-logo.svg" width="20" height="16" />
                    </RouterLink>
                    <Button class="!p-0" variant="link" @click="isCollapsed = !isCollapsed">
                        <img src="@/assets/collapse.svg" />
                    </Button>
                </div>
                <div class="flex-1">
                    <SideNav 
                        class="flex flex-col gap-2 overflow-hidden" 
                        :nav-items="applicationState.mainNavItems"
                        v-model="applicationState.selectedNavItem" 
                        :collapsed="isCollapsed" />
                </div>
                <div :class="[isCollapsed ? 'justify-center items-center' : '']" class="flex items-center gap-3 cursor-pointer mt-5">
                    <Avatar
                        image="https://fqjltiegiezfetthbags.supabase.co/storage/v1/render/image/public/block.images/blocks/avatars/circle/avatar-f-1.png" />
                    <div v-if="!isCollapsed">
                        <div class="text-sm font-semibold text-surface-0">Amy Elsner</div>
                        <span class="text-xs text-surface-400 leading-none">Description</span>
                    </div>
                </div>
            </div>
        </div> -->
        <Header />
        <div class="py-10"
        >
        <!-- :style="[isCollapsed ? {'width' :'calc(100% - 72px)'}: {'width' :'calc(100% - 250px)'}]" -->
            <!-- <MainLayoutBreadcrumb separator-icon="pi pi-chevron-right" /> -->
            <div class="flex-1 px-[33px] overflow-y-auto">
                <router-view />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
// import SideNav from '@/components/SideNav.vue'
import Header from '@/layouts/Header.vue'
// import { StructuresStates } from '@/states/index.js'
// import Avatar from 'primevue/avatar'
// import MainLayoutBreadcrumb from './MainLayoutBreadcrumb.vue'
// import { ref } from 'vue'

// const isCollapsed = ref(false)

// const applicationState = StructuresStates.getApplicationState()

</script>
