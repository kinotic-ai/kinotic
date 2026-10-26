<script setup lang="ts">
import type { HeaderLink } from '~~/page-meta'

const SITE_LINKS: HeaderLink[] = [
  { label: 'Home', to: '/' },
  { label: 'Docs', to: '/getting-started/introduction' },
  { label: 'Test Reports', to: '/test-results/', external: true },
]
const SITE_CTA: HeaderLink = { label: 'Get Started', to: '/getting-started/quick-start' }

// How far below the viewport top a section has to reach before its link counts as current.
const SPY_LINE = 140

const route = useRoute()
const links = computed(() => route.meta.headerNav?.links ?? SITE_LINKS)
const cta = computed(() => route.meta.headerNav?.cta ?? SITE_CTA)

const scrolled = ref(false)
const menuOpen = ref(false)
const activeAnchor = ref('')

const passedSpyLine = (link: HeaderLink) => {
  const target = link.to.startsWith('#') ? document.getElementById(link.to.slice(1)) : null
  return target !== null && target.getBoundingClientRect().top <= SPY_LINE
}

// The current link is the last in-page section that has scrolled past the spy line; once
// the call to action's own section passes it, no link is current.
const currentAnchor = () => {
  let current = ''
  for (const link of links.value) {
    if (passedSpyLine(link)) current = link.to
  }
  if (passedSpyLine(cta.value)) current = ''
  return current
}

const onScroll = () => {
  scrolled.value = window.scrollY > 24
  activeAnchor.value = currentAnchor()
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})

// Covers navigation that bypasses the menu's own click handler — browser back
// and forward while the overlay is open. The new page's sections exist only after
// it renders, so the current link is recomputed on the next tick.
watch(() => route.fullPath, async () => {
  menuOpen.value = false
  await nextTick()
  onScroll()
})
</script>

<template>
  <header class="knav" :class="{ 'knav--solid': scrolled }">
    <div class="k-wrap knav__bar">
      <NuxtLink to="/" class="knav__brand" aria-label="Kinotic home">
        <KinoticLogo :height="24" />
      </NuxtLink>

      <div class="knav__right">
        <nav class="knav__links" aria-label="Main">
          <NuxtLink
            v-for="link in links"
            :key="link.to"
            :to="link.to"
            :external="link.external"
            :class="{ 'is-current': link.to === activeAnchor }"
            :aria-current="link.to === activeAnchor ? 'location' : undefined"
          >{{ link.label }}</NuxtLink>
        </nav>

        <NuxtLink :to="cta.to" :external="cta.external" class="knav__cta">{{ cta.label }}</NuxtLink>

        <button
          class="knav__toggle"
          type="button"
          :aria-expanded="menuOpen"
          aria-controls="knav-menu"
          aria-label="Menu"
          @click="menuOpen = !menuOpen"
        >
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none" aria-hidden="true">
            <path d="M3 6.5h16M3 11h16M3 15.5h16" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
          </svg>
        </button>
      </div>
    </div>

    <nav v-show="menuOpen" id="knav-menu" class="knav__menu" aria-label="Main" @click="menuOpen = false">
      <NuxtLink v-for="link in [...links, cta]" :key="link.to" :to="link.to" :external="link.external">{{ link.label }}</NuxtLink>
    </nav>
  </header>
</template>

<style scoped>
.knav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 60;
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid transparent;
  transition: background 0.3s ease, border-color 0.3s ease;
}

.knav--solid {
  background: rgba(8, 9, 10, 0.85);
  backdrop-filter: blur(14px);
  border-bottom-color: #1B1C1F;
}

.knav__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 76px;
}

.knav__brand {
  display: inline-flex;
}

.knav__right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.knav__links {
  display: flex;
  align-items: center;
  gap: 40px;
}

.knav__links a {
  position: relative;
  font-family: var(--font-k-body);
  font-size: 14px;
  color: var(--color-k-muted);
  text-decoration: none;
  transition: color 0.2s ease;
}

/* The red underline marking the current in-page section, grown from the left. */
.knav__links a::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: calc(100% + 12px);
  height: 2px;
  background: var(--color-k-red);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.knav__links a:hover,
.knav__links a.router-link-active,
.knav__links a.is-current {
  color: #fff;
}

.knav__links a.is-current::after {
  transform: scaleX(1);
}

.knav__cta {
  font-family: var(--font-k-mono);
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: var(--color-k-red);
  padding: 11px 22px;
  border-radius: 4px;
  text-decoration: none;
  transition: background 0.2s ease;
}

.knav__cta:hover {
  color: #fff;
  background: var(--color-k-red-hover);
}

.knav__toggle {
  display: none;
  width: 44px;
  height: 44px;
  margin-right: -11px;
  padding: 0;
  background: none;
  border: none;
  color: var(--color-k-text);
  cursor: pointer;
  align-items: center;
  justify-content: center;
}

.knav__menu {
  width: 100%;
  z-index: 59;
  display: flex;
  flex-direction: column;
  padding: 4px 22px 18px;
  background: rgba(8, 9, 10, 0.97);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid #1B1C1F;
}

.knav__menu a {
  font-family: var(--font-k-body);
  font-size: 16px;
  color: var(--color-k-text);
  text-decoration: none;
  padding: 15px 2px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.knav__menu a:last-child {
  border-bottom: none;
}

@media (max-width: 768px) {
  .knav__links,
  .knav__cta {
    display: none;
  }

  .knav__toggle {
    display: inline-flex;
  }
}
</style>
