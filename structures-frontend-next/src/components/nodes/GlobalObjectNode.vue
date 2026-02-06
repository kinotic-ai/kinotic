<template>
  <div class="rounded-lg overflow-hidden bg-white text-xs min-w-[200px] border border-surface-100">
    <div>
      <div class="p-3 bg-surface-950 font-semibold text-white flex items-center gap-2">
        <i class="pi pi-table text-[#C8FB39]" />
        {{ data.label }}
      </div>
    </div>

    <div
      v-for="(field, index) in data.fields"
      :key="index"
      class="relative px-2 py-1 border-t border-surface-100 flex justify-between items-center"
    >
      <span class="font-normal text-xs text-surface-900">{{ field.name }}</span>
      <span class="text-gray-500 text-xs font-bold bg-gray-200 ml-2 px-2 py-1 rounded-xl"> {{ field.type }}</span>
      <Handle
        type="source"
        :position="Position.Right"
        :id="`out-${index}`"
        class="w-2 h-2 opacity-0 absolute right-[-6px] top-1/2 transform -translate-y-1/2"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from "@vue-flow/core";

defineProps<{
  data: {
    label: string;
    fields: { name: string; type: string }[];
    color: any;
  };
}>();
</script>