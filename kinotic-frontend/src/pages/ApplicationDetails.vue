<script lang="ts">
import { Component, Vue, Watch } from 'vue-facing-decorator'
import ProjectList from '@/components/ProjectList.vue'
import StructuresList from '@/components/StructuresList.vue'
import StructureItemModal from '@/components/modals/StructureItemModal.vue'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { isDark as darkMode } from '@/composables/useTheme'

@Component({
  components: {
    ProjectList,
    StructuresList,
    StructureItemModal,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel
  }
})
export default class ApplicationDetails extends Vue {
  activeTab: string | number  = 0
  isInitialized: boolean = false

  get applicationId(): string {
    return APPLICATION_STATE.currentApplication?.id || ''
  }

  get projectsCount(): number {
    return APPLICATION_STATE.projectsCount ?? 0
  }

  get structuresCount(): number {
    return APPLICATION_STATE.structuresCount ?? 0
  }

  get isDark() {
    return darkMode.value
  }

  get searchProduct(): string | undefined {
    return this.$route.query['search-project'] as string | undefined
  }

  get searchStructure(): string | undefined {
    return this.$route.query['search-structure'] as string | undefined
  }

  get activeTabFromQuery(): number {
    const query = this.$route.query
    if ('tab' in query) {
      const parsed = parseInt(query.tab as string)
      return isNaN(parsed) ? 0 : parsed
    }
    return 0
  }

  created() {
    this.activeTab = this.activeTabFromQuery
    this.isInitialized = true
  }

  @Watch('$route.query', { immediate: true })
  onQueryChanged() {
    const tabFromQuery = this.activeTabFromQuery
    if (this.activeTab !== tabFromQuery) {
      this.activeTab = tabFromQuery
    }
  }

  @Watch('APPLICATION_STATE.currentApplication')
  onApplicationChange() {
  }

  @Watch('activeTab')
  onTabChanged(newTab: number) {
    if (!this.isInitialized) return

    const query = { ...this.$route.query }
    query.tab = String(newTab)

    this.$router.replace({ query }).catch(() => {})
  }

}
</script>

<template>
  <div :class="['p-10 transition-colors', isDark ? 'text-white' : 'text-[#101010]']">
    <div class="flex justify-between items-center mb-6 h-[58px]">
      <div>
        <h1 :class="['mb-3 text-2xl font-semibold', isDark ? 'text-white' : 'text-surface-950']">{{ applicationId }}</h1>
        <span :class="[isDark ? 'text-[#a1a1aa]' : 'text-[#5f6165]']">{{ projectsCount }} projects, {{ structuresCount }} structures</span>
      </div>
    </div>

    <Tabs :value="activeTab" @update:value="activeTab = $event">
      <TabList>
        <Tab :value="0">
          <span class="flex items-center gap-2">
            <svg class="application-details-tab__icon h-4 w-4" viewBox="0 0 20 21" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
              <path fill-rule="evenodd" clip-rule="evenodd" d="M3.33333 3.30488C2.50208 3.30488 1.81818 3.98877 1.81818 4.82003V15.7291C1.81818 16.5604 2.50208 17.2443 3.33333 17.2443H16.6667C17.4979 17.2443 18.1818 16.5604 18.1818 15.7291V9.06245C18.1818 8.2312 17.4979 7.5473 16.6667 7.5473H10C9.73461 7.5473 9.48248 7.43133 9.30977 7.22984L5.94551 3.30488H3.33333ZM0 4.82003C0 2.98462 1.49792 1.48669 3.33333 1.48669H6.36364C6.62902 1.48669 6.88116 1.60266 7.05387 1.80416L10.4181 5.72912H16.6667C18.5021 5.72912 20 7.22704 20 9.06245V15.7291C20 17.5645 18.5021 19.0625 16.6667 19.0625H3.33333C1.49792 19.0625 0 17.5645 0 15.7291V4.82003Z" fill="currentColor"/>
            </svg>
            <span>Projects</span>
          </span>
        </Tab>
        <Tab :value="1">
          <span class="flex items-center gap-2">
            <svg class="application-details-tab__icon h-4 w-4" viewBox="0 0 20 21" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
              <path fill-rule="evenodd" clip-rule="evenodd" d="M2 0.274414C0.895431 0.274414 0 1.16984 0 2.27441V9.13156C0 10.2361 0.895431 11.1316 2 11.1316H6.57143C7.676 11.1316 8.57143 10.2361 8.57143 9.13156V2.27441C8.57143 1.16984 7.676 0.274414 6.57143 0.274414H2ZM1.71429 2.27441C1.71429 2.11662 1.8422 1.9887 2 1.9887H6.57143C6.72922 1.9887 6.85714 2.11662 6.85714 2.27441V9.13156C6.85714 9.28935 6.72922 9.41727 6.57143 9.41727H2C1.8422 9.41727 1.71429 9.28935 1.71429 9.13156V2.27441ZM2 13.9887C0.895431 13.9887 0 14.8841 0 15.9887V18.2744C0 19.379 0.895431 20.2744 2 20.2744H6.57143C7.676 20.2744 8.57143 19.379 8.57143 18.2744V15.9887C8.57143 14.8841 7.676 13.9887 6.57143 13.9887H2ZM1.71429 15.9887C1.71429 15.8309 1.8422 15.703 2 15.703H6.57143C6.72922 15.703 6.85714 15.8309 6.85714 15.9887V18.2744C6.85714 18.4322 6.72922 18.5601 6.57143 18.5601H2C1.8422 18.5601 1.71429 18.4322 1.71429 18.2744V15.9887ZM11.4286 11.4173C11.4286 10.3127 12.324 9.41727 13.4286 9.41727H18C19.1046 9.41727 20 10.3127 20 11.4173V18.2744C20 19.379 19.1046 20.2744 18 20.2744H13.4286C12.324 20.2744 11.4286 19.379 11.4286 18.2744V11.4173ZM13.4286 11.1316C13.2708 11.1316 13.1429 11.2595 13.1429 11.4173V18.2744C13.1429 18.4322 13.2708 18.5601 13.4286 18.5601H18C18.1578 18.5601 18.2857 18.4322 18.2857 18.2744V11.4173C18.2857 11.2595 18.1578 11.1316 18 11.1316H13.4286ZM13.4286 0.274414C12.324 0.274414 11.4286 1.16984 11.4286 2.27441V4.56013C11.4286 5.6647 12.324 6.56013 13.4286 6.56013H18C19.1046 6.56013 20 5.6647 20 4.56013V2.27441C20 1.16984 19.1046 0.274414 18 0.274414H13.4286ZM13.1429 2.27441C13.1429 2.11662 13.2708 1.9887 13.4286 1.9887H18C18.1578 1.9887 18.2857 2.11662 18.2857 2.27441V4.56013C18.2857 4.71792 18.1578 4.84584 18 4.84584H13.4286C13.2708 4.84584 13.1429 4.71792 13.1429 4.56013V2.27441Z" fill="currentColor"/>
            </svg>
            <span>Structures</span>
          </span>
        </Tab>
      </TabList>
      <TabPanels>
        <TabPanel :value="0">
          <div v-show="activeTab === 0">
            <ProjectList
              :applicationId="applicationId"
              :initialSearch="searchProduct"
            />
          </div>
        </TabPanel>
        <TabPanel :value="1">
          <div v-show="activeTab === 1">
            <StructuresList
              :applicationId="applicationId"
              :initialSearch="searchStructure"
            />
          </div>
        </TabPanel>
      </TabPanels>
    </Tabs>

  </div>
</template>
<style>
.p-tabpanels {
  padding-left: 0 !important;
  padding-right: 0 !important;
}
.p-tab {
  padding: 14px 15px !important;
}
.application-details-tab__icon {
  color: currentColor;
}
html.dark .p-tablist {
  border-bottom-color: #2f2f35 !important;
  background: transparent !important;
}
html.dark .p-tab {
  background: transparent !important;
  color: #9f9fa8 !important;
  border: none !important;
}
html.light .p-tab,
:root:not(.dark) .p-tab {
  border: none !important;
}
html.dark .p-tab.p-tab-active {
  color: #ffffff !important;
}
html.dark .p-tab.p-tab-active .application-details-tab__icon,
:root:not(.dark) .p-tab.p-tab-active .application-details-tab__icon {
  color: #EE3764 !important;
}
html.dark .p-tablist-active-bar {
  background: var(--p-primary-500) !important;
}
</style>