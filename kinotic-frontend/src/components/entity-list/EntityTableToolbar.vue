<template>
  <Toolbar class="!w-full flex-shrink-0">
    <template #start>
      <InputText
        :modelValue="searchText"
        @update:modelValue="$emit('update:searchText', $event)"
        placeholder="Search"
        @keyup.enter="$emit('search')"
        @focus="($event.target as HTMLInputElement)?.select()"
        class="w-1/2"
      />
      <Button icon="pi pi-times" class="ml-2" v-if="searchText" @click="$emit('clearSearch')" />
    </template>
  </Toolbar>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-facing-decorator'
import Toolbar from 'primevue/toolbar'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'

@Component({
  components: { Toolbar, Button, InputText },
  emits: ['update:searchText', 'search', 'clearSearch']
})
export default class EntityTableToolbar extends Vue {
  @Prop({ type: String, default: null }) searchText!: string | null
}
</script>
