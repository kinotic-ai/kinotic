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
        <Button text class="!text-[#334155] !bg-white" title="OpenAPI">
          <RouterLink target="_blank" :to="'/scalar-ui.html?namespace=' + item.id">
            <svg width="20" height="20" viewBox="0 0 24 24">
              <path :d="icons.api" fill="currentColor" />
            </svg>
          </RouterLink>
        </Button>

        <Button text class="!text-[#334155] !bg-white" title="GraphQL">
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

<script lang="ts">
import { Component, Vue } from 'vue-facing-decorator'
import CrudTable from '@/components/CrudTable.vue'
import { type Identifiable } from '@kinotic/continuum-client'
import { Structures, type IApplicationService } from '@kinotic/structures-api'
import { mdiGraphql, mdiApi } from '@mdi/js'
import type { CrudHeader } from '@/types/CrudHeader'

@Component({
  components: { CrudTable }
})
export default class Users extends Vue {
  headers: CrudHeader[] = [
    { field: 'id', header: 'Id', sortable: false },
    { field: 'name', header: 'Name', sortable: false },
    { field: 'surname', header: 'Surname', sortable: false },
  ]

  dataSource: IApplicationService = Structures.getApplicationService()

  icons = {
    graph: mdiGraphql,
    api: mdiApi
  }

  async mounted(): Promise<void> {
    try {
      this.refreshTable()

      if (this.$route.query.created === 'true') {
        this.$router.replace({ query: {} })
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Unknown error'
      console.error('[UsersList] Auth or connection failed:', message)
    }
  }

  private refreshTable(): void {
    const tableRef = this.$refs.crudTable as InstanceType<typeof CrudTable> | undefined
    tableRef?.find()
  }

  onAddItem(): void {
    this.$router.push('/application-add')
  }

  onEditItem(item: Identifiable<string>): void {
    this.$router.push(`${this.$route.path}/edit/${item.id}`)
  }
}
</script>
