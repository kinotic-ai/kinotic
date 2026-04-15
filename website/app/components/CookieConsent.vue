<script setup lang="ts">
defineEmits<{
  decision: [accepted: boolean]
}>()
</script>

<template>
  <div
    class="fixed inset-x-4 bottom-4 z-[100] mx-auto max-w-3xl rounded-2xl border border-white/10 bg-[#1A1A1C] p-5 shadow-2xl sm:p-6"
    role="dialog"
    aria-live="polite"
    aria-label="Cookie consent"
  >
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <p class="font-[InterRegular] text-[14px] leading-[150%] text-[#AFAFB4]">
        We use Microsoft Clarity to understand how visitors use our site. By
        default we collect only anonymous, cookieless data. Accept to allow
        cookies and anonymized session recordings so we can improve the site
        further.
        <a href="/privacy" class="text-white underline hover:text-[#FF2D7A]">
          Learn more
        </a>.
      </p>
      <div class="flex shrink-0 items-center gap-3">
        <button
          type="button"
          class="inline-flex h-[40px] items-center justify-center rounded-full border border-white/20 px-5 font-[InterMedium] text-[13px] text-white transition hover:bg-white/5"
          @click="$emit('decision', false)"
        >
          Decline
        </button>
        <button
          type="button"
          class="inline-flex h-[40px] items-center justify-center rounded-full bg-[linear-gradient(135deg,#FF2D7A_0%,#FF2A55_100%)] px-5 font-[InterMedium] text-[13px] text-white shadow-[0_15px_30px_rgba(255,45,122,0.28)] transition hover:scale-[1.02]"
          @click="$emit('decision', true)"
        >
          Accept
        </button>
      </div>
    </div>
  </div>
</template>
