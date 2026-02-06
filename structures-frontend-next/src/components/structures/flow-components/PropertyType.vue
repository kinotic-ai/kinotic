<script lang="ts">
import { Vue, Component, Prop, Emit } from 'vue-facing-decorator'

@Component
export default class PropertyType extends Vue {
  @Prop({ required: true }) readonly type!: string
  @Prop({ required: true }) readonly color!: string

  @Emit('edit')
  onEdit(event: MouseEvent) {
    return event
  }

  get parsedTypes() {
    const match = this.type?.match(/^(.+)\[\]$/);
    const baseType = match ? match[1] : this.type

    const isSpecialType = (type: string) => ['object', 'enum', 'union'].includes(type)

    const getClass = (type: string) =>
        isSpecialType(type) ? `${this.color}` : 'bg-surface-100'

    if (match) {
      return [
        { label: 'Array', className: 'bg-surface-100' },
        { label: this.capitalize(baseType), className: getClass(baseType) }
      ]
    }

    return [{ label: this.capitalize(this.type), className: getClass(this.type) }]
  }

  capitalize(str: string) {
    return str ? str.charAt(0).toUpperCase() + str.slice(1) : ''
  }
}
</script>

<template>
  <div class="flex gap-1 justify-end">
    <span
        v-for="(type, index) in parsedTypes"
        :key="index"
        class="text-[10px] px-2 py-1 text-surface-600 rounded-full font-bold whitespace-nowrap cursor-pointer"
        :class="type.className"
        @dblclick.stop="onEdit"
    >
      {{ type.label }}
    </span>
  </div>
</template>
