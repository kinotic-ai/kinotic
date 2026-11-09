<script setup lang="ts">
// The design partner coupon, floating in the corner on wider screens; PlansComponent shows the same card in the
// page on phones. It appears once the visitor has scrolled past the pricing intro, hides while the design partner
// section it points to is on screen, and sits above the cookie banner while that banner is open.
const DISMISSED_KEY = 'kinotic-pricing-coupon-dismissed'
const GAP = 16

const consent = useCookieConsent()
const pastIntro = ref(false)
const atOffer = ref(false)
const dismissed = ref(false)
const bannerHeight = ref(0)

const shown = computed(() => pastIntro.value && !atOffer.value && !dismissed.value)
const lift = computed(() => (consent.visible.value && bannerHeight.value > 0
  ? { bottom: `${bannerHeight.value + GAP}px` }
  : undefined))

const dismiss = () => {
  dismissed.value = true
  try {
    sessionStorage.setItem(DISMISSED_KEY, '1')
  } catch {
    // storage can be unavailable in private windows; the coupon then stays dismissed for this page view only
  }
}

let observer: IntersectionObserver | undefined
let bannerObserver: ResizeObserver | undefined

// the banner is fixed to the bottom of the viewport, so its distance from the bottom edge is the room the coupon
// must leave
const measureBanner = () => {
  const banner = document.querySelector('[data-cookie-consent]')
  bannerHeight.value = banner ? Math.max(0, Math.round(window.innerHeight - banner.getBoundingClientRect().top)) : 0
}

const observeBanner = () => {
  bannerObserver?.disconnect()
  const banner = document.querySelector('[data-cookie-consent]')
  if (banner) {
    bannerObserver = new ResizeObserver(measureBanner)
    bannerObserver.observe(banner)
  }
  measureBanner()
}

onMounted(() => {
  try {
    dismissed.value = sessionStorage.getItem(DISMISSED_KEY) === '1'
  } catch {
    dismissed.value = false
  }
  const intro = document.querySelector('.plans__intro')
  const offer = document.getElementById('design-partners')
  observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.target === intro) {
        pastIntro.value = !entry.isIntersecting && entry.boundingClientRect.top < 0
      } else if (entry.target === offer) {
        atOffer.value = entry.isIntersecting
      }
    }
  })
  if (intro) observer.observe(intro)
  if (offer) observer.observe(offer)

  observeBanner()
  window.addEventListener('resize', measureBanner)
})

// the footer's Cookie settings link reopens the banner as a new element
watch(consent.visible, async () => {
  await nextTick()
  observeBanner()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  bannerObserver?.disconnect()
  window.removeEventListener('resize', measureBanner)
})
</script>

<template>
  <Transition name="coupon">
    <aside v-if="shown" class="coupon" :style="lift" aria-label="Design partner offer">
      <PricingCouponCardComponent class="coupon__card" />
      <button class="coupon__close" type="button" aria-label="Dismiss the design partner offer" @click="dismiss">
        ×
      </button>
    </aside>
  </Transition>
</template>

<style scoped>
.coupon {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 40;
  width: 340px;
  border-radius: 12px;
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.45);
  transition: bottom 0.25s ease;
}

.coupon__close {
  position: absolute;
  top: 8px;
  right: 10px;
  padding: 2px 6px;
  border: 0;
  background: none;
  color: var(--color-k-muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.coupon__close:hover {
  color: var(--color-k-heading);
}

.coupon__close:focus-visible {
  outline: 2px solid var(--color-k-mint);
  outline-offset: 3px;
}

.coupon-enter-active,
.coupon-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease, bottom 0.25s ease;
}

.coupon-enter-from,
.coupon-leave-to {
  opacity: 0;
  transform: translateY(16px);
}

@media (prefers-reduced-motion: reduce) {
  .coupon,
  .coupon-enter-active,
  .coupon-leave-active {
    transition: none;
  }
}

/* phones get the card in the page instead, from PlansComponent */
@media (max-width: 640px) {
  .coupon {
    display: none;
  }
}
</style>
