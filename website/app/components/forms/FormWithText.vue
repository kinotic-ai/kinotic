<script setup>
import { useModal } from '~/composables/useModal'

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
  <section id="contact" v-if="isMounted" :style="{ background: gradientStyle }" class="px-5 py-16 xl:px-0 xl:py-24">
    <BaseContainer>
      <div class="mx-auto overflow-hidden rounded-[32px] border border-white/8 bg-[#16161A] px-6 py-10 shadow-[0_35px_80px_rgba(0,0,0,0.24)] md:flex md:justify-between md:gap-10 xl:px-10 xl:py-14">
        <div class="mb-10 md:mb-0 md:w-[42%]">
          <span class="mb-4 inline-flex rounded-full border border-[#20E6A7]/20 bg-[#20E6A7]/10 px-3 py-1 text-xs uppercase tracking-[0.22em] text-[#7DEAC7]">
            Build with Kinotic
          </span>
          <h2
            class="text-center font-[BauhausNanoDisplayBold] text-[34px] leading-tight text-[#EDEEF2] xl:text-left xl:text-5xl">
            Launch your next<br />
            data workflow with<br />
            Kinotic
          </h2>
          <p class="mt-5 max-w-[420px] text-center text-base leading-7 text-[#A8AFBB] xl:text-left">
            Keep the form for now, swap the copy later, and ship the refreshed brand today.
          </p>
        </div>

        <form @submit.prevent="handleSubmit" class="grid grid-cols-1 gap-6 md:w-[52%] sm:grid-cols-2">
          <div>
            <label class="mb-2 block text-sm font-[InterRegular] text-[#BBBBBF]">Name</label>
            <UInput
              v-model="formData.name"
              placeholder="Enter name"
              class="w-full"
              input-class="h-[52px] rounded-xl border border-white/10 bg-white/5 px-4 text-white placeholder:text-[#7B8190] outline-none focus:ring-2 focus:ring-[#FF2D7A]/40"
            />
          </div>
          <div>
            <label class="mb-2 block text-sm font-[InterRegular] text-[#BBBBBF]">Email</label>
            <UInput
              v-model="formData.email"
              type="email"
              placeholder="Enter email"
              class="w-full"
              input-class="h-[52px] rounded-xl border border-white/10 bg-white/5 px-4 text-white placeholder:text-[#7B8190] outline-none focus:ring-2 focus:ring-[#FF2D7A]/40"
            />
          </div>
          <div>
            <label class="mb-2 block text-sm font-[InterRegular] text-[#BBBBBF]">Company name</label>
            <UInput
              v-model="formData.company"
              placeholder="Company"
              class="w-full"
              input-class="h-[52px] rounded-xl border border-white/10 bg-white/5 px-4 text-white placeholder:text-[#7B8190] outline-none focus:ring-2 focus:ring-[#FF2D7A]/40"
            />
          </div>
          <div>
            <label class="mb-2 block text-sm font-[InterRegular] text-[#BBBBBF]">Number of employees</label>
            <USelect
              v-model="formData.employee"
              :items="employees"
              value-key="value"
              label-key="label"
              placeholder="Select"
              class="w-full"
              select-class="h-[52px] rounded-xl border border-white/10 bg-white/5 pl-4 py-3 pr-3 text-white outline-none focus:ring-2 focus:ring-[#FF2D7A]/40"
            />
          </div>
          <div class="col-span-1 sm:col-span-2">
            <UButton
              type="submit"
              label="Request Early Access"
              class="h-[52px] w-full rounded-full bg-[linear-gradient(135deg,#FF2D7A_0%,#FF4D5E_100%)] text-white transition hover:opacity-95 xl:w-[220px]"
            />
          </div>
        </form>
      </div>
    </BaseContainer>
  </section>
</template>
