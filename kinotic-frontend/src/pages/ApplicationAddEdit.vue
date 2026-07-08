<template>
  <CrudEntityAddEdit :crud-service-identifier="crudServiceIdentifier" title="Application" :identity="id"
    :identityRules="applicationRules" :entity.sync="application" update:entity="handleEntityUpdate">
    <template #basic-info="{ entity }">
      <div class="mb-4">
        <label for="description" class="block text-sm font-medium text-gray-700 mb-1">Description</label>
        <InputText id="description" v-model="entity.description"
          class="w-full p-2 border rounded-m"
          placeholder="Enter description" />
      </div>
    </template>
  </CrudEntityAddEdit>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import InputText from 'primevue/inputtext'
import CrudEntityAddEdit from '@/components/CrudEntityAddEdit.vue'
import { Application } from '@kinotic-ai/os-api'

type RuleValidator = (value: string) => string | boolean

withDefaults(defineProps<{
  id?: string | null
}>(), {
  id: null,
})

const crudServiceIdentifier = 'os_api.org.kinotic.os.api.services.ApplicationService'

const application = ref<Application>(new Application('', ''))

const applicationRules: RuleValidator[] = [
  (v: string) => !!v || 'Name is required'
]
</script>
