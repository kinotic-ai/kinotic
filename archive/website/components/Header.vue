<script setup>
const isMounted = ref(false)
const isMenuOpen = ref(false)

onMounted(() => {
  isMounted.value = true
})

watch(isMenuOpen, (val) => {
  if (val) {
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
})
</script>


<template>
  <header
    v-if="isMounted"
    class="bg-[#101010] text-[#EDEEF2] shadow-md sticky top-0 left-0 z-50 py-5"
  >
    <BaseContainer>
      <nav class="mx-auto flex justify-between items-center px-5 xl:px-0">
        <NuxtLink
          to="/"
          class="text-lg font-bold"
          v-show="!isMenuOpen"
        >
          <img src="/icons/logo.svg" alt="logo" />
        </NuxtLink>

        <div class="hidden lg:flex space-x-6 items-center">
          <label class="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              class="sr-only peer"
              :checked="$colorMode.preference === 'dark'"
              @change="$colorMode.preference = $colorMode.preference === 'dark' ? 'light' : 'dark'"
            />
            <div
              class="w-11 h-6 bg-gray-300 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-[#E0E5FF]/20 rounded-full peer dark:bg-gray-600 peer-checked:bg-[#E0E5FF]/20 transition-all"
            ></div>
            <div
              class="absolute left-0.5 top-0.5 w-5 h-5 rounded-full transition-transform peer-checked:translate-x-full flex items-center justify-center
                     bg-white dark:bg-black"
            >
              <img
                :src="$colorMode.preference === 'dark' ? 'icons/moon.svg' : 'icons/sun.svg'"
                alt="theme icon"
                class="w-3.5 h-3.5"
              />
            </div>
          </label>

          <NuxtLink to="https://structuresframework.org/webdocs/guide/overview.html" class="text-[#EDEEF2] text-sm">Guide</NuxtLink>
          <NuxtLink to="https://structuresframework.org/webdocs/reference/structures-config.html" class="text-[#EDEEF2] text-sm">Reference</NuxtLink>
          <NuxtLink to="https://structuresframework.org/allure/index.html" class="text-[#EDEEF2] text-sm">Test Status</NuxtLink>
          <NuxtLink
            to="https://github.com/MindIgnied/structures"
            class="flex items-center gap-2 rounded-lg px-3 py-2 bg-[#2B2A32] text-sm"
          >
            <img src="/icons/git.svg" alt="GitHub" class="w-5 h-5" />
            <span class="text-[#EDEEF2]">View on GitHub</span>
          </NuxtLink>
        </div>
        <button
          class="lg:hidden flex items-center"
          @click="isMenuOpen = !isMenuOpen"
        >
          <img src="/icons/burger.svg" alt="Menu" class="w-6 h-6" />
        </button>
      </nav>

      <div
        v-if="isMenuOpen"
        class="fixed top-0 left-0 w-full h-screen bg-[#101010] flex flex-col justify-start px-[40px] py-6 z-50"
      >
        <div class="flex justify-between items-center mb-10">
          <label class="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              class="sr-only peer"
              :checked="$colorMode.preference === 'dark'"
              @change="$colorMode.preference = $colorMode.preference === 'dark' ? 'light' : 'dark'"
            />
            <div
              class="w-11 h-6 bg-gray-300 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-[#E0E5FF]/20 rounded-full peer dark:bg-gray-600 peer-checked:bg-[#E0E5FF]/20 transition-all"
            ></div>
            <div
              class="absolute left-0.5 top-0.5 w-5 h-5 rounded-full transition-transform peer-checked:translate-x-full flex items-center justify-center
                     bg-white dark:bg-black"
            >
              <img
                :src="$colorMode.preference === 'dark' ? 'icons/moon.svg' : 'icons/sun.svg'"
                alt="theme icon"
                class="w-3.5 h-3.5"
              />
            </div>
          </label>

          <button @click="isMenuOpen = false">
            <img src="/icons/close.svg" alt="Close menu" class="w-6 h-6" />
          </button>
        </div>

         <div class="flex flex-col justify-between items-center gap-6 h-screen">
          <div class="flex flex-col justify-between items-center gap-8">
            <NuxtLink to="https://structuresframework.org/webdocs/guide/overview.html" class="text-[#EDEEF2] text-sm" @click="isMenuOpen = false">Guide</NuxtLink>
            <NuxtLink to="https://structuresframework.org/webdocs/reference/structures-config.html" class="text-[#EDEEF2] text-sm" @click="isMenuOpen = false">Reference</NuxtLink>
            <NuxtLink to="https://structuresframework.org/allure/index.html" class="text-[#EDEEF2] text-sm" @click="isMenuOpen = false">Test Status</NuxtLink>
          </div>
          <NuxtLink
            to="https://github.com/kinotic-ai/kinotic"
            class="flex items-center justify-center gap-2 rounded-lg px-3 py-2 bg-[#2B2A32] w-full xl:w-fit"
            @click="isMenuOpen = false"
          >
            <img src="/icons/git.svg" alt="GitHub" class="w-5 h-5" />
            <span class="text-[#EDEEF2] text-sm">View on GitHub</span>
          </NuxtLink>
        </div>
      </div>
    </BaseContainer>
  </header>
</template>
