<template>
  <div>
    <div v-if="!isMounted" class="fixed inset-0 flex items-center justify-center bg-black text-white z-50 transition-opacity duration-300">
      <ProgressSpinner class="w-10 h-10 text-white" strokeWidth="4" animationDuration="1s" />
    </div>
    <div v-if="isMounted" class="w-full bg-cover flex items-center justify-between bg-center bg-no-repeat py-1 transition-opacity duration-500 ease-in xl:pb-10" :style="{
      backgroundImage: `url(images/${colorMode.value === 'dark' ? 'mask.svg' : 'mask-light.svg'})`,
      backgroundColor: colorMode.value === 'dark' ? '#000000' : '#FFFFFF'
    }">
    <div class="w-full bg-cover max-w-[1700px] mx-auto flex items-center justify-between xl:flex-row flex-col-reverse bg-center bg-no-repeat py-1 transition-opacity duration-500 ease-in">
      <BaseContainer>
        <div class="w-full flex justify-between items-center xl:px-0 pr-5 !pl-[30px]">
          <Card class="max-w-full xl:max-w-[550px] mt-0 mb-[54px] xl:mb-0 text-white shadow-none border-none bg-transparent">
            <template #content>
              <p class="flex flex-col xl:mb-10 mb-3">
                <span class="xl:text-[68px] text-4xl text-center xl:text-left font-[BauhausNanoDisplayRegular] leading-[110%] dark:text-[#EDEEF2] text-[#101010]">
                  Data Evolution Made Simple
                </span>
              </p>
              <p class="xl:text-[22px] text-[17px] text-center xl:text-left dark:text-[#BBBBBF] text-black font-[InterRegular]">
                Streamline your management of complex data structures with our powerful
                open-source framework for schema evolution and data manipulation.
              </p>
              <div class="xl:mt-10 mt-7 flex justify-center xl:justify-start items-center gap-6">
                <Button label="Sign up for Beta" class="px-4 py-2 xl:text-lg text-[17px] rounded-lg bg-[#3651ED] text-white font-[InterMedium]" />
                <Button label="View on Github" class="px-4 py-2 xl:text-lg text-[17px] rounded-lg bg-[#3651ED]/10 text-[#3651ED] dark:bg-[#D8DEFF]/10 dark:text-[#EDEEF2] font-medium" outlined />
              </div>
            </template>
          </Card>
        </div>
      </BaseContainer>
      <div class="mb-11 xl:mb-0">
        <img src="/public/images/rectangle-cut.png" alt="Hero Section Image" class="max-w-[800px] w-full" />
      </div>
    </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ProgressSpinner from 'primevue/progressspinner'
import Button from 'primevue/button'
import Card from 'primevue/card'

const colorMode = useColorMode()
const isMounted = ref(false)

onMounted(() => {
  isMounted.value = true
})
</script>