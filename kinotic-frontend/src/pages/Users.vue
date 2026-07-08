<template>
  <div class="pt-4">
    <CrudTable
      :headers="headers"
      :data-source="dataSource"
      :singleExpand="false"
      title="Users"
      subtitle=""
      @add-item="onAddItem"
      @edit-item="onEditItem"
      ref="crudTable"
    >
      <template #item.id="{ item }">
        <span>{{ item.id }}</span>
      </template>

      <template #item.description="{ item }">
        {{ item.description }}
      </template>

      <template #additional-actions="{ item }">
        <Button text :class="isDark ? '!text-surface-300 !bg-surface-900' : '!text-surface-700 !bg-surface-0'" title="OpenAPI">
          <RouterLink target="_blank" :to="'/scalar-ui.html?namespace=' + item.id">
            <svg width="20" height="20" viewBox="0 0 24 24">
              <path :d="icons.api" fill="currentColor" />
            </svg>
          </RouterLink>
        </Button>

        <Button text :class="isDark ? '!text-surface-300 !bg-surface-900' : '!text-surface-700 !bg-surface-0'" title="GraphQL">
          <RouterLink target="_blank" :to="'/gql-ui.html?namespace=' + item.id">
            <svg width="20" height="20" viewBox="0 0 24 24">
              <path :d="icons.graph" fill="currentColor" />
            </svg>
          </RouterLink>
        </Button>
      </template>
    </CrudTable>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CrudTable from '@/components/CrudTable.vue'
import { type Identifiable } from '@kinotic-ai/core'
import { Kinotic } from '@kinotic-ai/core'
import { type IApplicationService } from '@kinotic-ai/os-api'
import { mdiGraphql, mdiApi } from '@mdi/js'
import type { CrudHeader } from '@/types/CrudHeader'
import { createDebug } from '@/util/debug'
import { isDark as darkMode } from '@/composables/useTheme'

const debug = createDebug('users');

const headers: CrudHeader[] = [
  { field: 'id', header: 'Id', sortable: false },
  { field: 'name', header: 'Name', sortable: false },
  { field: 'surname', header: 'Surname', sortable: false },
]

const dataSource: IApplicationService = Kinotic.applications

const icons = {
  graph: mdiGraphql,
  api: mdiApi
}

const isDark = darkMode

const route = useRoute()
const router = useRouter()

const crudTable = ref<InstanceType<typeof CrudTable>>()

onMounted(async () => {
  try {
    refreshTable()

    if (route.query.created === 'true') {
      router.replace({ query: {} })
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Unknown error'
    debug('Auth or connection failed: %s', message)
  }
})

function refreshTable(): void {
  crudTable.value?.find()
}

function onAddItem(): void {
  router.push({ path: '/applications', query: { add: 'true' } })
}

function onEditItem(item: Identifiable<string>): void {
  router.push(`${route.path}/edit/${item.id}`)
}
</script>
