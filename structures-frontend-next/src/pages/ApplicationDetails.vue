<script lang="ts">
import { Component, Vue, Watch } from 'vue-facing-decorator'
import ProjectList from '@/components/ProjectList.vue'
import StructuresList from '@/components/StructuresList.vue'
import GraphQLModal from '@/components/modals/GraphQLModal.vue'
import OpenAPIModal from '@/components/modals/OpenAPIModal.vue'
import StructureItemModal from '@/components/modals/StructureItemModal.vue'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import { APPLICATION_STATE } from '@/states/IApplicationState'

@Component({
  components: {
    ProjectList,
    StructuresList,
    GraphQLModal,
    OpenAPIModal,
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
  showGraphQLModal: boolean = false
  showOpenAPIModal: boolean = false
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

  get currentApplication() {
    return APPLICATION_STATE.currentApplication
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

  openGraphQL(): void {
    this.showGraphQLModal = true
  }

  closeGraphQL(): void {
    this.showGraphQLModal = false
  }

  openOpenAPI(): void {
    this.showOpenAPIModal = true
  }

  closeOpenAPI(): void {
    this.showOpenAPIModal = false
  }
}
</script>

<template>
  <div class="p-10">
    <div class="flex justify-between items-center mb-6 h-[58px]">
      <div>
        <h1 class="font-semibold text-2xl text-surface-950 mb-3">{{ applicationId }}</h1>
        <span>{{ projectsCount }} projects, {{ structuresCount }} structures</span>
      </div>
      <div class="flex gap-3 h-full">
        <div
          v-if="currentApplication?.enableGraphQL"
          @click="openGraphQL"
          class="border border-surface-200 rounded-xl flex items-center gap-2 px-16 cursor-pointer"
        >
          <img src="@/assets/graphql.svg" class="w-6 h-6" />
          <span class="text-sm font-semibold">GraphQL</span>
        </div>
        <div 
          v-if="currentApplication?.enableOpenAPI"
          @click="openOpenAPI"
          class="border border-surface-200 rounded-xl flex items-center gap-2 px-16 cursor-pointer"
        >
          <img src="@/assets/scalar.svg" class="w-6 h-6" />
          <span class="text-sm font-semibold">OpenAPI</span>
        </div>
      </div>
    </div>

    <Tabs :value="activeTab" @update:value="activeTab = $event">
      <TabList>
        <Tab :value="0">Projects</Tab>
        <Tab :value="1">Structures</Tab>
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

    <GraphQLModal :visible="showGraphQLModal" @close="closeGraphQL" />
    <OpenAPIModal :visible="showOpenAPIModal" @close="closeOpenAPI" />
  </div>
</template>
<style>
.p-tabpanels {
  padding-left: 0 !important;
  padding-right: 0 !important;
}
.p-tab {
  padding-left: 0 !important;
}
</style>