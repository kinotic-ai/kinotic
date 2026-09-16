<template>
  <Dialog :visible="secret !== null" modal :header="secret?.title" :style="{ width: '34rem' }"
          :closable="false" @update:visible="secret = null">
    <div class="flex flex-col gap-4">
      <p class="text-sm m-0">
        Store the client secret now — <strong>it is shown only this once</strong> and cannot be
        recovered.
      </p>
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium">Client ID</label>
        <div class="flex items-center gap-2">
          <code class="text-sm break-all grow">{{ secret?.clientId }}</code>
          <Button icon="pi pi-copy" severity="secondary" text aria-label="Copy client ID"
                  @click="copy(secret?.clientId, 'Client ID')" />
        </div>
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium">Client secret</label>
        <div class="flex items-center gap-2">
          <code class="text-sm break-all grow">{{ secret?.clientSecret }}</code>
          <Button icon="pi pi-copy" severity="secondary" text aria-label="Copy client secret"
                  @click="copy(secret?.clientSecret, 'Client secret')" />
        </div>
      </div>
    </div>
    <template #footer>
      <Button label="Done" @click="secret = null" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import { useToast } from 'primevue/usetoast'

/** The credentials a machine connects with, as disclosed by the operation that issued them. */
export interface MachineSecret {
  /** Names the operation the secret came from, shown as the dialog's header. */
  title: string
  clientId: string
  clientSecret: string
}

/**
 * The one disclosure of a machine's client secret: the credential pair, each copyable, above the
 * Done button that dismisses it. Setting the model opens the dialog; dismissing it clears the
 * model, so the plaintext does not outlive the dialog that discloses it.
 */
const secret = defineModel<MachineSecret | null>({ required: true })

const toast = useToast()

async function copy(value: string | undefined, what: string) {
  try {
    await navigator.clipboard.writeText(value ?? '')
    toast.add({ severity: 'success', summary: `${what} copied`, life: 3000 })
  } catch {
    toast.add({ severity: 'error', summary: `Could not copy ${what.toLowerCase()}`, life: 5000 })
  }
}
</script>
