<template>
  <FormDrawer :visible="visible" title="Grant access" :description="description" @close="emit('update:visible', false)">
    <form :id="formId" @submit.prevent="submit">
      <div class="flex flex-col gap-5">
        <div>
          <span class="mb-2 block text-sm font-medium">Who</span>
          <SelectButton v-model="kindLabel" :options="kinds.map(kind => kind.label)" :allow-empty="false" class="mb-3" />
          <Select v-model="subjectId" :options="kind.options" option-label="label" option-value="subject.id"
                  filter :filter-fields="['label', 'detail']" :placeholder="kind.placeholder"
                  class="w-full" :empty-message="kind.emptyMessage">
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
import type { SubjectKind } from './SubjectKind'
import SubjectOptionItem from './SubjectOptionItem.vue'

/**
 * Grants a role to a subject picked from one of the offered kinds, through the given grant call. The default slot
 * sits between the subject and the role, for a picker of where the grant is made.
 */
const props = withDefaults(defineProps<{
  visible: boolean
  description: string
  /** The kinds of subject offered, the first picked when the drawer opens. */
  kinds: SubjectKind[]
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
const kindLabel = ref(props.kinds[0]?.label ?? '')
const subjectId = ref<string | null>(null)
const roleId = ref<string | null>(null)
const granting = ref(false)

const kind = computed(() => props.kinds.find(candidate => candidate.label === kindLabel.value) ?? props.kinds[0])
const picked = computed(() => kind.value?.options.find(option => option.subject.id === subjectId.value) ?? null)

watch(() => props.visible, visible => {
  if (visible) {
    kindLabel.value = props.kinds[0]?.label ?? ''
    subjectId.value = null
    roleId.value = null
  }
})

// a subject of one kind is not one of another, so switching the kind clears the pick
watch(kindLabel, () => { subjectId.value = null })

async function submit(): Promise<void> {
  if (!picked.value || !roleId.value || !props.ready) {
    return
  }
  granting.value = true
  try {
    const grant = await props.grant(picked.value.subject, roleId.value)
    toast.add({ severity: 'success', summary: 'Access granted', life: 4000 })
    emit('granted', grant)
    emit('update:visible', false)
  } catch (err) {
    showErrorToast(toast, 'Failed to grant access', err, { life: 8000 })
  } finally {
    granting.value = false
  }
}
</script>
