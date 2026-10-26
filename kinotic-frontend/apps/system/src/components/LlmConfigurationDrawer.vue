<template>
  <FormDrawer :visible="visible" :title="isNew ? 'Add LLM configuration' : 'Edit LLM configuration'"
              description="A model the system's services call, selected by its name." @close="emit('update:visible', false)">
    <form id="llm-configuration-form" class="flex flex-col gap-5" @submit.prevent="submit">
      <div>
        <label for="llm-name" class="mb-2 block text-sm font-medium">Name</label>
        <InputText id="llm-name" v-model="name" placeholder="app-builder" autocomplete="off" :invalid="nameMissing" class="w-full font-mono" />
        <p v-if="nameMissing" class="mt-1.5 text-[0.8125rem] text-red-700 dark:text-red-400">Enter the name a service selects this configuration by.</p>
        <p v-else class="mt-1.5 text-[0.8125rem] text-muted-color">The name a service selects this configuration by, unique in the system.</p>
      </div>

      <div>
        <label for="llm-provider" class="mb-2 block text-sm font-medium">Provider</label>
        <Select input-id="llm-provider" :model-value="provider" :options="PROVIDERS" option-label="label" option-value="value" class="w-full"
                @update:model-value="selectProvider" />
      </div>

      <div>
        <label for="llm-model" class="mb-2 block text-sm font-medium">Model</label>
        <InputText id="llm-model" v-model="model" autocomplete="off" :invalid="modelMissing" class="w-full font-mono" />
        <p v-if="modelMissing" class="mt-1.5 text-[0.8125rem] text-red-700 dark:text-red-400">
          Enter the {{ LLM_PROVIDER_NAMES[provider] }} id of the model, such as {{ DEFAULT_MODELS[provider] }}.
        </p>
      </div>

      <div>
        <label for="llm-api-key" class="mb-2 block text-sm font-medium">API key</label>
        <Password input-id="llm-api-key" v-model="apiKey" :feedback="false" toggle-mask
                  :placeholder="configuration?.apiKeyHeld ? 'A key is held' : undefined"
                  :input-props="{ autocomplete: 'new-password' }" class="w-full" input-class="w-full" />
        <p v-if="configuration?.apiKeyHeld" class="mt-1.5 text-[0.8125rem] text-muted-color">Leave it empty to keep the key held for this configuration.</p>
      </div>

      <div>
        <label for="llm-endpoint" class="mb-2 flex items-center justify-between text-sm font-medium">
          Endpoint
          <span class="text-xs font-normal text-muted-color">Optional</span>
        </label>
        <InputText id="llm-endpoint" v-model="endpoint" placeholder="https://api.anthropic.com" autocomplete="off" class="w-full font-mono" />
        <p class="mt-1.5 text-[0.8125rem] text-muted-color">The base URL of the provider's API, when it is not the provider's public API.</p>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <label for="llm-max-tokens" class="mb-2 flex items-center justify-between text-sm font-medium">
            Max tokens
            <span class="text-xs font-normal text-muted-color">Optional</span>
          </label>
          <InputNumber input-id="llm-max-tokens" v-model="maxTokens" :min="1" :use-grouping="false" class="w-full" input-class="w-full" />
        </div>
        <div>
          <label for="llm-temperature" class="mb-2 flex items-center justify-between text-sm font-medium">
            Temperature
            <span class="text-xs font-normal text-muted-color">Optional</span>
          </label>
          <InputNumber input-id="llm-temperature" v-model="temperature" :min="0" :max="MAX_TEMPERATURES[provider]" :min-fraction-digits="0" :max-fraction-digits="2"
                       :invalid="!temperatureInRange" class="w-full" input-class="w-full" />
          <p v-if="!temperatureInRange" class="mt-1.5 text-[0.8125rem] text-red-700 dark:text-red-400">
            {{ LLM_PROVIDER_NAMES[provider] }} accepts a temperature from 0 to {{ MAX_TEMPERATURES[provider] }}.
          </p>
        </div>
      </div>
    </form>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
      <Button type="submit" form="llm-configuration-form" :label="isNew ? 'Add configuration' : 'Save'" :loading="saving" />
    </template>
  </FormDrawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import { useToast } from 'primevue/usetoast'
import { Kinotic } from '@kinotic-ai/core'
import { LlmConfiguration, LlmProviderType } from '@kinotic-ai/system-api'
import { FormDrawer, showErrorToast } from '@kinotic-ai/frontend-common'
import { LLM_PROVIDER_NAMES } from '@/util/LlmProviderNames'

/**
 * Adds an LLM configuration, or edits the one given, and saves it with the API key typed, if any.
 */
const props = defineProps<{
  visible: boolean
  configuration: LlmConfiguration | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'saved', configuration: LlmConfiguration): void
}>()

const PROVIDERS = Object.values(LlmProviderType).map(value => ({ label: LLM_PROVIDER_NAMES[value], value }))

// Anthropic accepts a temperature from 0 to 1, OpenAI from 0 to 2
const MAX_TEMPERATURES: Record<LlmProviderType, number> = {
  [LlmProviderType.ANTHROPIC]: 1,
  [LlmProviderType.OPENAI]: 2
}

const DEFAULT_MODELS: Record<LlmProviderType, string> = {
  [LlmProviderType.ANTHROPIC]: 'claude-opus-5-5',
  [LlmProviderType.OPENAI]: 'gpt-5'
}

const toast = useToast()

const name = ref('')
const provider = ref<LlmProviderType>(LlmProviderType.ANTHROPIC)
const model = ref('')
const apiKey = ref('')
const endpoint = ref('')
const maxTokens = ref<number | null>(null)
const temperature = ref<number | null>(null)
const saving = ref(false)
// set by the first submit, so a required field shows as missing only once the user has tried to save without it
const submitted = ref(false)

const isNew = computed(() => !props.configuration?.id)
const temperatureInRange = computed(() => temperature.value === null || temperature.value <= MAX_TEMPERATURES[provider.value])
const nameMissing = computed(() => submitted.value && name.value.trim() === '')
const modelMissing = computed(() => submitted.value && model.value.trim() === '')
const complete = computed(() => name.value.trim() !== '' && model.value.trim() !== '' && temperatureInRange.value)

// the fields start from the configuration being edited each time the drawer opens, and the typed key is dropped when it closes
watch(() => props.visible, visible => {
  if (visible) {
    const configuration = props.configuration
    name.value = configuration?.name ?? ''
    provider.value = configuration?.provider ?? LlmProviderType.ANTHROPIC
    model.value = configuration?.model ?? DEFAULT_MODELS[provider.value]
    apiKey.value = ''
    endpoint.value = configuration?.endpoint ?? ''
    maxTokens.value = configuration?.maxTokens ?? null
    temperature.value = configuration?.temperature ?? null
    submitted.value = false
  } else {
    apiKey.value = ''
  }
}, { immediate: true })

// the model follows the provider while it is still the previous provider's default, so switching provider never leaves
// another provider's model id behind
function selectProvider(selected: LlmProviderType): void {
  if (model.value.trim() === '' || model.value.trim() === DEFAULT_MODELS[provider.value]) {
    model.value = DEFAULT_MODELS[selected]
  }
  provider.value = selected
}

async function submit(): Promise<void> {
  submitted.value = true
  if (!complete.value) {
    return
  }
  const configuration = Object.assign(new LlmConfiguration(name.value.trim(), provider.value, model.value.trim()), {
    id: props.configuration?.id ?? null,
    created: props.configuration?.created ?? null,
    endpoint: endpoint.value.trim() || null,
    maxTokens: maxTokens.value,
    temperature: temperature.value
  })
  saving.value = true
  try {
    const saved = await Kinotic.llmConfigurations.save(configuration, apiKey.value.trim() || null)
    toast.add({ severity: 'success', summary: isNew.value ? 'Configuration added' : 'Configuration saved', life: 4000 })
    emit('saved', saved)
    emit('update:visible', false)
  } catch (err) {
    showErrorToast(toast, 'Failed to save the configuration', err, { life: 8000 })
  } finally {
    saving.value = false
  }
}
</script>
