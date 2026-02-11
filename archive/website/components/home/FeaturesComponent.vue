<script setup>
import BaseContainer from '~/components/BaseContainer.vue'
import { ref, onMounted } from 'vue'

const colorMode = useColorMode()
const isMounted = ref(false)

onMounted(() => {
  isMounted.value = true
})

const features = [
  {
    title: "Simplified data management",
    details:
      "Structures streamlines the process of managing complex data structures, allowing developers to easily create, read, update, and delete data.",
    icon: "database.png",
    iconLight: "database-light.png",
  },
  {
    title: "Efficient schema evolution",
    details:
      "The framework supports schema evolution, making it easy for developers to modify data schemas over time without requiring significant changes to their application code.",
    icon: "share.png",
    iconLight: "share-light.png",
  },
  {
    title: "Powerful data manipulation",
    details:
      "Structures provides developers with a powerful set of tools for data manipulation, enabling them to efficiently process and analyze complex data sets.",
    icon: "lightning.png",
    iconLight: "lightning-light.png",
  },
  {
    title: "Flexible and extensible",
    details:
      "The framework is highly flexible and extensible, allowing developers to easily integrate Structures into their existing applications and workflows.",
    icon: "apps.png",
    iconLight: "apps-light.png",
  },
];
</script>
<template>
  <BaseContainer>
    <section class="xl:pt-[120px] pt-[52px] xl:px-0 px-5 dark:bg-[#101010]">
      <h1
        class="font-[BauhausNanoDisplayRegular] xl:text-[40px] text-[30px] mb-[48px] text-center text-[#101010] dark:text-[#EDEEF2]">
        Powerhouse Tools for Developers
      </h1>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-[43px] xl:pb-[70px] pb-[52px] mx-auto">
        <div v-for="(f, i) in features" :key="i"
          class="border border-[#EDEEF2] dark:border-[#2B2A32] bg-transparent xl:p-8 p-5 rounded-3xl">
          <div class="flex xl:gap-7 gap-4">
            <img v-if="isMounted" :src="`icons/${colorMode.value === 'dark' ? f.icon : f.iconLight}`"
              :alt="f.title" class="xl:w-[56px] xl:h-[56px] w-[52px] h-[52px]" />
            <div class="flex flex-col">
              <h3
                class="xl:text-xl text-[19px] dark:text-[#EDEEF2] text-[#101010] mb-3 font-[BauhausNanoDisplayMedium]">
                {{ f.title }}
              </h3>
              <p class="dark:text-[#BBBBBF] text-[#2B2A32] xl:text-lg text-[17px] font-[InterRegular]">
                {{ f.details }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </BaseContainer>
</template>