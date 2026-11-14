<template>
  <FormDrawer :visible="visible" :title="group?.name ?? 'Group'"
              description="Who is in the group. A grant made to the group reaches whoever is in it at the time."
              @close="emit('update:visible', false)">
    <div class="flex flex-col gap-4">
      <form v-if="canManageAccess" id="group-member-form" class="flex gap-2" @submit.prevent="add">
        <SubjectPicker v-model="adding" :sources="[addable]" placeholder="Add a member" empty-message="No member to add matches"
                       class="min-w-0 flex-1" />
        <Button type="submit" label="Add" icon="pi pi-plus" :loading="busy" :disabled="!adding" />
      </form>

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
                  v-tooltip.top="'Remove from group'" :loading="removing.has(member.id ?? '')" @click="remove(member)" />
        </li>
      </ul>
    </div>

    <template #footer>
      <Button type="button" label="Done" severity="secondary" outlined @click="emit('update:visible', false)" />
    </template>
  </FormDrawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import { useToast } from 'primevue/usetoast'
import { Kinotic } from '@kinotic-ai/core'
import type { Group, OrganizationParticipantIdentity } from '@kinotic-ai/management-api'
import { FormDrawer, InitialsTile, showErrorToast } from '@kinotic-ai/frontend-common'
import { type SubjectOption, SubjectPicker, type SubjectSource } from '@kinotic-ai/frontend-common/management'
import { useAccess } from '@/composables/useAccess'

/** The members of a group, with adding and removing for a member who manages access. */
const props = defineProps<{
  visible: boolean
  group: Group | null
  /** The organization's members, as the picker offers them. */
  candidates: SubjectOption[]
  /** Finds the organization's members matching a name or an email, for one the candidates do not hold. */
  searchCandidates: (text: string) => Promise<SubjectOption[]>
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
}>()

const toast = useToast()
const { canManageAccess } = useAccess()
const members = ref<OrganizationParticipantIdentity[]>([])
const loading = ref(false)
const busy = ref(false)
const adding = ref<SubjectOption | null>(null)
// the ids of the members whose removal is in flight, so a second click sends nothing
const removing = ref(new Set<string>())

// the members the picker offers, listed and searched: whoever is in the group is left out
const addable = computed<SubjectSource>(() => ({
  label: 'Members',
  options: outside(props.candidates),
  search: async text => outside(await props.searchCandidates(text))
}))

// Reads the group's members as it is called, so a search that answers after the members were read again leaves out
// whoever is in the group by then
function outside(options: SubjectOption[]): SubjectOption[] {
  const inGroup = new Set(members.value.map(member => member.id))
  return options.filter(option => !inGroup.has(option.subject.id))
}

watch(() => [props.visible, props.group?.id], ([visible]) => {
  if (visible && props.group?.id) {
    void load()
  }
})

// The drawer may be opened for another group while a read is in flight, so the list is emptied as a read starts
// and only the latest read's answer, or failure, reaches it
let latestLoad = 0

async function load(): Promise<void> {
  const thisLoad = ++latestLoad
  loading.value = true
  adding.value = null
  members.value = []
  try {
    const found = await Kinotic.permissions.findGroupMembers(props.group!.id!)
    if (thisLoad === latestLoad) {
      members.value = found
    }
  } catch (err) {
    if (thisLoad === latestLoad) {
      showErrorToast(toast, 'Failed to load the group\'s members', err, { life: 8000 })
    }
  } finally {
    if (thisLoad === latestLoad) {
      loading.value = false
    }
  }
}

async function add(): Promise<void> {
  if (!adding.value || !props.group?.id) {
    return
  }
  busy.value = true
  try {
    await Kinotic.permissions.addGroupMember(props.group.id, adding.value.subject.id)
    await load()
  } catch (err) {
    showErrorToast(toast, 'Failed to add the member', err, { life: 8000 })
  } finally {
    busy.value = false
  }
}

async function remove(member: OrganizationParticipantIdentity): Promise<void> {
  if (!props.group?.id || !member.id || removing.value.has(member.id)) {
    return
  }
  const groupId = props.group.id
  removing.value = new Set(removing.value).add(member.id)
  try {
    await Kinotic.permissions.removeGroupMember(groupId, member.id)
    // the drawer may show another group by now, whose list this member stays in
    if (props.group?.id === groupId) {
      members.value = members.value.filter(listed => listed.id !== member.id)
    }
  } catch (err) {
    showErrorToast(toast, 'Failed to remove the member', err, { life: 8000 })
  } finally {
    const next = new Set(removing.value)
    next.delete(member.id)
    removing.value = next
  }
}
</script>
