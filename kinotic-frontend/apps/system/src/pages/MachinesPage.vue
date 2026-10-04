<template>
  <div class="flex flex-col">
    <PageHeader title="Machines"
                description="Platform daemons that connect to the system zone with their own client id and secret, such as a worker node's vm-manager." />

    <!-- SystemMemberService's machine methods already take SYSTEM scope implicitly, so it is
         the MachineOperations shape with nothing to bind -->
    <MachinesTable :machines="Kinotic.systemMembers" :more-actions="accessActions">
      <template #create-hint>
        A machine connects with the Kinotic client, using the client id and secret shown after
        creation — the <code>KINOTIC_CLIENT_ID</code> and <code>KINOTIC_CLIENT_SECRET</code> a
        vm-manager is configured with.
      </template>
    </MachinesTable>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { MenuItem } from 'primevue/menuitem'
import { Kinotic } from '@kinotic-ai/core'
import { MachinesTable, PageHeader } from '@kinotic-ai/frontend-common'

const router = useRouter()

// a machine's grants, such as the registrar role a vm-manager registers nodes with, live on the Access page
function accessActions(machineId: string): MenuItem[] {
  return [{ label: 'Manage access', icon: 'pi pi-key', command: () => router.push({ name: 'platform-access', query: { subject: machineId } }) }]
}
</script>
