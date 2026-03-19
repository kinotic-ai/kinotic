<template>
  <UModal v-model:open="modalState.isOpen">
    <template #content>
      <div class="xl:pt-[72px] pt-[54px] xl:px-[64px] px-[25px] xl:pb-[64px] pb-[35px] bg-white dark:bg-[#101010] text-center rounded-2xl">
        <img
          src="/icons/rocket.svg"
          alt="Rocket"
          class="xl:w-[200px] w-[136px] xl:h-[200px] h-[136px] mx-auto xl:mb-[48px] mb-6"
        />
        <h2 class="xl:text-[40px] text-[30px] font-normal dark:text-white/85 text-[#101010] font-[InterRegular] xl:mb-4 mb-3">
          Success!
        </h2>
        <p class="xl:text-lg text-[17px] dark:text-white/75 text-[#2B2A32] leading-relaxed">
          Thanks for joining the Structures Cloud early access list! We're excited to bring
          you a seamless, cloud-based experience for Structures. Expect an email with
          access details and updates as we approach the launch. Stay tuned!
        </p>
        <div class="mt-6">
          <UButton label="Close" variant="outline" @click="closeModal" />
        </div>
      </div>
    </template>
  </UModal>
</template>

<script setup>
import { useModal } from '~/composables/useModal'

const { modalState, closeModal } = useModal()
</script>
