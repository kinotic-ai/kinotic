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

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Toolbar from 'primevue/toolbar'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'

import { Pageable, type Page, Order, Direction, type Identifiable } from '@kinotic-ai/core'
import { Kinotic } from '@kinotic-ai/core'
import { EntityDefinition, type IEntityDefinitionService } from '@kinotic-ai/os-api'
import { type IEntitiesRepository } from '@kinotic-ai/persistence'

import DatetimeUtil from '@/util/DatetimeUtil'
import { EntityDefinitionUtil } from '@/util/EntityDefinitionUtil'
import { createDebug } from '@/util/debug'

const debug = createDebug('entity-list-entityDefinitions');

const props = defineProps<{
  entityDefinitionId?: string
}>()

const route = useRoute()

const loading = ref(false)
const finishedInitialLoad = ref(false)
const items = ref<Array<Identifiable<string>>>([])
const totalItems = ref(0)
const searchText = ref<string | null>(null)

const keys = ref<string[]>([])
const headers = ref<any[]>([])
const entityDefinitionProperties = ref<any>({})
const entityDefinition = ref<EntityDefinition>()

const entitiesService: IEntitiesRepository = Kinotic.entities
const entityDefinitionService: IEntityDefinitionService = Kinotic.entityDefinitions

const options = ref({
  rows: 10,
  first: 0,
  sortField: '',
  sortOrder: 1
})

onMounted(() => {
  const paramId = route.params.id
  const id = props.entityDefinitionId || (Array.isArray(paramId) ? paramId[0] : paramId)

  if (!id) {
    displayAlert("Missing entity ID.")
    return
  }

  entityDefinitionService.findById(id)
    .then((definition: EntityDefinition) => {
      entityDefinition.value = definition
      entityDefinitionProperties.value = definition.schema.properties
      for (const property of entityDefinitionProperties.value) {
        if (property) {
          const fieldName = property.name[0].toUpperCase() + property.name.slice(1)
          let sortable = true
          if (
            ['ref', 'array', 'object'].includes(property.type.type) ||
            (property.type.type === 'string' && EntityDefinitionUtil.hasDecorator('Text', property.decorators))
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
          headers.value.push(headerDef)
          keys.value.push(property.name)
        }
      }

      find()
    })
    .catch((error: Error) => {
      debug('Error during entityDefinition retrieval: %O', error)
      displayAlert(error.message)
    })
})

function formatDate(date: string): string {
  return DatetimeUtil.formatDate(date)
}

function isDateField(field: string): boolean {
  return EntityDefinitionUtil.getPropertyDefinition(field, entityDefinitionProperties.value)?.type?.type === 'date'
}

function onPage(event: any) {
  options.value.rows = event.rows
  options.value.first = event.first
  find()
}

function onSort(event: any) {
  options.value.sortField = event.sortField
  options.value.sortOrder = event.sortOrder
  find()
}

function clearSearch() {
  searchText.value = null
  options.value.first = 0
  find()
}

function search() {
  options.value.first = 0
  find()
}

function displayAlert(text: string) {
  alert(text)
}

function find() {
  if (loading.value) return

  loading.value = true

  const page = options.value.first / options.value.rows
  const orders: Order[] = []

  if (options.value.sortField) {
    orders.push(new Order(options.value.sortField, options.value.sortOrder === 1 ? Direction.ASC : Direction.DESC))
  }

  const pageable = Pageable.create(page, options.value.rows, { orders })
  const paramId = route.params.id
  const id = props.entityDefinitionId || (Array.isArray(paramId) ? paramId[0] : paramId)

  const queryPromise = (searchText.value?.length)
    ? entitiesService.search(id, searchText.value, pageable)
    : entitiesService.findAll(id, pageable)

  queryPromise
    .then((page: Page<any>) => {
      items.value = page.content ?? []
      totalItems.value = page.totalElements ?? 0
      loading.value = false

      if (!finishedInitialLoad.value) {
        setTimeout(() => { finishedInitialLoad.value = true }, 500)
      }
    })
    .catch((error: any) => {
      displayAlert(error.message)
      loading.value = false
      if (!finishedInitialLoad.value) {
        setTimeout(() => { finishedInitialLoad.value = true }, 500)
      }
    })
}
</script>

<style scoped>
.p-datatable .p-button {
  margin-top: 1rem;
}
</style>