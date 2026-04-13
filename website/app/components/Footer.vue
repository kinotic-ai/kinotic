<template>
  <footer class="bg-[#101010] text-center text-xs text-[#AEAFB2] py-[40px]">
    <BaseContainer>
      <div class="flex flex-col items-center gap-3 sm:flex-row sm:justify-center sm:gap-6">
        <span>
          Copyright © 2018-present Kinotic
        </span>
        <nav class="flex items-center gap-6">
          <NuxtLink to="/terms" class="hover:text-white transition">
            Terms of Service
          </NuxtLink>
          <NuxtLink to="/privacy" class="hover:text-white transition">
            Privacy Policy
          </NuxtLink>
        </nav>
      </div>
    </BaseContainer>
  </footer>
</template>
