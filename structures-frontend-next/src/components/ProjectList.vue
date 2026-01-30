<script lang="ts">
import { Component, Vue, Prop, Ref, Watch } from 'vue-facing-decorator'
import CrudTable from '@/components/CrudTable.vue'
import NewProjectSidebar from '@/components/NewProjectSidebar.vue'
import ProjectStructuresTable from '@/components/ProjectStructuresTable.vue'
import type { IDataSource, Identifiable, IterablePage, Pageable } from '@kinotic/continuum-client'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { Project, Structures } from '@kinotic/structures-api'
import type { CrudHeader } from '@/types/CrudHeader'
import DatetimeUtil from "@/util/DatetimeUtil"

@Component({
  components: { CrudTable, NewProjectSidebar, ProjectStructuresTable }
})
export default class ProjectList extends Vue {
  @Prop({ required: true }) applicationId!: string

  @Ref('crudTable') crudTable!: InstanceType<typeof CrudTable>

  searchText: string = ''
  showProjectSidebar = false
  selectedProjectId: string | null = null
  isInitialized = false

  projectTableHeaders: CrudHeader[] = [
    { field: 'name', header: 'Project Name', sortable: true },
    { field: 'sourceOfTruth', header: 'Source of Truth', sortable: true },
    { field: 'description', header: 'Description', sortable: false },
    { field: 'updated', header: 'Updated', sortable: false }
  ]

  mounted() {
    this.searchText = (this.$route.query['search-project'] as string) || ''
    this.isInitialized = true
  }

  @Watch('$route.query.search-project')
  onSearchQueryChange(newVal: string) {
    if (this.isInitialized) {
      this.searchText = newVal || ''
      this.refreshTable()
    }
  }

  @Watch('applicationId')
  onAppChange() {
    this.refreshTable()
  }

  get dataSource(): IDataSource<Project> {
    return {
      findAll: async (pageable: Pageable): Promise<IterablePage<Project>> => {
        const result = await Structures.getProjectService().findAllForApplication(this.applicationId, pageable)
        APPLICATION_STATE.projectsCount = result.totalElements ?? 0
        return result
      },
      search: async (_searchText: string, pageable: Pageable): Promise<IterablePage<Project>> => {
        const search = `applicationId:${this.applicationId} && ${this.searchText}`
        return Structures.getProjectService().search(search, pageable)
      }
    }
  }

  get projectsCount() {
    return APPLICATION_STATE.projectsCount
  }
  public DatetimeUtil = DatetimeUtil
  refreshTable(): void {
    this.crudTable?.find?.()
  }

  updateRouteQuery(newSearch: string) {
    this.searchText = newSearch
    const newQuery = { ...this.$route.query }

    if (newSearch) {
      newQuery['search-project'] = newSearch
    } else {
      delete newQuery['search-project']
    }

    this.$router.replace({ query: newQuery }).catch(() => {})
    this.refreshTable()
  }

  onAddProject(): void {
    this.showProjectSidebar = true
  }

  onProjectSidebarClose(): void {
    this.showProjectSidebar = false
  }

  async onProjectSubmit(): Promise<void> {
    try {
      this.refreshTable()
    } catch (error) {
      console.error('[ProjectList] Refresh after project creation failed:', error)
      this.$toast.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to refresh project list.',
        life: 3000
      })
    } finally {
      this.showProjectSidebar = false
    }
  }

  onEditItem(item: Identifiable<string>): void {
    this.$router.push(`${this.$route.path}/edit/${item.id}`)
  }

  async toProjectPage(item: Identifiable<string>): Promise<void> {
    if (!item.id) return
    
    try {
      const appId = this.applicationId
      const projectId = item.id
      
      console.log('ProjectList: Navigating to project:', (item as any).name, 'ID:', projectId, 'App ID:', appId)
      
      await this.$router.push(`/application/${encodeURIComponent(appId)}/project/${encodeURIComponent(projectId)}/structures`)
    } catch (error) {
      console.error('[ProjectList] Failed to navigate to project page:', error)
    }
  }

  clearSelectedProject() {
    this.selectedProjectId = null
  }
}
</script>

<template>
  <div>
    <CrudTable
      v-if="!selectedProjectId"
      ref="crudTable"
      rowHoverColor=""
      :data-source="dataSource"
      :headers="projectTableHeaders"
      :singleExpand="false"
      :search="searchText"
      @update:search="updateRouteQuery"
      @add-item="onAddProject"
      @edit-item="onEditItem"
      @onRowClick="toProjectPage"
      createNewButtonText="New Project"
      emptyStateText="No projects yet"
      :isShowAddNew="true"
      class="!text-sm"
    >
      <template #item.id="{ item }">
        <span>{{ item.id }}</span>
      </template>
      <template #item.updated="{ item }">
        <span>
          {{ DatetimeUtil.formatRelativeDate(item.updated) }}
        </span>
      </template>
    </CrudTable>

    <div v-if="selectedProjectId" class="mt-6">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-semibold text-[#101010]">
          Structures for Project: {{ selectedProjectId }}
        </h2>
        <Button label="Back to Projects" icon="pi pi-arrow-left" @click="clearSelectedProject" />
      </div>
      <ProjectStructuresTable :projectId="selectedProjectId" />
    </div>

    <NewProjectSidebar
      :visible="showProjectSidebar"
      @close="onProjectSidebarClose"
      @submit="onProjectSubmit"
    />
  </div>
</template>
