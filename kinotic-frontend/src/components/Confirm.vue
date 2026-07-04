<template>
    <Dialog
      v-model:visible="dialog"
      :header="title ?? ''"
      :modal="true"
      :closable="false"
      :draggable="false"
      :style="{ width: options.width + 'px' }"
    >
      <div class="p-4">
        <p>{{ message }}</p>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <Button
            label="Yes"
            severity="danger"
            @click="agree"
          />
          <Button
            label="Cancel"
            severity="secondary"
            @click="cancel"
          />
        </div>
      </template>
    </Dialog>
  </template>
  
  <script setup lang="ts">
  import { ref } from 'vue'
  import Dialog from 'primevue/dialog'
  import Button from 'primevue/button'

  export interface ConfirmOptions {
    width?: number
  }

  const dialog = ref(false)
  let resolve!: (value: boolean) => void
  const message = ref<string | null>(null)
  const title = ref<string | null>(null)

  const options = ref<ConfirmOptions>({
    width: 400
  })

  function open(newTitle: string, newMessage: string, newOptions: ConfirmOptions = {}): Promise<boolean> {
    title.value = newTitle
    message.value = newMessage
    options.value = { ...options.value, ...newOptions }
    dialog.value = true
    return new Promise<boolean>((res) => {
      resolve = res
    })
  }

  function agree() {
    resolve(true)
    dialog.value = false
  }

  function cancel() {
    resolve(false)
    dialog.value = false
  }
  </script>
  