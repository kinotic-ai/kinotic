<template>
  <div class="mb-3 flex shrink-0 items-center gap-2">
    <IconField class="w-[236px] max-w-sm">
      <InputIcon class="pi pi-search" />
      <InputText
        :modelValue="searchText"
        placeholder="Search"
        size="small"
        name="search"
        autocomplete="off"
        @update:modelValue="$emit('update:searchText', $event)"
        @keyup.enter="$emit('search')"
        @focus="($event.target as HTMLInputElement)?.select()"
      />
    </IconField>
    <Button v-if="searchText" icon="pi pi-times" severity="secondary" text rounded size="small"
            aria-label="Clear search" @click="$emit('clearSearch')" />
  </div>
</template>

<script setup lang="ts">
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'

/** The search field above the entity table, in the shape every CrudTable draws its own. */
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
