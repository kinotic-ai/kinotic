<template>
  <MachinesPage
    description="Non-human callers that connect to this application's API with their own client id and secret."
    :machines="machines"
  >
    <template #create-hint>
      A machine connects to this application's API with the Kinotic client, using the
      client id and secret shown after creation.
    </template>
  </MachinesPage>
</template>

<script setup lang="ts">
import { Kinotic } from '@kinotic-ai/core'
import { MachinesPage, type MachineOperations } from '@kinotic-ai/frontend-common'

/** Machine API clients of one application, managed by the owning org's members. */
const props = defineProps<{
  applicationId: string
}>()

// Each call reads applicationId when it runs, so the page follows a switch to another application
const machines: MachineOperations = {
  findMachines: pageable => Kinotic.machines.findMachines(props.applicationId, pageable),
  createMachine: displayName => Kinotic.machines.createMachine(displayName, props.applicationId),
  rotateSecret: machineId => Kinotic.machines.rotateSecret(machineId),
  setMachineEnabled: (machineId, enabled) => Kinotic.machines.setMachineEnabled(machineId, enabled),
  removeMachine: machineId => Kinotic.machines.removeMachine(machineId)
}
</script>
