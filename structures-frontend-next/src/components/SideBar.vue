<script lang="ts">
import { Component, Vue, Watch } from 'vue-facing-decorator'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import SidebarItem from './SidebarItem.vue'
import strCollapse from '@/assets/str-collapse.svg'
import strExpand from '@/assets/str-expand.svg'

const COLLAPSE_KEY = 'sidebar-collapsed'

@Component({
  components: {
    SidebarItem
  }
})
export default class Sidebar extends Vue {
  collapsed = false
  sidebarItems: Array<{ icon: string; label: string; path: string }> = []

  strCollapse = strCollapse
  strExpand = strExpand

  mounted() {
    const stored = localStorage.getItem(COLLAPSE_KEY)
    this.collapsed = stored === 'true'

    this.generateSidebarItems()
  }

  @Watch('$route')
  onRouteChange() {
    this.generateSidebarItems()
  }

  get router() {
    return this.$router
  }

  get route(): RouteLocationNormalizedLoaded {
    return this.$route
  }

  toggleSidebar() {
    this.collapsed = !this.collapsed
    localStorage.setItem(COLLAPSE_KEY, String(this.collapsed))
  }

  navigateTo(path: string) {
    if (this.route.path !== path) {
      this.router.push(path) 
    }
  }

  generateSidebarItems() {
    const matchedWithSidebar = this.route.matched.find(r => r.meta?.sidebarItems)

    if (matchedWithSidebar) {
      const sidebarItemsMeta = matchedWithSidebar.meta.sidebarItems
      this.sidebarItems =
        typeof sidebarItemsMeta === 'function'
          ? sidebarItemsMeta(this.route)
          : sidebarItemsMeta || []
    } else {
      const allRoutes = this.router.getRoutes()
      this.sidebarItems = allRoutes
        .filter(r => r.meta?.showInMainNav)
        .map(r => ({
          icon: r.meta.icon,
          label: r.meta.label,
          path: r.path
        })) as Array<{ icon: string; label: string; path: string }>
    }
  }

  isActive(path: string): boolean {
    if (path.includes('/dashboards') && !path.includes('/dashboards/')) {
      return this.route.path === path || this.route.path.startsWith(path + '/')
    }
    return this.route.path === path
  }
}
</script>

<template>
  <div class="fixed rounded-xl top-[64px] left-0 z-40 h-[calc(100vh-67px)] pl-[8px] pt-[8px] pb-[8px] box-border"
    :class="[collapsed ? 'w-[75px]' : 'w-[256px]']">
    <div :class="['bg-surface-50 rounded-xl flex flex-col justify-between h-full', 'transition-[width] duration-300 ease-in-out w-full box-border', collapsed ? 'px-1 py-2 items-center' : 'px-2 py-2']">
      <div class="flex flex-col w-full" :class="collapsed ? 'justify-center items-center' : 'pl-[10px]'">
        <SidebarItem
          v-for="item in sidebarItems"
          :key="item.path"
          :icon="item.icon"
          :label="item.label"
          :collapsed="collapsed"
          :path="item.path"
          :isActive="isActive(item.path)"
          @click="navigateTo(item.path)"
        />
      </div>

      <div @click="toggleSidebar" class="cursor-pointer p-2 hover:bg-gray-200 rounded-lg transition w-max !pl-3">
        <img :style="{ width: '14px', height: '14px' }" :src="collapsed ? strExpand : strCollapse" alt="Toggle Sidebar" class="w-5 h-5 transition-transform duration-300" :class="collapsed ? 'rotate-180' : ''"/>
      </div>
    </div>
  </div>
</template>
