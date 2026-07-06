<script lang="ts">
import { Component, Vue, Prop, Ref, Watch } from 'vue-facing-decorator'
import CrudTable from '@/components/CrudTable.vue'
import NewProjectSidebar from '@/components/NewProjectSidebar.vue'
import ProjectStructuresTable from '@/components/ProjectStructuresTable.vue'
import type { IDataSource, Identifiable, IterablePage, Pageable } from '@kinotic-ai/core'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { Kinotic } from '@kinotic-ai/core'
import { Project, RepositoryConnectionStatus } from '@kinotic-ai/os-api'
import type { CrudHeader } from '@/types/CrudHeader'
import DatetimeUtil from "@/util/DatetimeUtil"
import { createDebug } from '@/util/debug'
import { isDark as darkMode } from '@/composables/useTheme'

const debug = createDebug('project-list');

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
    { field: 'repoConnectionStatus', header: 'Repository', sortable: false },
    { field: 'sourceOfTruth', header: 'Source of Truth', sortable: true },
    { field: 'description', header: 'Description', sortable: false },
    { field: 'created', header: 'Created', sortable: false },
    { field: 'updated', header: 'Updated', sortable: false }
  ]

  // Ids of projects whose repository initialization is currently being retried,
  // to drive the per-row Retry button's loading state.
  retryingIds: string[] = []

  public RepoStatus = RepositoryConnectionStatus

  mounted() {
    this.searchText = (this.$route.query['search-project'] as string) || ''
    this.isInitialized = true
    this.handleOpenNewProjectQuery()
  }

  /**
   * Honors the post-install handoff from `GitHubInstallCallback`. When the user
   * started a GitHub link from the new-project sidebar, the callback redirects
   * back here with `?openNewProject=1` so we re-open the sidebar automatically.
   */
  private handleOpenNewProjectQuery(): void {
    if (this.$route.query.openNewProject === '1') {
      this.showProjectSidebar = true
      const cleaned = { ...this.$route.query }
      delete cleaned.openNewProject
      this.$router.replace({ query: cleaned }).catch(() => {})
    }
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
        const result = await Kinotic.projects.findAllForApplication(this.applicationId, pageable)
        APPLICATION_STATE.projectsCount = result.totalElements ?? 0
        return result
      },
      search: async (_searchText: string, pageable: Pageable): Promise<IterablePage<Project>> => {
        const search = `applicationId:${this.applicationId} && ${this.searchText}`
        return Kinotic.projects.search(search, pageable)
      }
    }
  }

  get projectsCount() {
    return APPLICATION_STATE.projectsCount
  }

  get isDark() {
    return darkMode.value
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
      debug('Refresh after project creation failed: %O', error)
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
      
      debug('Navigating to project: %s, ID: %s, App ID: %s', (item as any).name, projectId, appId)
      
      await this.$router.push(`/application/${encodeURIComponent(appId)}/project/${encodeURIComponent(projectId)}/structures`)
    } catch (error) {
      debug('Failed to navigate to project page: %O', error)
    }
  }

  clearSelectedProject() {
    this.selectedProjectId = null
  }

  isRetrying(id: string | null): boolean {
    return id != null && this.retryingIds.includes(id)
  }

  /**
   * Re-runs repository initialization for a project left INITIALIZATION_FAILED
   * at create time, then refreshes the list so the updated status shows.
   */
  async retryRepoInit(project: Project): Promise<void> {
    if (!project.id) return
    this.retryingIds = [...this.retryingIds, project.id]
    try {
      await Kinotic.projects.retryRepoInitialization(project.id)
      this.$toast.add({
        severity: 'success',
        summary: 'Repository initialized',
        detail: `Initialization succeeded for ${project.name}.`,
        life: 3000
      })
      this.refreshTable()
    } catch (error) {
      debug('Retry repo initialization failed for %s: %O', project.id, error)
      this.$toast.add({
        severity: 'error',
        summary: 'Retry failed',
        detail: `Repository initialization failed again for ${project.name}.`,
        life: 5000
      })
    } finally {
      this.retryingIds = this.retryingIds.filter(id => id !== project.id)
    }
  }
}
</script>

<template>
  <div class="flex flex-1 flex-col">
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
      <template #item.repoConnectionStatus="{ item }">
        <div
          v-if="item.repoConnectionStatus === RepoStatus.INITIALIZATION_FAILED"
          class="flex items-center gap-2"
        >
          <Tag value="Init failed" severity="warn" />
          <Button
            label="Retry"
            size="small"
            :loading="isRetrying(item.id)"
            @click.stop="retryRepoInit(item)"
          />
        </div>
        <Tag
          v-else-if="item.repoConnectionStatus === RepoStatus.DISCONNECTED"
          value="Disconnected"
          severity="danger"
        />
      </template>
      <template #item.updated="{ item }">
        <span>
          {{ DatetimeUtil.formatRelativeDate(item.updated) }}
        </span>
      </template>
      <template #item.created="{ item }">
        <span>
          {{ DatetimeUtil.formatMonthDayYear(item.created) }}
        </span>
      </template>
    </CrudTable>

    <div v-if="selectedProjectId" class="mt-6">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-semibold" :class="isDark ? 'text-surface-0' : 'text-surface-950'">
          Entities for Project: {{ selectedProjectId }}
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
