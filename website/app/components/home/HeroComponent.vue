<template>
  <div>
    <div v-if="!isMounted" class="fixed inset-0 flex items-center justify-center bg-black text-white z-50 transition-opacity duration-300">
      <div class="animate-spin w-10 h-10 border-4 border-white border-t-transparent rounded-full"></div>
    </div>
    <div v-if="isMounted" class="w-full bg-cover flex items-center justify-between bg-center bg-no-repeat py-1 transition-opacity duration-500 ease-in xl:pb-10" :style="{
      backgroundImage: `url(images/${colorMode.value === 'dark' ? 'mask.svg' : 'mask-light.svg'})`,
      backgroundColor: colorMode.value === 'dark' ? '#000000' : '#FFFFFF'
    }">
      <div class="w-full bg-cover max-w-[1700px] mx-auto flex items-center justify-between xl:flex-row flex-col-reverse bg-center bg-no-repeat py-1 transition-opacity duration-500 ease-in">
        <BaseContainer>
          <div class="w-full flex justify-between items-center xl:px-0 pr-5 !pl-[30px]">
            <div class="max-w-full xl:max-w-[550px] mt-0 mb-[54px] xl:mb-0 text-white shadow-none border-none bg-transparent">
              <p class="flex flex-col xl:mb-10 mb-3">
                <span class="xl:text-[68px] text-4xl text-center xl:text-left font-[BauhausNanoDisplayRegular] leading-[110%] dark:text-[#EDEEF2] text-[#101010]">
                  Vibe Coding at Scale
                </span>
              </p>
              <p class="xl:text-[22px] text-[17px] text-center xl:text-left dark:text-[#BBBBBF] text-black font-[InterRegular]">
                Build enterprise-grade applications at internet scale. Kinotic OS abstracts
                away the complexity of cloud infrastructure so you can focus on what matters.
              </p>
              <div class="xl:mt-10 mt-7 flex justify-center xl:justify-start items-center gap-6">
                <UButton
                  label="Sign up for Beta"
                  class="px-4 py-2 xl:text-lg text-[17px] rounded-lg bg-[#3651ED] text-white font-[InterMedium]"
                />
                <UButton
                  label="View on Github"
                  variant="outline"
                  class="px-4 py-2 xl:text-lg text-[17px] rounded-lg bg-[#3651ED]/10 text-[#3651ED] dark:bg-[#D8DEFF]/10 dark:text-[#EDEEF2] font-medium"
                />
              </div>
            </div>
          </div>
        </BaseContainer>
        <div class="mb-11 xl:mb-0">
          <img src="/images/rectangle-cut.png" alt="Hero Section Image" class="max-w-[800px] w-full" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const colorMode = useColorMode()
const isMounted = ref(false)

onMounted(() => {
  isMounted.value = true
})
</script>
