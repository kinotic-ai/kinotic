<template>
  <div class="table-paginator flex items-center justify-between gap-3">
    <span class="app-subtle-text text-sm tabular-nums">{{ rangeLabel }}</span>

    <div class="flex items-center gap-2">
      <Select checkmark
        :modelValue="rows"
        :options="pageSizeOptions"
        optionLabel="label"
        optionValue="value"
        size="small"
        aria-label="Rows per page"
        @update:modelValue="changeRows"
      />
      <ButtonGroup>
        <Button
          severity="secondary"
          variant="outlined"
          size="small"
          class="!w-10"
          aria-label="Previous page"
          :disabled="page === 0"
          @click="goTo(page - 1)"
        >
          <template #icon>
            <ChevronLeft :size="16" :stroke-width="1.75" aria-hidden="true" />
          </template>
        </Button>
        <Button
          severity="secondary"
          variant="outlined"
          size="small"
          class="!w-10"
          aria-label="Next page"
          :disabled="page >= pageCount - 1"
          @click="goTo(page + 1)"
        >
          <template #icon>
            <ChevronRight :size="16" :stroke-width="1.75" aria-hidden="true" />
          </template>
        </Button>
      </ButtonGroup>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Button from 'primevue/button'
import ButtonGroup from 'primevue/buttongroup'
import Select from 'primevue/select'
import type { PageState } from 'primevue/paginator'
import { ChevronLeft, ChevronRight } from '@lucide/vue'

/**
 * Compact pager for a lazily loaded table: the visible range and total on the left, a page-size
 * select and grouped previous/next buttons on the right. Emits {@code page} with the new
 * {@link PageState} whenever the page or the page size changes.
 */
const props = defineProps<{
  /** Index of the first row on the current page. */
  first: number
  rows: number
  totalRecords: number
  rowsPerPageOptions: number[]
}>()

const emit = defineEmits<{
  (e: 'page', state: PageState): void
}>()

const page = computed(() => Math.floor(props.first / props.rows))
const pageCount = computed(() => Math.max(1, Math.ceil(props.totalRecords / props.rows)))

const pageSizeOptions = computed(() =>
    props.rowsPerPageOptions.map(size => ({ label: `${size} per page`, value: size })))

// Blank until there are rows to count: the table itself says when it is empty
const rangeLabel = computed(() => {
  let ret: string
  if (props.totalRecords === 0) {
    ret = ''
  } else {
    const last = Math.min(props.first + props.rows, props.totalRecords)
    ret = `${props.first + 1}–${last} of ${props.totalRecords}`
  }
  return ret
})

function goTo(target: number) {
  emit('page', { page: target, first: target * props.rows, rows: props.rows, pageCount: pageCount.value })
}

// A new page size starts over at the first page, as the old offset may lie past the end
function changeRows(rows: number) {
  emit('page', { page: 0, first: 0, rows, pageCount: Math.max(1, Math.ceil(props.totalRecords / rows)) })
}
</script>
