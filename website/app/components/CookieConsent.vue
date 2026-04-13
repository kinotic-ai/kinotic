<script setup lang="ts">
import { isEuOrUkVisitor, loadClarity } from '~/utils/clarity'

const STORAGE_KEY = 'kinotic-clarity-consent'

const config = useRuntimeConfig()
const projectId = (config.public.clarityProjectId as string | undefined) || ''

const visible = ref(false)

function persist(value: 'accepted' | 'declined') {
  try {
    localStorage.setItem(STORAGE_KEY, value)
  } catch {
    // ignore (private mode, etc.)
  }
}

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function accept() {
  persist('accepted')
  visible.value = false
  if (projectId) loadClarity(projectId)
}

function decline() {
  persist('declined')
  visible.value = false
}

onMounted(() => {
  // No project ID configured — analytics is off entirely, no banner needed.
  if (!projectId) return

  const stored = readStored()
  if (stored === 'accepted') {
    loadClarity(projectId)
    return
  }
  if (stored === 'declined') {
    return
  }

  // No prior choice. Visitors outside the EU/UK get analytics loaded
  // without a banner; EU/UK visitors are asked first.
  if (isEuOrUkVisitor()) {
    visible.value = true
  } else {
    loadClarity(projectId)
  }
})
</script>

<template>
  <div
    v-if="visible"
    class="fixed inset-x-4 bottom-4 z-[100] mx-auto max-w-3xl rounded-2xl border border-white/10 bg-[#1A1A1C] p-5 shadow-2xl sm:p-6"
    role="dialog"
    aria-live="polite"
    aria-label="Cookie consent"
  >
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <p class="font-[InterRegular] text-[14px] leading-[150%] text-[#AFAFB4]">
        We use Microsoft Clarity to understand how visitors use our site so we
        can improve it. This includes anonymized session recordings.
        <NuxtLink to="/privacy" class="text-white underline hover:text-[#FF2D7A]">
          Learn more
        </NuxtLink>.
      </p>
      <div class="flex shrink-0 items-center gap-3">
        <button
          type="button"
          class="inline-flex h-[40px] items-center justify-center rounded-full border border-white/20 px-5 font-[InterMedium] text-[13px] text-white transition hover:bg-white/5"
          @click="decline"
        >
          Decline
        </button>
        <button
          type="button"
          class="inline-flex h-[40px] items-center justify-center rounded-full bg-[linear-gradient(135deg,#FF2D7A_0%,#FF2A55_100%)] px-5 font-[InterMedium] text-[13px] text-white shadow-[0_15px_30px_rgba(255,45,122,0.28)] transition hover:scale-[1.02]"
          @click="accept"
        >
          Accept
        </button>
      </div>
    </div>
  </div>
</template>
