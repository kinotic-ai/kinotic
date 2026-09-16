<template>
  <MachinesPage
    description="Platform daemons that connect to the system zone with their own client id and secret, such as a worker node's vm-manager."
    :machines="Kinotic.systemMembers"
  >
    <template #create-hint>
      A machine connects with the Kinotic client, using the client id and secret shown after
      creation — the <code>KINOTIC_CLIENT_ID</code> and <code>KINOTIC_CLIENT_SECRET</code> a
      vm-manager is configured with.
    </template>
  </MachinesPage>
</template>

<script setup lang="ts">
import { Kinotic } from '@kinotic-ai/core'
import { MachinesPage } from '@kinotic-ai/frontend-common'

// SystemMemberService's machine methods already take SYSTEM scope implicitly, so it is the
// MachineOperations shape with nothing to bind
</script>
