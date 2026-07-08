<template>
  <div class="flex justify-between h-screen max-w-[1440px] mx-auto">
    <div class="pt-6 pl-8 w-1/2 flex flex-col">
      <div class="flex justify-content-start items-center">
        <Button
          icon="pi pi-times"
          class="p-button-text p-button-plain mr-3"
          @click="close"
          aria-label="Close"
        />
        <span class="text-black text-xl">Create Application</span>
      </div>
      <div class="mt-[260px] w-4/6 my-auto">
        <h2 class="text-3xl">
          Let's begin by choosing a name for your application.
        </h2>
        <div class="my-[60px]">
          <InputText
            v-model="syncedEntity.name"
            :disabled="!identityEditable"
            :placeholder="identityLabel"
            class="w-full max-w-[540px] h-[56px]"
            :class="{ 'p-invalid': !valid && rulesForIdentity.length > 0 }"
            @input="validateIdentity()"
          />
          <small
            v-if="!valid && rulesForIdentity.length > 0"
            class="p-error block mt-1"
          >
            {{ identityLabel }} is required
          </small>
        </div>

        <div class="mt-4">
          <Button
            label="Create"
            class="rounded-[10px] max-h-[56px] !py-[18px] !w-4/6 !text-base"
            @click="save"
          />
        </div>
      </div>
    </div>

    <div class="w-1/2 h-full relative">
      <img
        src="@/assets/create-application-image.svg"
        alt="Create Application Image"
        class="absolute left-0 top-0 max-h-[100vh]"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { CrudServiceProxyFactory, Kinotic, type ICrudServiceProxy, type Identifiable } from '@kinotic-ai/core'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'

type RuleValidator = (value: string) => string | boolean

interface ApplicationEntity extends Identifiable<string> {
  id: string
  name?: string
  description?: string
}

const props = withDefaults(defineProps<{
  crudServiceIdentifier: string
  title: string
  identity?: string | null
  identityLabel?: string
  identityRules?: RuleValidator[]
  identityEditable?: boolean
  showBasicInfoSubheader?: boolean
  entity?: ApplicationEntity
}>(), {
  identity: null,
  identityLabel: 'Enter your name here',
  identityRules: () => [],
  identityEditable: true,
  showBasicInfoSubheader: true,
  entity: () => ({ id: '' }),
})

const emit = defineEmits<{
  (e: 'beforeSave', entity: ApplicationEntity): void
  (e: 'afterLoad', item: ApplicationEntity): void
}>()

const router = useRouter()
const toast = useToast()

const syncedEntity = ref<ApplicationEntity>({ id: '' })
const crudServiceProxy = ref<ICrudServiceProxy<ApplicationEntity>>()
const editing = ref(false)
const valid = ref(true)
const loading = ref(false)
const rulesForIdentity = ref<RuleValidator[]>([])

onMounted(async () => {
  rulesForIdentity.value =
    props.identityRules.length > 0
      ? props.identityRules
      : [(v: string) => !!v || `${props.identityLabel} is required`]

  try {
    crudServiceProxy.value = new CrudServiceProxyFactory(Kinotic.serviceRegistry).crudServiceProxy(props.crudServiceIdentifier)

    if (props.identity !== null) {
      editing.value = true
      loading.value = true

      const item = await crudServiceProxy.value.findById(props.identity)
      syncedEntity.value = item
      afterLoad(item)
      loading.value = false
    }
  } catch (error: unknown) {
    loading.value = false
    displayAlert((error as Error).message || 'Error connecting or loading data')
  }
})

function close() {
  router.back()
}

function validateIdentity() {
  valid.value = rulesForIdentity.value.every(rule => rule(syncedEntity.value.name ?? '') === true)
}

async function save() {
  validateIdentity()
  if (!valid.value) return

  loading.value = true
  beforeSave()

  try {
    if (!editing.value) {
      await crudServiceProxy.value!.create(syncedEntity.value)
    } else {
      await crudServiceProxy.value!.save(syncedEntity.value)
    }

    router.push({ path: '/application', query: { created: 'true' } })
  } catch (error: unknown) {
    displayAlert((error as Error).message)
  }

  loading.value = false
}

function beforeSave() {
  emit('beforeSave', syncedEntity.value)
}

function afterLoad(item: ApplicationEntity) {
  emit('afterLoad', item)
}

function displayAlert(text: string) {
  toast.add({
    severity: 'error',
    summary: 'Error',
    detail: text,
    life: 3000
  })
}
</script>