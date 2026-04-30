<script lang="ts">
import { Component, Vue, Prop } from 'vue-facing-decorator'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Toolbar from 'primevue/toolbar'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import { isDark as darkMode } from '@/composables/useTheme'

import { Pageable, type Page, Order, Direction, type Identifiable } from '@kinotic-ai/core'
import { Kinotic } from '@kinotic-ai/core'
import { EntityDefinition, type IEntityDefinitionService } from '@kinotic-ai/os-api'
import { type IEntitiesRepository } from '@kinotic-ai/persistence'

import DatetimeUtil from '@/util/DatetimeUtil'
import { StructureUtil } from '@/util/StructureUtil'
import { createDebug } from '@/util/debug'

const debug = createDebug('entity-list-old');

@Component({
  components: {
    DataTable,
    Column,
    Toolbar,
    Button,
    InputText
  }
})
class EntityList extends Vue {
  @Prop({ type: String }) structureId?: string

  loading = false
  finishedInitialLoad = false
  items: Array<Identifiable<string>> = []
  totalItems = 0
  searchText: string | null = null

  keys: string[] = []
  headers: any[] = []
  structureProperties: any = {}
  structure!: EntityDefinition

  entitiesService: IEntitiesRepository = Kinotic.entities
  structureService: IEntityDefinitionService = Kinotic.entityDefinitions

  options = {
    rows: 10,
    first: 0,
    sortField: '',
    sortOrder: 1
  }

  get isDark(): boolean {
    return darkMode.value
  }

  mounted() {
const paramId = this.$route.params.id
const id = this.structureId || (Array.isArray(paramId) ? paramId[0] : paramId)

if (!id) {
      this.displayAlert("Missing entity ID.")
      return
    }

    this.structureService.findById(id)
      .then((structure: EntityDefinition) => {
        this.structure = structure
        this.structureProperties = structure.schema.properties
        for (const property of this.structureProperties) {
          if (property) {
            const fieldName = property.name[0].toUpperCase() + property.name.slice(1)
            let sortable = true
            if (
              ['ref', 'array', 'object'].includes(property.type.type) ||
              (property.type.type === 'string' && StructureUtil.hasDecorator('Text', property.decorators))
            ) {
              sortable = false
            }
            const isComplex = ['ref', 'array', 'object'].includes(property.type.type)
            const headerDef: any = {
              header: fieldName,
              field: property.name,
              sortable: sortable,
              width: property.name === 'id' ? 220 : (isComplex ? 240 : (sortable ? 120 : 160)),
              isCollapsable: isComplex || property?.name === 'addresses' || property?.name === 'pet'
            }
            this.headers.push(headerDef)
            this.keys.push(property.name)
          }
        }

        this.find()
      })
      .catch((error: Error) => {
        debug('Error during structure retrieval: %O', error)
        this.displayAlert(error.message)
      })
  }

  formatDate(date: string): string {
    return DatetimeUtil.formatDate(date)
  }

  isDateField(field: string): boolean {
    return StructureUtil.getPropertyDefinition(field, this.structureProperties)?.type?.type === 'date'
  }

  onPage(event: any) {
    this.options.rows = event.rows
    this.options.first = event.first
    this.find()
  }

  onSort(event: any) {
    this.options.sortField = event.sortField
    this.options.sortOrder = event.sortOrder
    this.find()
  }

  clearSearch() {
    this.searchText = null
    this.options.first = 0
    this.find()
  }
  
  search() {
    this.options.first = 0
    this.find()
  }

  displayAlert(text: string) {
    debug('Display alert: %s', text)
    // alert(text)
  }

  find() {
    if (this.loading) return

    this.loading = true

    const page = this.options.first / this.options.rows
    const orders: Order[] = []
    
    if (this.options.sortField) {
      orders.push(new Order(this.options.sortField, this.options.sortOrder === 1 ? Direction.ASC : Direction.DESC))
    }

    const pageable = Pageable.create(page, this.options.rows, { orders })
    const paramId = this.$route.params.id
    const id = this.structureId || (Array.isArray(paramId) ? paramId[0] : paramId)
    
    const queryPromise = (this.searchText?.length)
    ? this.entitiesService.search(id, this.searchText, pageable)
    : this.entitiesService.findAll(id, pageable)

    queryPromise
    .then((page: Page<any>) => {
        this.items = page.content ?? []
        this.totalItems = page.totalElements ?? 0
        this.loading = false
        
        if (!this.finishedInitialLoad) {
          setTimeout(() => { this.finishedInitialLoad = true }, 500)
        }
      })
      .catch((error: any) => {
        this.displayAlert(error.message)
        this.loading = false
        if (!this.finishedInitialLoad) {
          setTimeout(() => { this.finishedInitialLoad = true }, 500)
        }
      })
  }
}

export default EntityList
</script>

<template>
  <div :class="['entity-list-old w-full overflow-y-auto', isDark ? 'entity-list-old--dark bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
    <Toolbar :class="['!w-full', isDark ? '!border-surface-700 !bg-surface-900 !text-surface-0' : '']">
      <template #start>
        <InputText
          v-model="searchText" 
          placeholder="Search" 
          @keyup.enter="search" 
          @focus="($event.target as HTMLInputElement)?.select()"
          :class="['w-1/2 !shadow-none', isDark ? 'border-surface-600 !bg-surface-950 !text-surface-0 placeholder:!text-surface-400' : '']"
        />
        <Button icon="pi pi-times" class="ml-2" v-if="searchText" @click="clearSearch" />
      </template>
    </Toolbar>

    <DataTable :value="items" :loading="loading" :paginator="true" :rows="options.rows" :totalRecords="totalItems"
      :class="isDark ? 'entity-list-old__table' : ''"
      :first="options.first" :lazy="true" :sortField="options.sortField" :sortOrder="options.sortOrder" @page="onPage"
      @sort="onSort" :scrollable="true" scrollHeight="flex" :resizableColumns="true" columnResizeMode="expand">
      <template v-if="headers.length > 0">
        <Column v-for="header in headers" :key="header.field" :field="header.field" :header="header.header"
          :sortable="header.sortable" :style="{ width: header.width + 'px' }"
          :class="[header.isCollapsable ? '!whitespace-normal' : '']">
          <template #body="slotProps">
            <div :class="[
              header.isCollapsable
                ? 'whitespace-normal break-words w-[240px] max-w-[240px] text-sm'
                : 'truncate'
            ]">
              <span v-if="typeof slotProps.data[header.field] === 'object'">
                {{ JSON.stringify(slotProps.data[header.field]) }}
              </span>
              <span v-else>
                {{ isDateField(header.field)
                  ? formatDate(slotProps.data[header.field])
                  : slotProps.data[header.field]
                }}
              </span>
            </div>
          </template>

        </Column>
      </template>

      <template v-if="items.length === 0">
        <div class="p-4 text-center">
          <Button label="No Data - Push To Search Again" @click="find" v-if="!loading" />
        </div>
      </template>
    </DataTable>
  </div>
</template>
<style>
.p-datatable .p-button {
  margin-top: 1rem;
}
.p-toolbar-start {
  width: 100% !important;
}

.entity-list-old--dark .p-datatable,
.entity-list-old--dark .p-datatable-wrapper,
.entity-list-old--dark .p-datatable-table-container,
.entity-list-old--dark .p-datatable-table,
.entity-list-old--dark .p-datatable-header,
.entity-list-old--dark .p-paginator {
  background: var(--p-surface-900) !important;
  color: var(--p-surface-0) !important;
}

.entity-list-old--dark .p-datatable-thead > tr > th {
  background: var(--p-surface-950) !important;
  border-color: var(--p-surface-700) !important;
  color: var(--p-surface-100) !important;
}

.entity-list-old--dark .p-datatable-tbody > tr,
.entity-list-old--dark .p-datatable-tbody > tr > td {
  background: var(--p-surface-900) !important;
  border-color: var(--p-surface-700) !important;
  color: var(--p-surface-100) !important;
}

.entity-list-old--dark .p-datatable-tbody > tr:hover,
.entity-list-old--dark .p-datatable-tbody > tr:hover > td {
  background: var(--p-surface-800) !important;
}

.entity-list-old--dark .p-paginator .p-paginator-page,
.entity-list-old--dark .p-paginator .p-paginator-next,
.entity-list-old--dark .p-paginator .p-paginator-prev,
.entity-list-old--dark .p-paginator .p-paginator-first,
.entity-list-old--dark .p-paginator .p-paginator-last {
  color: var(--p-surface-200) !important;
}
</style>