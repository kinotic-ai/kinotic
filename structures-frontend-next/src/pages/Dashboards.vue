<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { DashboardEntityService } from '@/services/DashboardEntityService'
import { Dashboard } from '@/domain/Dashboard'
import CrudTable from '@/components/CrudTable.vue'
import Button from 'primevue/button'
import Menu from 'primevue/menu'
import Dialog from 'primevue/dialog'
import { useToast } from 'primevue/usetoast'
import { type IDataSource, type IterablePage, Pageable } from '@mindignited/continuum-client'

const router = useRouter()
const toast = useToast()

const props = defineProps<{ applicationId?: string }>()

const currentApplicationId = computed(() => {
  return props.applicationId || APPLICATION_STATE.currentApplication?.id || ''
})

const title = computed(() => `Dashboards${currentApplicationId.value ? ` – ${currentApplicationId.value}` : ''}`)

const dashboardService = new DashboardEntityService()
const crudTableRef = ref<any>(null)
const tableKey = ref(0)
const actionMenus = ref<any[]>([])
const currentActionItem = ref<Dashboard | null>(null)
const showDeleteModal = ref(false)
const selectedDashboard = ref<Dashboard | null>(null)
const isDeleting = ref(false)

const dashboardDataSource: IDataSource<Dashboard> = {
  async findAll(pageable: Pageable): Promise<IterablePage<Dashboard>> {
    
    if (!currentApplicationId.value) {
      const emptyResult = await dashboardService.findAll(pageable)
      return {
        ...emptyResult,
        content: [],
        totalElements: 0
      }
    }
    
    try {
      const allPageable = Pageable.create(0, 10000)
      const result = await dashboardService.findAll(allPageable)
      const filteredDashboards = (result.content || []).filter(dashboard => {
        const matches = dashboard.applicationId === currentApplicationId.value
        return matches
      })
      return {
        ...result,
        content: filteredDashboards,
        totalElements: filteredDashboards.length
      }
    } catch (error) {
      const emptyResult = await dashboardService.findAll(pageable)
      return {
        ...emptyResult,
        content: [],
        totalElements: 0
      }
    }
  },
  
  async search(searchText: string, pageable: Pageable): Promise<IterablePage<Dashboard>> {
    
    if (!currentApplicationId.value) {
      const emptyResult = await dashboardService.findAll(pageable)
      return {
        ...emptyResult,
        content: [],
        totalElements: 0
      }
    }
    
    try {
      const allPageable = Pageable.create(0, 10000)
      const result = await dashboardService.findAll(allPageable)
      
      const filteredDashboards = (result.content || []).filter(dashboard => {
        const matchesApp = dashboard.applicationId === currentApplicationId.value
        const matchesSearch = dashboard.name?.toLowerCase().includes(searchText.toLowerCase()) ||
                             dashboard.description?.toLowerCase().includes(searchText.toLowerCase())
        return matchesApp && matchesSearch
      })
      
      
      return {
        ...result,
        content: filteredDashboards,
        totalElements: filteredDashboards.length
      }
    } catch (error) {
      const emptyResult = await dashboardService.findAll(pageable)
      return {
        ...emptyResult,
        content: [],
        totalElements: 0
      }
    }
  }
}


const tableHeaders = [
  { field: 'name', header: 'Name', sortable: true },
  { field: 'description', header: 'Description', sortable: true },
  { field: 'created', header: 'Created', sortable: true },
  { field: 'updated', header: 'Updated', sortable: true },
  { field: 'actions', header: '', sortable: false }
]

const openDeleteModal = (dashboard: Dashboard) => {
  selectedDashboard.value = dashboard
  showDeleteModal.value = true
}

const closeDeleteModal = () => {
  showDeleteModal.value = false
  selectedDashboard.value = null
  isDeleting.value = false
}

const deleteDashboard = async () => {
  if (!selectedDashboard.value?.id) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Dashboard ID is missing', life: 3000 })
    return
  }

  isDeleting.value = true

  try {
    
    // const _result = await dashboardService.deleteById(selectedDashboard.value.id)
    await dashboardService.deleteById(selectedDashboard.value.id)
    
    toast.add({ severity: 'success', summary: 'Success', detail: 'Dashboard deleted successfully', life: 3000 })
    
    tableKey.value++
    
    closeDeleteModal()
  } catch (error: any) {
    toast.add({ 
      severity: 'error', 
      summary: 'Error', 
      detail: error.message || error.statusText || 'Failed to delete dashboard',
      life: 3000
    })
  } finally {
    isDeleting.value = false
  }
}

const toggleMenu = (event: Event, item: Dashboard, index: number) => {
  currentActionItem.value = item
  const menu = actionMenus.value[index]
  if (menu) {
    menu.toggle(event)
  }
}

const getActionMenu = (item: Dashboard) => {
  return [
    {
      label: 'Delete',
      icon: 'pi pi-trash',
      command: () => openDeleteModal(item)
    }
  ]
}

const openDashboard = (dashboard: Dashboard) => {
  if (!dashboard.id) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Dashboard ID is missing', life: 3000 })
    return
  }
  
  if (!currentApplicationId.value) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Application ID is missing', life: 3000 })
    return
  }
  
  const route = `/application/${currentApplicationId.value}/dashboards/${dashboard.id}`
  router.push(route)
}

const createNewDashboard = () => {
  router.push(`/application/${currentApplicationId.value}/dashboards/new`)
}


watch(() => router.currentRoute.value.query.refresh, () => {
})

</script>

<template>
  <div class="p-6 h-full">
    <div class="flex justify-between items-center mb-6 border-b border-surface-200 pb-6">
      <h1 class="text-2xl font-semibold text-surface-950">{{ title }}</h1>
    </div>
    
    <div class="h-[calc(100vh-140px)]">
      <CrudTable
        :key="tableKey"
        ref="crudTableRef"
        :dataSource="dashboardDataSource"
        :headers="tableHeaders"
        :defaultPageSize="50"
        :isShowAddNew="true"
        :isShowDelete="true"
        @delete="deleteDashboard"
        @onRowClick="openDashboard"
        @add-item="createNewDashboard"
        createNewButtonText="New dashboard"
        row-clickable
      >
        <template #item.created="{ item }">
          {{ item.created ? new Date(item.created).toLocaleDateString() : '-' }}
        </template>
        <template #item.updated="{ item }">
          {{ item.updated ? new Date(item.updated).toLocaleDateString() : '-' }}
        </template>
        <template #item.actions="{ item }">
          <div class="flex items-center justify-center">
            <Button
              icon="pi pi-ellipsis-v"
              @click.stop="(event) => toggleMenu(event, item, item.id)"
              aria-haspopup="true"
              :aria-controls="'action_menu_' + item.id"
              type="button"
              severity="secondary"
              variant="text"
            />
            <Menu
              :ref="(el) => (actionMenus[item.id] = el)"
              :model="getActionMenu(item)"
              :popup="true"
              :id="'action_menu_' + item.id"
            />
          </div>
        </template>
      </CrudTable>
    </div>

    <Dialog
      v-model:visible="showDeleteModal"
      modal
      :style="{ width: '450px' }"
      :closable="false"
    >
      <template #header>
        <div class="flex items-center">
          <i class="pi pi-exclamation-triangle text-red-500 mr-3 text-lg"></i>
          <span class="text-lg font-semibold">Delete Dashboard</span>
        </div>
      </template>
      
      <div class="py-4">
        <p class="text-gray-700 mb-4">
          Are you sure you want to delete the dashboard 
          <strong>"{{ selectedDashboard?.name }}"</strong>?
        </p>
        <p class="text-sm text-gray-500">
          This action cannot be undone. All widgets and configurations will be permanently removed.
        </p>
      </div>

      <template #footer>
        <div class="flex justify-end gap-3">
          <Button
            label="Cancel"
            severity="secondary"
            @click="closeDeleteModal"
            :disabled="isDeleting"
            class="px-4 py-2"
          />
          <Button
            label="Delete"
            severity="danger"
            @click="deleteDashboard"
            :loading="isDeleting"
            class="px-4 py-2"
          />
        </div>
      </template>
    </Dialog>

  </div>
</template>


