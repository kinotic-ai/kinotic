<template>
  <FormDialog :visible="visible" :icon="UsersRound" :title="group?.id ? 'Rename group' : 'New group'"
              description="A group collects members so one grant reaches all of them."
              @update:visible="emit('update:visible', $event)" @submit="submit">
    <div class="flex flex-col gap-5">
      <div>
        <label for="group-name" class="mb-2 block text-sm font-medium">Name</label>
        <InputText id="group-name" v-model="name" placeholder="QA" autocomplete="off" autofocus class="w-full" />
      </div>
      <div>
        <label for="group-description" class="mb-2 flex items-center justify-between text-sm font-medium">
          Description
          <span class="text-xs font-normal text-muted-color">Optional</span>
        </label>
        <Textarea id="group-description" v-model="description" rows="2" auto-resize placeholder="Who is in it" class="w-full" />
      </div>
    </div>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
      <Button type="submit" :label="group?.id ? 'Save' : 'Create group'" icon="pi pi-check" :loading="saving" :disabled="name.trim() === ''" />
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'
import { UsersRound } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import { Group } from '@kinotic-ai/management-api'
import { FormDialog, showErrorToast } from '@kinotic-ai/frontend-common'

/** Creates a group, or renames one. */
const props = defineProps<{
  visible: boolean
  /** The group to rename, or null for a new one. */
  group: Group | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'saved', group: Group): void
}>()

const toast = useToast()
const name = ref('')
const description = ref('')
const saving = ref(false)

watch(() => props.visible, visible => {
  if (visible) {
    name.value = props.group?.name ?? ''
    description.value = props.group?.description ?? ''
  }
})

async function submit(): Promise<void> {
  if (name.value.trim() === '') {
    return
  }
  saving.value = true
  try {
    const group = Object.assign(new Group(), props.group ?? {}, { name: name.value.trim(), description: description.value.trim() || null })
    const saved = await Kinotic.permissions.saveGroup(group)
    toast.add({ severity: 'success', summary: props.group?.id ? 'Group saved' : 'Group created', life: 4000 })
    emit('saved', saved)
    emit('update:visible', false)
  } catch (err) {
    showErrorToast(toast, 'Failed to save group', err, { life: 8000 })
  } finally {
    saving.value = false
  }
}
</script>
