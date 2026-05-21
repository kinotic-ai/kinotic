<script lang="ts">
import { Component, Vue, Watch } from 'vue-facing-decorator'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import SidebarItem from './SidebarItem.vue'
import strCollapse from '@/assets/str-collapse.svg'
import strExpand from '@/assets/str-expand.svg'
import { isDark as darkMode } from '@/composables/useTheme'

const COLLAPSE_KEY = 'sidebar-collapsed'

interface SidebarNavItem {
  icon: string
  label: string
  path: string
  section?: string
}

@Component({
  components: {
    SidebarItem
  }
})
export default class Sidebar extends Vue {
  collapsed = false
  sidebarItems: SidebarNavItem[] = []

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

  get isDark() {
    return darkMode.value
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

  get groupedSidebarItems(): Array<{ section: string; items: SidebarNavItem[] }> {
    const groups = new Map<string, SidebarNavItem[]>()

    this.sidebarItems.forEach((item) => {
      const section = item.section ?? ''
      const existing = groups.get(section)
      if (existing) {
        existing.push(item)
      } else {
        groups.set(section, [item])
      }
    })

    return Array.from(groups.entries()).map(([section, items]) => ({ section, items }))
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
        })) as SidebarNavItem[]
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
  <div class="fixed top-[64px] left-0 z-40 h-[calc(100vh-64px)] box-border"
    :class="[collapsed ? 'w-[75px]' : 'w-[256px]']">
    <div :class="[
      'app-sidebar-shell flex h-full flex-col justify-between transition-[width,background-color,border-color] duration-300 ease-in-out w-full box-border border-r',
      collapsed ? 'px-1 py-3 items-center' : 'px-4 py-4'
    ]">
      <div class="flex flex-col w-full gap-0" :class="collapsed ? 'justify-center items-center' : ''">
        <div
          v-for="group in groupedSidebarItems"
          :key="group.section || 'default'"
          :class="[
            'w-full',
            group.section === 'Organization'
              ? (collapsed ? 'mt-2 pt-2' : 'pt-3')
              : ''
          ]"
        >
          <div
            v-if="collapsed && group.section === 'Organization'"
            class="w-full px-[10px] pb-2"
          >
            <div class="app-surface-divider border-t" />
          </div>

          <div
            v-if="!collapsed && group.section"
            :class="[
              'app-sidebar-section-label mb-2 px-2 text-[11px] font-semibold uppercase tracking-[0.08em]'
            ]"
          >
            {{ group.section }}
          </div>

          <div class="flex flex-col w-full" :class="collapsed ? 'items-center' : ''">
            <SidebarItem
              v-for="item in group.items"
              :key="item.path"
              :icon="item.icon"
              :label="item.label"
              :collapsed="collapsed"
              :path="item.path"
              :isActive="isActive(item.path)"
              @click="navigateTo(item.path)"
            />
          </div>
        </div>
      </div>

      <div
        @click="toggleSidebar"
        :class="[
          'app-sidebar-toggle flex w-full items-center gap-2 cursor-pointer border-t px-2 py-3 transition-colors',
          collapsed ? 'justify-center' : 'justify-start'
        ]"
      >
        <img :style="{ width: '14px', height: '14px' }" :src="collapsed ? strExpand : strCollapse" alt="Toggle Sidebar" class="w-5 h-5 transition-transform duration-300" :class="[collapsed ? 'rotate-180' : '', isDark ? 'opacity-70 invert' : 'opacity-70']"/>
        <span v-if="!collapsed" class="text-sm font-medium">Collapse</span>
      </div>
    </div>
  </div>
</template>
