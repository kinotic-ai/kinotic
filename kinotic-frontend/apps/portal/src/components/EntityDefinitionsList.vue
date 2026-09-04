<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Tag from 'primevue/tag'
import { Kinotic, type IDataSource, type IterablePage, type Pageable } from '@kinotic-ai/core'
import type { EntityDefinition } from '@kinotic-ai/management-api'
import { CrudTable, DatetimeUtil, type CrudHeader } from '@kinotic-ai/frontend-common'
import { useEntityPublishing } from '@/composables/entity-definition/useEntityPublishing'

/**
 * The entity definitions of an application, or of one of its projects when projectId is
 * given. A row opens the entity's page; the row menu publishes or unpublishes it.
 */
const props = defineProps<{
  applicationId: string
  projectId?: string
}>()

const route = useRoute()
const router = useRouter()

const crudTable = ref<InstanceType<typeof CrudTable>>()
const searchText = ref<string>((route.query['search-entityDefinition'] as string) || '')

const headers = computed<CrudHeader[]>(() => {
  let ret: CrudHeader[]
  if (props.projectId) {
    ret = [
      { field: 'name', header: 'Entity name', sortable: true, width: '23%' },
      { field: 'description', header: 'Description', sortable: false, width: '35%', optional: true },
      { field: 'created', header: 'Created', sortable: false, width: '14%', optional: true },
      { field: 'updated', header: 'Updated', sortable: false, width: '14%', optional: true },
      { field: 'published', header: 'Status', sortable: false, centered: true, width: '14%' }
    ]
  } else {
    ret = [
      { field: 'name', header: 'Entity name', sortable: true, width: '19%' },
      { field: 'projectId', header: 'Project', sortable: true, width: '16%' },
      { field: 'description', header: 'Description', sortable: false, width: '26%', optional: true },
      { field: 'created', header: 'Created', sortable: false, width: '13%', optional: true },
      { field: 'updated', header: 'Updated', sortable: false, width: '13%', optional: true },
      { field: 'published', header: 'Status', sortable: false, centered: true, width: '13%' }
    ]
  }
  return ret
})

const dataSource = computed<IDataSource<EntityDefinition>>(() => ({
  findAll: (pageable: Pageable): Promise<IterablePage<EntityDefinition>> => {
    return props.projectId
      ? Kinotic.entityDefinitions.findAllForProject(props.projectId, pageable)
      : Kinotic.entityDefinitions.findAllForApplication(props.applicationId, pageable)
  },
  search: (_searchText: string, pageable: Pageable): Promise<IterablePage<EntityDefinition>> => {
    const scope = props.projectId ? `projectId:${props.projectId}` : `applicationId:${props.applicationId}`
    return Kinotic.entityDefinitions.search(`${scope} && ${searchText.value}`, pageable)
  }
}))

const { rowActions } = useEntityPublishing(refreshTable)

function refreshTable(): void {
  crudTable.value?.find()
}

function updateRouteQuery(newSearch: string): void {
  searchText.value = newSearch
  const query = { ...route.query }
  if (newSearch) {
    query['search-entityDefinition'] = newSearch
  } else {
    delete query['search-entityDefinition']
  }
  router.replace({ query }).catch(() => {})
  refreshTable()
}

// The entity page opens in the scope of this list, so the sidebar and the way back stay put
function openEntity(item: EntityDefinition): void {
  const applicationPath = `/application/${encodeURIComponent(props.applicationId)}`
  const listPath = props.projectId
      ? `${applicationPath}/project/${encodeURIComponent(props.projectId)}/entities`
      : `${applicationPath}/entities`
  router.push(`${listPath}/${encodeURIComponent(item.id ?? '')}`)
}
</script>

<template>
  <div class="flex flex-1 flex-col">
    <CrudTable
      ref="crudTable"
      rowHoverColor=""
      :data-source="dataSource"
      :headers="headers"
      :singleExpand="false"
      :search="searchText"
      :isShowAddNew="false"
      :row-actions="rowActions"
      emptyStateText="No entities yet"
      class="!text-sm"
      @update:search="updateRouteQuery"
      @onRowClick="openEntity"
    >
      <template #item.created="{ item }">
        <span>{{ DatetimeUtil.formatMonthDayYear(item.created) }}</span>
      </template>

      <template #item.updated="{ item }">
        <span>{{ DatetimeUtil.formatRelativeDate(item.updated) }}</span>
      </template>

      <template #item.published="{ item }">
        <Tag
          :value="item.published ? 'Published' : 'Unpublished'"
          :severity="item.published ? 'success' : 'secondary'"
          class="px-2 py-1 text-sm"
          rounded
        />
      </template>
    </CrudTable>
  </div>
</template>
