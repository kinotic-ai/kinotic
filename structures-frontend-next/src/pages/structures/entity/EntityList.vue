<template>
  <div>
    <Toolbar>
      <template #start>
        <InputText v-model="searchText" placeholder="Search" @keyup.enter="search" @focus="($event.target as HTMLInputElement)?.select()" />
        <Button icon="pi pi-times" class="ml-2" v-if="searchText" @click="clearSearch" />
      </template>
    </Toolbar>

    <DataTable :value="items" :loading="loading" :paginator="true" :rows="options.rows" :totalRecords="totalItems"
      :first="options.first" :lazy="true" :sortField="options.sortField" :sortOrder="options.sortOrder" @page="onPage"
      @sort="onSort" :scrollable="true" scrollHeight="flex" :resizableColumns="true" columnResizeMode="expand">
      <template v-if="headers.length > 0">
        <Column v-for="header in headers" :key="header.field" :field="header.field" :header="header.header"
          :sortable="header.sortable" :style="{ width: header.width + 'px' }"
          :class="[header.isCollapsable ? '!whitespace-normal' : '']">
          <template #body="slotProps">
            <div :class="[
              header.isCollapsable
                ? 'whitespace-normal break-words w-[400px] max-w-[400px] text-sm'
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

<script lang="ts">
import { Component, Vue, Prop } from 'vue-facing-decorator'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Toolbar from 'primevue/toolbar'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'

import { Pageable, type Page, Order, Direction, type Identifiable } from '@kinotic/continuum-client'
import { Structure, type IStructureService, Structures, type IEntitiesService } from '@kinotic/structures-api'

import DatetimeUtil from '@/util/DatetimeUtil'
import { StructureUtil } from '@/util/StructureUtil'

@Component({
  components: {
    DataTable,
    Column,
    Toolbar,
    Button,
    InputText
  }
})
export default class EntityList extends Vue {
  @Prop({ type: String }) structureId?: string

  loading = false
  finishedInitialLoad = false
  items: Array<Identifiable<string>> = []
  totalItems = 0
  searchText: string | null = null

  keys: string[] = []
  headers: any[] = []
  structureProperties: any = {}
  structure!: Structure

  entitiesService: IEntitiesService = Structures.getEntitiesService()
  structureService: IStructureService = Structures.getStructureService()

  options = {
    rows: 10,
    first: 0,
    sortField: '',
    sortOrder: 1
  }

  mounted() {
const paramId = this.$route.params.id
const id = this.structureId || (Array.isArray(paramId) ? paramId[0] : paramId)

    if (!id) {
      this.displayAlert("Missing structure ID.")
      return
    }

    this.structureService.findById(id)
      .then((structure) => {
        this.structure = structure
        this.structureProperties = structure.entityDefinition.properties
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
            const headerDef: any = {
              header: fieldName,
              field: property.name,
              sortable: sortable,
              width: property.name === 'id' ? 300 : (sortable ? 150 : 200),
              isCollapsable: property?.name === 'addresses' || property?.name === 'pet'
            }
            this.headers.push(headerDef)
            this.keys.push(property.name)
          }
        }

        this.find()
      })
      .catch((error) => {
        console.error(`Error during structure retrieval: ${error.message}`)
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
    alert(text)
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
</script>

<style scoped>
.p-datatable .p-button {
  margin-top: 1rem;
}
</style>