<template>
  <FormDrawer :visible="visible" title="Grant access" :description="description" @close="emit('update:visible', false)">
    <form :id="formId" @submit.prevent="submit">
      <div class="flex flex-col gap-5">
        <div>
          <span class="mb-2 block text-sm font-medium">Who</span>
          <SelectButton v-model="tabLabel" :options="tabs.map(tab => tab.label)" :allow-empty="false" class="mb-3" />
          <Select v-model="subjectId" :options="tab.options" option-label="label" option-value="subject.id"
                  filter :filter-fields="['label', 'detail']" :placeholder="tab.placeholder"
                  class="w-full" :empty-message="tab.emptyMessage">
            <template #option="{ option }">
              <SubjectOptionItem :option="option" />
            </template>
          </Select>
        </div>
        <slot />
        <div>
          <label :for="roleInputId" class="mb-2 block text-sm font-medium">Role</label>
          <Select :input-id="roleInputId" v-model="roleId" :options="roles" option-label="name" option-value="id" filter
                  placeholder="Choose a role" class="w-full">
            <template #option="{ option }">
              <div class="flex flex-col">
                <span class="flex items-center gap-2 text-sm">
                  {{ option.name }}
                  <Tag v-if="option.builtIn" value="Built-in" severity="secondary" class="!text-[10px]" />
                </span>
                <span class="text-xs text-muted-color">{{ summarizeRole(option) }}</span>
              </div>
            </template>
          </Select>
          <p v-if="roleHint" class="mt-1.5 text-[0.8125rem] text-muted-color">{{ roleHint }}</p>
        </div>
      </div>
    </form>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
      <Button type="submit" :form="formId" label="Grant" icon="pi pi-check" :loading="granting" :disabled="!picked || !roleId || !ready" />
    </template>
  </FormDrawer>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import type { Grant, RoleDefinition, Subject } from '@kinotic-ai/management-api'
import FormDrawer from '../FormDrawer.vue'
import { showErrorToast } from '../../util/helpers'
import { summarizeRole } from '../../util/access'
import type { SubjectPickerTab } from './SubjectPickerTab'
import SubjectOptionItem from './SubjectOptionItem.vue'

/**
 * Grants a role to a subject picked from one of the picker's tabs, through the given grant call. The default slot
 * sits between the subject and the role, for a picker of where the grant is made.
 */
const props = withDefaults(defineProps<{
  visible: boolean
  description: string
  /** The tabs of the subject picker, the first shown when the drawer opens. */
  tabs: SubjectPickerTab[]
  /** The roles offered. */
  roles: RoleDefinition[]
  /** What the picked role reaches, shown beneath the role picker. */
  roleHint?: string
  /** Whether everything the slot asks for is picked. */
  ready?: boolean
  grant: (subject: Subject, roleId: string) => Promise<Grant>
}>(), {
  roleHint: undefined,
  ready: true
})

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'granted', grant: Grant): void
}>()

const toast = useToast()
const formId = useId()
const roleInputId = useId()
const tabLabel = ref(props.tabs[0]?.label ?? '')
const subjectId = ref<string | null>(null)
const roleId = ref<string | null>(null)
const granting = ref(false)

const tab = computed(() => props.tabs.find(candidate => candidate.label === tabLabel.value) ?? props.tabs[0])
const picked = computed(() => tab.value?.options.find(option => option.subject.id === subjectId.value) ?? null)

// counts the times the drawer opened, so a grant that completes after the drawer was closed and opened again
// leaves the picks it did not start open
let opening = 0

watch(() => props.visible, visible => {
  if (visible) {
    opening++
    tabLabel.value = props.tabs[0]?.label ?? ''
    subjectId.value = null
    roleId.value = null
  }
})

// a subject of one tab is not one of another, so switching the tab clears the pick
watch(tabLabel, () => { subjectId.value = null })

async function submit(): Promise<void> {
  if (!picked.value || !roleId.value || !props.ready) {
    return
  }
  const thisOpening = opening
  granting.value = true
  try {
    const grant = await props.grant(picked.value.subject, roleId.value)
    toast.add({ severity: 'success', summary: 'Access granted', life: 4000 })
    emit('granted', grant)
    if (thisOpening === opening) {
      emit('update:visible', false)
    }
  } catch (err) {
    showErrorToast(toast, 'Failed to grant access', err, { life: 8000 })
  } finally {
    granting.value = false
  }
}
</script>
