```vue
<script setup>
import { useModal } from '~/composables/useModal'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import Button from 'primevue/button'
import { ref, computed, onMounted } from 'vue'

const { openModal } = useModal()
const employees = [
  { label: '1-10', value: '1-10' },
  { label: '11-50', value: '11-50' },
  { label: '51-200', value: '51-200' },
  { label: '201+', value: '201+' },
]
const formData = ref({
  name: '',
  email: '',
  company: '',
  employee: null,
})
const colorMode = useColorMode()
const isMounted = ref(false)
const windowWidth = ref(0)

onMounted(() => {
  isMounted.value = true
  windowWidth.value = window.innerWidth
  window.addEventListener('resize', () => {
    windowWidth.value = window.innerWidth
  })
})

const gradientStyle = computed(() => {
  const isDark = colorMode.value === 'dark'
  const isSmallScreen = windowWidth.value < 1280

  if (isSmallScreen) {
    return isDark
      ? 'linear-gradient(117deg, rgba(16, 16, 16, 1) 0%, rgba(54, 81, 237, 0.28) 100%)'
      : 'linear-gradient(117deg, rgba(255, 255, 255, 1) 0%, rgba(128, 147, 255, 0.28) 100%)'
  } else {
    return isDark
      ? 'linear-gradient(171deg, rgba(16, 16, 16, 1) 0%, rgba(54, 81, 237, 0.28) 100%)'
      : 'linear-gradient(171deg, rgba(255, 255, 255, 1) 0%, rgba(128, 147, 255, 0.28) 100%)'
  }
})

const handleSubmit = () => {
  openModal()
  console.log('Form Data:', formData.value)
}
</script>

<template>
  <section v-if="isMounted" :style="{ background: gradientStyle }">
    <BaseContainer>
      <div class="mx-auto rounded-xl xl:py-20 py-12 pb-5 xl:px-0 px-5 md:flex md:justify-between gap-10">
        <div class="md:w-1/2 mb-10 md:mb-0">
          <h2
            class="text-[#101010] dark:text-[#EDEEF2] text-[30px] xl:text-4xl font-[BauhausNanoDisplayRegular] leading-tight text-center xl:text-left">
            Sign up to Get<br />
            Early Access to<br />
            Structures Cloud
          </h2>
        </div>

        <form @submit.prevent="handleSubmit" class="md:w-1/2 grid grid-cols-1 sm:grid-cols-2 gap-6">
          <div>
            <label class="block text-sm text-[#2B2A32] dark:text-[#BBBBBF] font-[InterRegular] mb-2">Name</label>
            <InputText
              v-model="formData.name"
              placeholder="Enter name"
              class="w-full h-[48px] px-4 border border-[#CBD5E1] dark:border-none rounded-lg bg-white dark:bg-[#FFFFFF]/10 text-black dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm text-[#2B2A32] dark:text-[#BBBBBF] font-[InterRegular] mb-2">Email</label>
            <InputText
              v-model="formData.email"
              type="email"
              placeholder="Enter email"
              class="w-full h-[48px] px-4 border border-[#CBD5E1] dark:border-none rounded-lg bg-white dark:bg-[#FFFFFF]/10 text-black dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm text-[#2B2A32] dark:text-[#BBBBBF] font-[InterRegular] mb-2">Company name</label>
            <InputText
              v-model="formData.company"
              placeholder="Company"
              class="w-full h-[48px] px-4 border border-[#CBD5E1] dark:border-none rounded-lg bg-white dark:bg-[#FFFFFF]/10 text-black dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm text-[#2B2A32] dark:text-[#BBBBBF] font-[InterRegular] mb-2">Number of employees</label>
            <Dropdown
              v-model="formData.employee"
              :options="employees"
              optionLabel="label"
              optionValue="value"
              placeholder="Select"
              class="w-full h-[48px] pl-4 py-3 pr-3 border border-[#CBD5E1] dark:border-none rounded-lg bg-white dark:bg-[#FFFFFF]/10 text-black dark:text-white outline-none focus:ring-2 focus:ring-blue-500"
              />
          </div>
          <div class="col-span-1 sm:col-span-2">
            <Button
              type="submit"
              label="Sign up for Beta"
              class="xl:w-1/4 w-full h-[48px] bg-[#3651ED] text-white rounded-md font-medium hover:bg-blue-600 transition"
            />
          </div>
        </form>
      </div>
    </BaseContainer>
  </section>
</template>
<style>
.p-dialog-header {
  justify-content: flex-end !important
}
</style>