<template>
  <Dialog :visible="secret !== null" modal :header="secret?.title" :style="{ width: '34rem' }"
          :closable="false" @update:visible="secret = null">
    <div class="flex flex-col gap-4">
      <p class="text-sm m-0">
        Store the {{ secretLabel.toLowerCase() }} now — <strong>it is shown only this once</strong> and cannot be
        recovered.
      </p>
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium">{{ idLabel }}</label>
        <div class="flex items-center gap-2">
          <code class="text-sm break-all grow">{{ secret?.clientId }}</code>
          <Button icon="pi pi-copy" severity="secondary" text :aria-label="`Copy ${idLabel.toLowerCase()}`"
                  @click="copy(secret?.clientId, idLabel)" />
        </div>
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-sm font-medium">{{ secretLabel }}</label>
        <div class="flex items-center gap-2">
          <code class="text-sm break-all grow">{{ secret?.clientSecret }}</code>
          <Button icon="pi pi-copy" severity="secondary" text :aria-label="`Copy ${secretLabel.toLowerCase()}`"
                  @click="copy(secret?.clientSecret, secretLabel)" />
        </div>
      </div>
    </div>
    <template #footer>
      <Button label="Done" @click="secret = null" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import { useToast } from 'primevue/usetoast'

/** The credentials a machine connects with, or a user signs in with, as disclosed by the operation that issued them. */
export interface MachineSecret {
  /** Names the operation the secret came from, shown as the dialog's header. */
  title: string
  clientId: string
  clientSecret: string
  /** What the id is shown as; a machine's client ID when not given. */
  idLabel?: string
  /** What the secret is shown as; a machine's client secret when not given. */
  secretLabel?: string
}

/**
 * The one disclosure of a generated secret, a machine's client secret or a user's reset password: the credential pair, each copyable, above the
 * Done button that dismisses it. Setting the model opens the dialog; dismissing it clears the
 * model, so the plaintext does not outlive the dialog that discloses it.
 */
const secret = defineModel<MachineSecret | null>({ required: true })

const idLabel = computed(() => secret.value?.idLabel ?? 'Client ID')
const secretLabel = computed(() => secret.value?.secretLabel ?? 'Client secret')

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
