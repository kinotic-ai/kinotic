<script lang="ts">
import { Vue, Component } from 'vue-facing-decorator'
import RadioButton from 'primevue/radiobutton'
import Textarea from 'primevue/textarea'
import { isDark as darkMode } from '@/composables/useTheme'

@Component({
  components: { RadioButton, Textarea },
})
export default class StructureSidebarDashboard extends Vue {
  categories = [
    { key: 'table', name: 'Table' },
    { key: 'stream', name: 'Stream' },
  ]

  accessModes = [
    { key: 'none', name: 'None' },
    { key: 'shared', name: 'Shared' },
  ]

  selectedCategory: string = 'Table'
  selectedAccess: string = 'None'
  notes: string = ''

  get isDark() {
    return darkMode.value
  }
}
</script>

<template>
  <div :class="['w-[320px] h-full flex flex-col border', isDark ? 'border-[#2f2f35] bg-[#171717] text-white' : 'border-surface-200 bg-white text-[#101010]']">
    <div :class="['p-6 border-b', isDark ? 'border-[#2f2f35]' : 'border-surface-200']">
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
            v-model="selectedCategory"
            :inputId="category.key"
            name="category"
            :value="category.name"
          />
          <label :for="category.key" class="text-sm">{{ category.name }}</label>
        </div>
      </div>
      <div class="space-y-3">
        <p :class="['text-xs font-medium', isDark ? 'text-[#9f9fa8]' : 'text-gray-500']">Multi tenancy</p>
        <div
          v-for="access in accessModes"
          :key="access.key"
          class="flex items-center gap-2"
        >
          <RadioButton
            size="small"
            v-model="selectedAccess"
            :inputId="access.key"
            name="access"
            :value="access.name"
          />
          <label :for="access.key" class="text-sm">{{ access.name }}</label>
        </div>
      </div>
      <div>
        <p :class="['text-xs font-medium mb-1', isDark ? 'text-[#9f9fa8]' : 'text-gray-500']">Description</p>
        <Textarea
          v-model="notes"
          autoResize
          size="small"
          rows="5" cols="30"
          class="w-full text-sm"
        />
      </div>
    </div>
  </div>
</template>
