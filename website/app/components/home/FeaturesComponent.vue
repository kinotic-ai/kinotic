<script setup>
const features = [
  {
    title: "Schema Evolution",
    details:
      "Structures supports schema evolution, allowing developers to modify data schemas over time without requiring significant changes to their application code.",
    icon: "account_tree.svg",
  },
  {
    title: "Data Management",
    details:
      "Structures provides developers with a comprehensive set of tools for managing data, including the ability to create, read, update, and delete data.",
    icon: "database.svg",
  },
  {
    title: "User-Friendly GUI",
    details:
      "Structures provides a user-friendly graphical user interface built with Continuum, as well as OpenAPI and GraphQL interfaces for interacting with data.",
    icon: "touch_app.svg",
  },
]
</script>

<template>
  <section class="bg-[#171717] px-5 py-16 lg:px-[110px] lg:pb-[80px] lg:pt-[120px]">
    <div class="mx-auto w-full max-w-[1440px]">
      <div class="mx-auto max-w-[1220px] text-center">
        <h2 class="font-[InterBold] text-[34px] leading-[120%] tracking-[-0.02em] text-white xl:text-[49px]">
          Lorem ipsum dolor sit amet consectetur
        </h2>
      </div>

      <div class="mx-auto mt-14 grid max-w-[1200px] gap-7 lg:grid-cols-3 xl:mt-16 xl:gap-8">
        <article
          v-for="feature in features"
          :key="feature.title"
          class="min-h-[336px] rounded-[16px] border border-white/5 bg-[#242424] p-[32px] shadow-[0_30px_60px_rgba(0,0,0,0.18)] transition hover:-translate-y-1 hover:border-[#20E6A7]/20"
        >
          <div class="mb-8 inline-flex h-14 w-14 items-center justify-center rounded-xl bg-[#10392F]">
            <img :src="`/icons/${feature.icon}`" :alt="feature.title" class="h-11 w-11 object-contain" />
          </div>
          <h3 class="font-[InterSemiBold] text-[31px] leading-[120%] tracking-[-0.02em] text-white">
            {{ feature.title }}
          </h3>
          <p class="mt-4 font-[InterRegular] text-[16px] leading-[140%] tracking-[0] text-[#AFAFB4]">
            {{ feature.details }}
          </p>
        </article>
      </div>
    </div>
  </section>
</template>
