<template>
  <div class="overflow-hidden rounded-lg border border-surface-200 bg-surface-0 transition-colors focus-within:border-surface-400 dark:border-surface-700 dark:bg-surface-950 dark:focus-within:border-surface-500">
    <div class="flex items-center justify-between gap-3 border-b border-surface-200 bg-surface-50 px-3 py-2 dark:border-surface-700 dark:bg-surface-900">
      <span class="flex min-w-0 items-center gap-2 text-[13px] text-surface-700 dark:text-surface-200">
        <FileCode :size="15" :stroke-width="1.75" class="shrink-0 text-surface-500" aria-hidden="true" />
        <span class="truncate">{{ fileName }}</span>
      </span>
      <button type="button"
              class="flex h-7 w-7 items-center justify-center rounded-md text-surface-500 transition-colors hover:bg-surface-200/70 hover:text-surface-950 dark:hover:bg-surface-800 dark:hover:text-surface-0"
              :aria-label="copied ? 'Copied' : 'Copy'" v-tooltip.top="copied ? 'Copied' : 'Copy'" @click="copy">
        <Check v-if="copied" :size="14" :stroke-width="2" class="text-green-600 dark:text-green-400" aria-hidden="true" />
        <Copy v-else :size="14" :stroke-width="1.75" aria-hidden="true" />
      </button>
    </div>

    <div class="flex max-h-[30rem] overflow-auto">
      <!-- the gutter stays put while the code scrolls sideways -->
      <div class="sticky left-0 z-[1] shrink-0 select-none border-r border-surface-100 bg-surface-50/80 py-3 text-right font-mono text-[13px] leading-6 text-surface-400 dark:border-surface-800 dark:bg-surface-900/80 dark:text-surface-500"
           aria-hidden="true">
        <div v-for="line in lineCount" :key="line" class="px-3 tabular-nums">{{ line }}</div>
      </div>
      <!-- The textarea and the highlighted copy share one grid cell, so the copy sizes the cell and the
           transparent textarea on top takes the input while the copy shows its colours -->
      <div class="grid w-max shrink-0 grow">
        <pre class="pointer-events-none m-0 whitespace-pre px-4 py-3 font-mono text-[13px] leading-6 text-surface-800 [grid-area:1/1] dark:text-surface-100"
             aria-hidden="true" v-html="highlighted" />
        <textarea :id="id" :value="modelValue" :aria-label="ariaLabel" wrap="off" spellcheck="false"
                  autocapitalize="off" autocomplete="off"
                  class="m-0 resize-none overflow-hidden whitespace-pre border-0 bg-transparent px-4 py-3 font-mono text-[13px] leading-6 text-transparent caret-surface-950 outline-none [grid-area:1/1] selection:bg-sky-200/70 dark:caret-surface-0 dark:selection:bg-sky-500/30"
                  @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, Copy, FileCode } from '@lucide/vue'

/**
 * A code field for a Handlebars template, with line numbers and syntax colours. In HTML mode tags,
 * attributes, strings and comments are coloured; in both modes the {{variables}} stand out.
 */
const props = withDefaults(defineProps<{
  modelValue: string
  /** Whether the source is HTML, which colours its markup, or plain text. */
  html?: boolean
  fileName: string
  id?: string
  ariaLabel?: string
  /** How many lines the field shows before it grows with its content. */
  minLines?: number
}>(), {
  html: false,
  minLines: 14
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const TONE = {
  punctuation: 'text-surface-400 dark:text-surface-500',
  tag: 'text-sky-700 dark:text-sky-300',
  attribute: 'text-violet-600 dark:text-violet-300',
  string: 'text-emerald-700 dark:text-emerald-300',
  comment: 'italic text-surface-400 dark:text-surface-500',
  variable: 'rounded-sm bg-indigo-50 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300'
}

// A Handlebars expression, double or triple braced; an unclosed one runs to the end of its line while it is typed
const VARIABLE = /\{\{\{?[^\n]*?(?:\}\}\}?|(?=\n)|$)/
const HTML_TOKEN = new RegExp(`<!--[\\s\\S]*?(?:-->|$)|${VARIABLE.source}|<\\/?[A-Za-z][^>]*>?`, 'g')
const PLAIN_TOKEN = new RegExp(VARIABLE.source, 'g')
const ATTRIBUTE_TOKEN = /([^\s=/>"']+)(\s*=\s*)?("[^"]*"?|'[^']*'?)?|\s+|[\s\S]/g

const copied = ref(false)

const lineCount = computed(() => Math.max(props.minLines, props.modelValue.split('\n').length))

const highlighted = computed(() => {
  const source = props.modelValue
  const pattern = props.html ? HTML_TOKEN : PLAIN_TOKEN
  let out = ''
  let last = 0
  for (const match of source.matchAll(pattern)) {
    out += escape(source.slice(last, match.index))
    out += highlightToken(match[0])
    last = match.index + match[0].length
  }
  out += escape(source.slice(last))
  // padding the empty lines below keeps the cell as tall as the gutter, and a trailing newline keeps its last line
  return out + '\n'.repeat(Math.max(1, lineCount.value - source.split('\n').length + 1))
})

function escape(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function span(tone: keyof typeof TONE, text: string): string {
  return `<span class="${TONE[tone]}">${escape(text)}</span>`
}

/** A string with any variables inside it marked out. */
function withVariables(tone: keyof typeof TONE, text: string): string {
  let out = ''
  let last = 0
  for (const match of text.matchAll(PLAIN_TOKEN)) {
    out += text.slice(last, match.index) ? span(tone, text.slice(last, match.index)) : ''
    out += span('variable', match[0])
    last = match.index + match[0].length
  }
  return out + (text.slice(last) ? span(tone, text.slice(last)) : '')
}

function highlightToken(token: string): string {
  let ret: string
  if (token.startsWith('<!--')) {
    ret = span('comment', token)
  } else if (token.startsWith('{{')) {
    ret = span('variable', token)
  } else {
    ret = highlightTag(token)
  }
  return ret
}

function highlightTag(token: string): string {
  const [, open, name, rest] = token.match(/^(<\/?)([A-Za-z][\w-]*)([\s\S]*)$/) ?? ['', '<', '', token.slice(1)]
  const close = rest.match(/\/?>$/)?.[0] ?? ''
  const attributes = rest.slice(0, rest.length - close.length)
  let out = span('punctuation', open) + span('tag', name)
  for (const [part, attribute, equals, value] of attributes.matchAll(ATTRIBUTE_TOKEN)) {
    if (attribute) {
      out += span('attribute', attribute)
      out += equals ? span('punctuation', equals) : ''
      out += value ? withVariables('string', value) : ''
    } else {
      out += escape(part)
    }
  }
  return out + (close ? span('punctuation', close) : '')
}

async function copy(): Promise<void> {
  await navigator.clipboard.writeText(props.modelValue)
  copied.value = true
  setTimeout(() => {
    copied.value = false
  }, 1500)
}
</script>
