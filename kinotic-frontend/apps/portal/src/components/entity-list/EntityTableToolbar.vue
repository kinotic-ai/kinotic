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

<script setup lang="ts">
import Toolbar from 'primevue/toolbar'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'

withDefaults(defineProps<{
  searchText?: string | null
}>(), {
  searchText: null,
})

defineEmits<{
  (e: 'update:searchText', value: string | undefined): void
  (e: 'search'): void
  (e: 'clearSearch'): void
}>()
</script>
