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
  
  <script lang="ts">
  import { Vue, Component } from 'vue-facing-decorator'
  import Dialog from 'primevue/dialog'
  import Button from 'primevue/button'
  
  export interface ConfirmOptions {
    width?: number
  }
  
  @Component({
    components: {
      Dialog,
      Button
    }
  })
  export default class Confirm extends Vue {
    private dialog = false
    private resolve!: (value: boolean) => void
    public reject!: (reason?: any) => void
    private message: string | null = null
    private title: string | null = null
  
    private options: ConfirmOptions = {
      width: 400
    }
  
    public open(title: string, message: string, options: ConfirmOptions = {}): Promise<boolean> {
      this.title = title
      this.message = message
      this.options = { ...this.options, ...options }
      this.dialog = true
      return new Promise<boolean>((resolve, reject) => {
        this.resolve = resolve
        this.reject = reject
      })
    }
  
    public agree() {
      this.resolve(true)
      this.dialog = false
    }
  
    public cancel() {
      this.resolve(false)
      this.dialog = false
    }
  }
  </script>
  