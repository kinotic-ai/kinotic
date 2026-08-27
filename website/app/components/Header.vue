<script setup lang="ts">
const route = useRoute()

const scrolled = ref(false)
const menuOpen = ref(false)

const onScroll = () => {
  scrolled.value = window.scrollY > 24
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})

// Covers navigation that bypasses the menu's own click handler — browser back
// and forward while the overlay is open.
watch(() => route.fullPath, () => {
  menuOpen.value = false
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
          <NuxtLink to="/">Home</NuxtLink>
          <NuxtLink to="/apps/introduction">Docs</NuxtLink>
          <a href="/test-results/">Test Reports</a>
        </nav>

        <NuxtLink to="/apps/quick-start" class="knav__cta">Get Started</NuxtLink>

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
      <NuxtLink to="/">Home</NuxtLink>
      <NuxtLink to="/apps/introduction">Docs</NuxtLink>
      <a href="/test-results/">Test Reports</a>
      <NuxtLink to="/apps/quick-start">Get Started</NuxtLink>
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
  height: 76px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid transparent;
  transition: background 0.3s ease, border-color 0.3s ease;
}

.knav--solid {
  background: rgba(8, 9, 10, 0.85);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom-color: #1B1C1F;
}

.knav__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
  font-family: var(--k-font-body);
  font-size: 14px;
  color: var(--k-muted);
  text-decoration: none;
  transition: color 0.2s ease;
}

.knav__links a:hover,
.knav__links a.router-link-active {
  color: #fff;
}

.knav__cta {
  font-family: var(--k-font-mono);
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: var(--k-red);
  padding: 11px 22px;
  border-radius: 4px;
  text-decoration: none;
  transition: background 0.2s ease;
}

.knav__cta:hover {
  color: #fff;
  background: var(--k-red-hover);
}

.knav__toggle {
  display: none;
  width: 44px;
  height: 44px;
  margin-right: -11px;
  padding: 0;
  background: none;
  border: none;
  color: var(--k-text);
  cursor: pointer;
  align-items: center;
  justify-content: center;
}

.knav__menu {
  position: fixed;
  top: 76px;
  left: 0;
  right: 0;
  z-index: 59;
  display: flex;
  flex-direction: column;
  padding: 4px 22px 18px;
  background: rgba(8, 9, 10, 0.97);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid #1B1C1F;
}

.knav__menu a {
  font-family: var(--k-font-body);
  font-size: 16px;
  color: var(--k-text);
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
