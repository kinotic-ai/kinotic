<template>
  <AutoComplete v-model="entered" :input-id="inputId" :suggestions="suggestions" option-label="label"
                :option-group-label="grouped ? 'label' : undefined" :option-group-children="grouped ? 'options' : undefined"
                :placeholder="placeholder" :empty-search-message="emptyMessage" dropdown force-selection fluid
                @complete="complete($event.query)" @keydown.esc="keepEscape">
    <template #option="{ option }">
      <SubjectOptionItem :option="option" />
    </template>
  </AutoComplete>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AutoComplete from 'primevue/autocomplete'
import { useToast } from 'primevue/usetoast'
import { showErrorToast } from '../../util/helpers'
import type { SubjectOption } from './SubjectOption'
import SubjectOptionItem from './SubjectOptionItem.vue'
import type { SubjectSource } from './SubjectSource'

/**
 * Picks one subject from the given sources: opening the list offers each source's options, and typing offers the
 * subjects that match, found by the source's search or, for a source without one, among its options. Several
 * sources are listed under their labels.
 */
const props = withDefaults(defineProps<{
  sources: SubjectSource[]
  placeholder: string
  /** What the list says when nothing matches. */
  emptyMessage: string
  inputId?: string
}>(), {
  inputId: undefined
})

const picked = defineModel<SubjectOption | null>({ required: true })

const toast = useToast()
// the input holds the text being typed until a suggestion is chosen, and text names no subject
const entered = ref<SubjectOption | string | null>(picked.value)
const suggestions = ref<SubjectOption[] | SubjectSource[]>([])
const grouped = computed(() => props.sources.length > 1)
// a search may answer after a later one was started, so only the latest search's answer, or failure, is shown
let latestSearch = 0

watch(entered, value => { picked.value = optionOf(value) })
watch(picked, value => {
  if (value !== optionOf(entered.value)) {
    entered.value = value
  }
})

// the suggestions of the sources shown before are none of the sources shown now, such as another tab's
watch(() => props.sources.map(source => source.label).join('\n'), () => {
  latestSearch++
  suggestions.value = []
})

// Escape closes the open list and stops there: left to bubble, it would also close a drawer the picker is in.
// The input still says the list is open, since the component renders it closed after the event
function keepEscape(event: KeyboardEvent): void {
  if (event.target instanceof HTMLElement && event.target.getAttribute('aria-expanded') === 'true') {
    event.stopPropagation()
  }
}

function optionOf(value: SubjectOption | string | null): SubjectOption | null {
  return value !== null && typeof value === 'object' ? value : null
}

async function complete(query: string): Promise<void> {
  const thisSearch = ++latestSearch
  const text = query.trim()
  try {
    const found = await Promise.all(props.sources.map(source => matching(source, text)))
    if (thisSearch === latestSearch) {
      suggestions.value = grouped.value
          ? props.sources.map((source, index) => ({ label: source.label, options: found[index] ?? [] }))
                         .filter(source => source.options.length > 0)
          : [...found[0] ?? []]
    }
  } catch (err) {
    if (thisSearch === latestSearch) {
      suggestions.value = []
      showErrorToast(toast, 'Failed to search', err, { life: 8000 })
    }
  }
}

function matching(source: SubjectSource, text: string): Promise<SubjectOption[]> {
  let ret: Promise<SubjectOption[]>
  if (text === '') {
    ret = Promise.resolve(source.options)
  } else if (source.search) {
    ret = source.search(text)
  } else {
    const lowered = text.toLowerCase()
    ret = Promise.resolve(source.options.filter(option => option.label.toLowerCase().includes(lowered)
        || (option.detail ?? '').toLowerCase().includes(lowered)))
  }
  return ret
}
</script>
