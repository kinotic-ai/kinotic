<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SidebarItem from './SidebarItem.vue'
import type { SidebarItemMeta } from '../types/SidebarItemMeta'

const COLLAPSE_KEY = 'sidebar-collapsed'

interface SidebarNavItem {
  icon: Component
  label: string
  path: string
  section?: string
}

/**
 * On small screens the sidebar is a drawer the header opens; {@code mobileOpen} slides it in
 * and {@code close} fires when the user picks an item or taps outside it. On wider screens
 * the drawer is always docked and those two are inert.
 */
const props = defineProps<{
  mobileOpen?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const route = useRoute()
const router = useRouter()

const collapsed = ref(false)
// shallow, so the icon components the items carry are not wrapped in reactive proxies
const sidebarItems = shallowRef<SidebarNavItem[]>([])
/** The sidebar group the current route resolved to; null when falling back to the main nav. */
const group = ref<string | null>(null)

// The drawer always shows labels: the collapsed state only applies once it is docked
const compact = computed(() => collapsed.value && !props.mobileOpen)

onMounted(() => {
  const stored = localStorage.getItem(COLLAPSE_KEY)
  collapsed.value = stored === 'true'
})

watch(route, () => {
  generateSidebarItems()
  emit('close')
}, { immediate: true })

/** Collapses or expands the docked sidebar; in the small-screen drawer it closes the drawer. */
function toggleSidebar() {
  if (props.mobileOpen) {
    emit('close')
  } else {
    collapsed.value = !collapsed.value
    localStorage.setItem(COLLAPSE_KEY, String(collapsed.value))
  }
}

function navigateTo(path: string) {
  if (route.path !== path) {
    router.push(path)
  } else {
    emit('close')
  }
}

const groupedSidebarItems = computed((): Array<{ section: string; items: SidebarNavItem[] }> => {
  const groups = new Map<string, SidebarNavItem[]>()

  sidebarItems.value.forEach((item) => {
    const section = item.section ?? ''
    const existing = groups.get(section)
    if (existing) {
      existing.push(item)
    } else {
      groups.set(section, [item])
    }
  })

  return Array.from(groups.entries()).map(([section, items]) => ({ section, items }))
})

/**
 * Builds the sidebar from route meta. The active group comes from the matched route
 * chain ({@code meta.sidebarGroup} on layout routes, or the route's own
 * {@code meta.sidebar.group}); items are every registered route declaring a
 * {@link SidebarItemMeta} for that group, ordered by {@code order}, with the current
 * route's params substituted into dynamic paths. A route outside every group gets an
 * empty sidebar.
 */
function generateSidebarItems() {
  const matched = route.matched.find(
      r => r.meta?.sidebarGroup || (r.meta?.sidebar as SidebarItemMeta | undefined)?.group)
  group.value = matched
      ? (matched.meta.sidebarGroup as string | undefined) ?? (matched.meta.sidebar as SidebarItemMeta).group
      : null

  const itemsByPath = new Map<string, SidebarNavItem & { order: number }>()
  for (const record of router.getRoutes()) {
    const meta = record.meta?.sidebar as SidebarItemMeta | undefined
    if (group.value === null || meta?.group !== group.value) continue
    const path = resolvePath(record.path)
    itemsByPath.set(path, { icon: meta.icon, label: meta.label, path, section: meta.section, order: meta.order })
  }
  sidebarItems.value = Array.from(itemsByPath.values())
      .sort((a, b) => a.order - b.order)
      .map(({ icon, label, path, section }) => ({ icon, label, path, section }))
}

/** Substitutes the current route's params into a route record path (e.g. :applicationId). */
function resolvePath(path: string): string {
  return path.replace(/:([A-Za-z0-9_]+)/g, (token, name) => {
    const value = route.params[name]
    return value != null ? String(value) : token
  })
}

/**
 * The item the current route falls under: the longest item path the route sits inside.
 * A detail route keeps its section highlighted (/jobs/<id> lights up Jobs) while a
 * deeper item still wins over the shallower one it nests under.
 */
const activePath = computed((): string | null => {
  let ret: string | null = null
  for (const item of sidebarItems.value) {
    if (containsRoute(item.path) && (ret === null || item.path.length > ret.length)) {
      ret = item.path
    }
  }
  return ret
})

// Compares whole segments so /jobs does not swallow a sibling like /jobs-archive
function containsRoute(itemPath: string): boolean {
  return route.path === itemPath || route.path.startsWith(itemPath + '/')
}

function isActive(path: string): boolean {
  return activePath.value === path
}

defineExpose({ collapsed })
</script>

<template>
  <div>
    <div
      v-if="mobileOpen"
      class="fixed inset-x-0 bottom-0 top-[64px] z-30 bg-surface-950/45 md:hidden"
      @click="emit('close')"
    />

    <div
      class="fixed top-[64px] left-0 z-40 h-[calc(100vh-64px)] w-[min(85vw,320px)] box-border transition-transform duration-200 md:translate-x-0"
      :class="[
        collapsed ? 'md:w-[73px]' : 'md:w-[256px]',
        mobileOpen ? 'translate-x-0 shadow-xl' : '-translate-x-full'
      ]"
    >
      <div :class="[
        'app-sidebar-shell flex h-full flex-col justify-between transition-[width,background-color,border-color] duration-300 ease-in-out w-full box-border border-r',
        compact ? 'px-1 py-3 items-center' : 'px-4 py-4'
      ]">
        <div class="flex min-h-0 w-full flex-1 flex-col overflow-y-auto">
          <!-- The app names the owner of this sidebar (the organization, application, ...)
               and the way up to its parent; the items below are the owner's sections.
               toggle collapses the sidebar, so the scope block can offer it. -->
          <slot name="scope" :collapsed="compact" :group="group" :toggle="toggleSidebar" />

          <div class="flex flex-col w-full gap-0" :class="compact ? 'justify-center items-center' : ''">
            <div
              v-for="(section, sectionIndex) in groupedSidebarItems"
              :key="section.section || 'default'"
              :class="[
                'w-full',
                sectionIndex > 0 && section.section
                  ? (compact ? 'mt-2 pt-2' : 'pt-3')
                  : ''
              ]"
            >
              <div
                v-if="compact && sectionIndex > 0 && section.section"
                class="w-full px-[10px] pb-2"
              >
                <div class="app-surface-divider border-t" />
              </div>

              <div
                v-if="!compact && section.section"
                :class="[
                  'app-sidebar-section-label mb-2 px-2 text-[11px] font-semibold uppercase tracking-[0.08em]'
                ]"
              >
                {{ section.section }}
              </div>

              <div class="flex flex-col w-full" :class="compact ? 'items-center' : ''">
                <SidebarItem
                  v-for="item in section.items"
                  :key="item.path"
                  :icon="item.icon"
                  :label="item.label"
                  :collapsed="compact"
                  :path="item.path"
                  :isActive="isActive(item.path)"
                  @click="navigateTo(item.path)"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Pinned below the items, e.g. the signed-in user's menu -->
        <div v-if="$slots.footer" class="app-surface-divider w-full shrink-0 border-t pt-2">
          <slot name="footer" :collapsed="compact" />
        </div>
      </div>
    </div>
  </div>
</template>
