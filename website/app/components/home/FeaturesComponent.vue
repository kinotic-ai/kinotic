<script setup>
const colorMode = useColorMode()
const isMounted = ref(false)

onMounted(() => {
  isMounted.value = true
})

const features = [
  {
    title: "Instant data layer",
    details:
      "Describe your data models and Kinotic gives you a complete API with search, pagination, and real-time updates -- no endpoints to build.",
    icon: "database.png",
    iconLight: "database-light.png",
  },
  {
    title: "Services that just connect",
    details:
      "Build services in any language and they're instantly reachable from any frontend, backend, or AI agent in your application.",
    icon: "share.png",
    iconLight: "share-light.png",
  },
  {
    title: "Security built in",
    details:
      "Fine-grained access control is part of the platform, not something you bolt on. Define who can do what in plain language and the platform enforces it everywhere.",
    icon: "lightning.png",
    iconLight: "lightning-light.png",
  },
  {
    title: "Ship day one",
    details:
      "CI/CD, observability, and deployment are included from the start. The app you prototype today is the same app you run in production.",
    icon: "apps.png",
    iconLight: "apps-light.png",
  },
]
</script>

<template>
  <BaseContainer>
    <section class="xl:pt-[120px] pt-[52px] xl:px-0 px-5 dark:bg-[#101010]">
      <h1
        class="font-[BauhausNanoDisplayRegular] xl:text-[40px] text-[30px] mb-[48px] text-center text-[#101010] dark:text-[#EDEEF2]">
        Build Apps, Not Infrastructure
      </h1>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-[43px] xl:pb-[70px] pb-[52px] mx-auto">
        <div v-for="(f, i) in features" :key="i"
          class="border border-[#EDEEF2] dark:border-[#2B2A32] bg-transparent xl:p-8 p-5 rounded-3xl">
          <div class="flex xl:gap-7 gap-4">
            <img v-if="isMounted" :src="`/icons/${colorMode.value === 'dark' ? f.icon : f.iconLight}`"
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
