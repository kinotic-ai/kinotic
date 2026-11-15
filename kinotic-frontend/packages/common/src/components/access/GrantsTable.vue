<template>
  <DataTable :value="grants">
    <Column header="Who" :style="{ width: widths.who }">
      <template #body="{ data, index }">
        <SubjectCell :subject="data.subject" :label="labelOf(data.subject)" :detail="detailOf?.(data.subject)" :index="index" />
      </template>
    </Column>
    <Column header="Role" :style="{ width: widths.role }">
      <template #body="{ data }"><RoleCell :role="roleOf(data.roleId)" :role-id="data.roleId" /></template>
    </Column>
    <Column v-if="$slots.where" :header="whereHeader" :style="{ width: widths.where }">
      <template #body="{ data }"><slot name="where" :grant="data" /></template>
    </Column>
    <Column v-if="revoke" :style="{ width: widths.actions }">
      <template #body="{ data }">
        <div class="flex justify-end">
          <Button label="Revoke" icon="pi pi-times" size="small" severity="danger" text @click="confirmRevoke(data)" />
        </div>
      </template>
    </Column>
  </DataTable>
</template>

<script setup lang="ts">
import { computed, useSlots } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import type { Grant, RoleDefinition, Subject } from '@kinotic-ai/management-api'
import { showErrorToast } from '../../util/helpers'
import RoleCell from '../RoleCell.vue'
import SubjectCell from '../SubjectCell.vue'

/**
 * The grants of a store as rows: who holds which role, where the grant was made when the `where` slot says so,
 * and, when a `revoke` is given, a button that revokes the grant once the viewer confirms it.
 */
const props = defineProps<{
  grants: Grant[]
  /** The roles the grants name, for the name and the permissions of each. */
  roles: RoleDefinition[]
  labelOf: (subject: Subject) => string
  /** What a subject is, shown under its name; by default a member or a group, as its type says. */
  detailOf?: (subject: Subject) => string | undefined
  /** The header of the column the `where` slot fills. */
  whereHeader?: string
  /** Revokes a grant in its store. Without it the rows have no Revoke button. */
  revoke?: (grant: Grant) => Promise<void>
  /** Where the confirmation says a grant is revoked, such as `here` or `on tenant acme`. */
  revokeWhere?: (grant: Grant) => string
  /** What the confirmation says becomes of the subject's access. */
  revokeConsequence?: string
}>()

const emit = defineEmits<{
  /** A grant was revoked in its store. */
  revoked: [grant: Grant]
}>()

const slots = useSlots()
const toast = useToast()
const confirm = useConfirm()

const widths = computed(() => {
  let ret: { who: string, role: string, where?: string, actions?: string }
  if (!slots.where) {
    ret = { who: '40%', role: '40%', actions: '20%' }
  } else if (props.revoke) {
    ret = { who: '35%', role: '30%', where: '20%', actions: '15%' }
  } else {
    ret = { who: '36%', role: '32%', where: '32%' }
  }
  return ret
})

function roleOf(roleId: string): RoleDefinition | undefined {
  return props.roles.find(role => role.id === roleId)
}

function confirmRevoke(grant: Grant): void {
  const where = props.revokeWhere ? ` ${props.revokeWhere(grant)}` : ''
  confirm.require({
    header: 'Revoke access',
    message: `Revoke ${roleOf(grant.roleId)?.name ?? grant.roleId} from ${props.labelOf(grant.subject)}${where}? ${props.revokeConsequence ?? ''}`.trim(),
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Revoke', severity: 'danger' },
    rejectProps: { label: 'Keep', severity: 'secondary', outlined: true },
    accept: () => revokeConfirmed(grant)
  })
}

async function revokeConfirmed(grant: Grant): Promise<void> {
  try {
    await props.revoke?.(grant)
    emit('revoked', grant)
    toast.add({ severity: 'success', summary: 'Access revoked', life: 4000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to revoke access', err, { life: 8000 })
  }
}
</script>
