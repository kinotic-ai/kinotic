<template>
  <FormDialog :visible="visible" :icon="UsersRound" :title="group?.name ?? 'Group'" description="Who is in the group. A grant made to the group reaches whoever is in it at the time."
              @update:visible="emit('update:visible', $event)" @submit="add">
    <div class="flex flex-col gap-4">
      <div v-if="canManageAccess" class="flex gap-2">
        <Select v-model="adding" :options="addable" option-label="label" option-value="id" filter :filter-fields="['label', 'detail']"
                placeholder="Add a member" class="min-w-0 flex-1" empty-message="Everyone is in the group">
          <template #option="{ option }">
            <div class="flex flex-col">
              <span class="text-sm">{{ option.label }}</span>
              <span v-if="option.detail" class="text-xs text-muted-color">{{ option.detail }}</span>
            </div>
          </template>
        </Select>
        <Button type="submit" label="Add" icon="pi pi-plus" :loading="busy" :disabled="!adding" />
      </div>

      <div v-if="loading" class="flex flex-col gap-2">
        <Skeleton v-for="n in 2" :key="n" height="2.5rem" />
      </div>
      <p v-else-if="members.length === 0" class="rounded-xl border border-dashed border-surface-300 p-6 text-center text-sm text-muted-color dark:border-surface-700">
        Nobody is in the group yet.
      </p>
      <ul v-else class="divide-y divide-surface-100 rounded-xl border border-surface-200 dark:divide-surface-800 dark:border-surface-700">
        <li v-for="(member, index) in members" :key="member.id ?? index" class="flex items-center gap-3 px-4 py-2.5">
          <InitialsTile :name="member.displayName || member.email" :index="index" />
          <span class="flex min-w-0 flex-1 flex-col">
            <span class="truncate text-sm text-surface-950 dark:text-surface-0">{{ member.displayName || member.email }}</span>
            <span v-if="member.displayName" class="truncate text-xs text-muted-color">{{ member.email }}</span>
          </span>
          <Button v-if="canManageAccess" icon="pi pi-times" size="small" severity="danger" text aria-label="Remove from group"
                  v-tooltip.top="'Remove from group'" @click="remove(member)" />
        </li>
      </ul>
    </div>

    <template #footer>
      <Button type="button" label="Done" severity="secondary" outlined @click="emit('update:visible', false)" />
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Skeleton from 'primevue/skeleton'
import { useToast } from 'primevue/usetoast'
import { UsersRound } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import type { Group, UserParticipantIdentity } from '@kinotic-ai/management-api'
import { FormDialog, InitialsTile, showErrorToast } from '@kinotic-ai/frontend-common'
import { useAccess } from '@/composables/useAccess'
import type { SubjectOption } from './useSubjects'

/** The members of a group, with adding and removing for a member who manages access. */
const props = defineProps<{
  visible: boolean
  group: Group | null
  /** The organization's members, as the picker offers them. */
  candidates: SubjectOption[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
}>()

const toast = useToast()
const { canManageAccess } = useAccess()
const members = ref<UserParticipantIdentity[]>([])
const loading = ref(false)
const busy = ref(false)
const adding = ref<string | null>(null)

const addable = computed(() => {
  const inGroup = new Set(members.value.map(member => member.id))
  return props.candidates.filter(option => !inGroup.has(option.subject.id))
                         .map(option => ({ id: option.subject.id, label: option.label, detail: option.detail }))
})

watch(() => [props.visible, props.group?.id], ([visible]) => {
  if (visible && props.group?.id) {
    void load()
  }
})

async function load(): Promise<void> {
  loading.value = true
  adding.value = null
  try {
    members.value = await Kinotic.permissions.findGroupMembers(props.group!.id!)
  } catch (err) {
    showErrorToast(toast, 'Failed to load the group\'s members', err, { life: 8000 })
  } finally {
    loading.value = false
  }
}

async function add(): Promise<void> {
  if (!adding.value || !props.group?.id) {
    return
  }
  busy.value = true
  try {
    await Kinotic.permissions.addGroupMember(props.group.id, adding.value)
    await load()
  } catch (err) {
    showErrorToast(toast, 'Failed to add the member', err, { life: 8000 })
  } finally {
    busy.value = false
  }
}

async function remove(member: UserParticipantIdentity): Promise<void> {
  if (!props.group?.id || !member.id) {
    return
  }
  try {
    await Kinotic.permissions.removeGroupMember(props.group.id, member.id)
    members.value = members.value.filter(listed => listed.id !== member.id)
  } catch (err) {
    showErrorToast(toast, 'Failed to remove the member', err, { life: 8000 })
  }
}
</script>
