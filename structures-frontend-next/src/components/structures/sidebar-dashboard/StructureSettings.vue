<script lang="ts">
import {useStructureStore} from "@/stores/editor.ts";
import {Component, Vue} from "vue-facing-decorator";

@Component
export default class StructureSettings extends Vue {
  structureStore = useStructureStore()

  categories = [
    {key: 'TABLE', name: 'Table'},
    {key: 'STREAM', name: 'Stream'}
  ]
  accessModes = [
    {key: 'NONE', name: 'None'},
    {key: 'SHARED', name: 'Shared'}
  ]

  get entityType() {
    const entityDecorator = this.structureStore.structure?.entityDefinition?.decorators?.find(
      (d: any) => d.type === 'Entity'
    ) as any
    return entityDecorator?.entityType || 'TABLE'
  }

  set entityType(value: string) {
    const entityDecorator = this.structureStore.structure?.entityDefinition?.decorators?.find(
      (d: any) => d.type === 'Entity'
    ) as any
    if (entityDecorator) {
      entityDecorator.entityType = value
    }
  }

  get multiTenancyType() {
    const entityDecorator = this.structureStore.structure?.entityDefinition?.decorators?.find(
      (d: any) => d.type === 'Entity'
    ) as any
    return entityDecorator?.multiTenancyType || 'NONE'
  }

  set multiTenancyType(value: string) {
    const entityDecorator = this.structureStore.structure?.entityDefinition?.decorators?.find(
      (d: any) => d.type === 'Entity'
    ) as any
    if (entityDecorator) {
      entityDecorator.multiTenancyType = value
    }
  }

  get description() {
    return this.structureStore.structure?.description || ''
  }

  set description(value: string) {
    if (this.structureStore.structure) {
      this.structureStore.structure.description = value
    }
  }
}
</script>

<template>
  <div v-if="structureStore.structure">
    <div class="border-b border-surface-200 p-6">
      <h3 class="text-sm font-semibold">Structure settings</h3>
    </div>
    <div class="flex-1 overflow-y-auto p-6 space-y-6">
      <div class="space-y-3 mb-7">
        <div
            v-for="category in categories"
            :key="category.key"
            class="flex items-center gap-2"
        >
          <RadioButton
              size="small"
              v-model="entityType"
              :inputId="category.key"
              name="category"
              :value="category.key"
          />
          <label :for="category.key" class="text-sm">{{ category.name }}</label>
        </div>
      </div>

      <div class="space-y-3">
        <p class="text-xs font-medium text-gray-500">Multi tenancy</p>
        <div
            v-for="access in accessModes"
            :key="access.key"
            class="flex items-center gap-2"
        >
          <RadioButton
              size="small"
              v-model="multiTenancyType"
              :inputId="access.key"
              name="access"
              :value="access.key"
          />
          <label :for="access.key" class="text-sm">{{ access.name }}</label>
        </div>
      </div>

      <div>
        <p class="text-xs font-medium text-gray-500 mb-1">Description</p>
        <Textarea
            v-model="description"
            autoResize
            size="small"
            rows="5" cols="30"
            class="w-full text-sm"
        />
      </div>
    </div>
  </div>
</template>
