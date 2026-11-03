<script setup lang="ts">
const { open: openCookieSettings } = useCookieConsent()
</script>

<template>
  <footer class="kfoot">
    <div class="k-wrap">
      <div class="kfoot__top">
        <div class="kfoot__brand">
          <KinoticLogo :height="26" />
          <p>The crystallization of thought into software. Where ideas become applications.</p>
        </div>

        <div class="kfoot__social">
          <a href="https://github.com/kinotic-ai/kinotic" target="_blank" rel="noreferrer" aria-label="Kinotic on GitHub">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
              <path d="M9 18c-4.51 2-5-2-7-2" />
            </svg>
          </a>
          <a href="https://www.youtube.com/@Kinoticsoftware" target="_blank" rel="noreferrer" aria-label="Kinotic on YouTube">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M2.5 17a24.12 24.12 0 0 1 0-10 2 2 0 0 1 1.4-1.4 49.56 49.56 0 0 1 16.2 0A2 2 0 0 1 21.5 7a24.12 24.12 0 0 1 0 10 2 2 0 0 1-1.4 1.4 49.55 49.55 0 0 1-16.2 0A2 2 0 0 1 2.5 17" />
              <path d="m10 15 5-3-5-3z" />
            </svg>
          </a>
          <a href="https://x.com/kinoticai" target="_blank" rel="noreferrer" aria-label="Kinotic on X">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M4 4l11.733 16H20L8.267 4z" />
              <path d="M4 20l6.768-6.768" />
              <path d="M13.232 10.768L20 4" />
            </svg>
          </a>
          <a href="https://www.linkedin.com/company/kinotic/" target="_blank" rel="noreferrer" aria-label="Kinotic on LinkedIn">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <rect x="3" y="3" width="18" height="18" rx="3" />
              <path d="M7.4 10.6V17" />
              <path d="M7.4 7.4h.01" />
              <path d="M11.4 17v-6.4" />
              <path d="M11.4 13.4a2.6 2.6 0 0 1 5.2 0V17" />
            </svg>
          </a>
        </div>
      </div>

      <div class="kfoot__bottom">
        <span>© 2018–present Kinotic</span>
        <span class="kfoot__legal">
          <NuxtLink to="/terms">Terms of use</NuxtLink>
          <span class="kfoot__sep" aria-hidden="true">|</span>
          <NuxtLink to="/privacy">Privacy policy</NuxtLink>
          <span class="kfoot__sep" aria-hidden="true">|</span>
          <button type="button" @click="openCookieSettings">Cookie settings</button>
        </span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.kfoot {
  padding: 70px 0 44px;
  background: #050506;
  font-family: var(--font-k-body);
}

.kfoot__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 32px 48px;
  justify-content: space-between;
  margin-bottom: 56px;
}

.kfoot__brand {
  max-width: 340px;
}

.kfoot__brand p {
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-k-dim);
  margin: 20px 0 0;
}

.kfoot__social {
  display: flex;
  align-items: center;
  gap: 34px;
}

.kfoot__social a {
  display: inline-flex;
  color: var(--color-k-dim);
  transition: color 0.2s ease;
}

.kfoot__social a:hover {
  color: var(--color-k-text);
}

.kfoot__social svg {
  display: block;
  width: 20px;
  height: 20px;
}

.kfoot__bottom {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  justify-content: space-between;
  align-items: center;
  padding-top: 28px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  font-size: 13px;
  color: var(--color-k-fainter);
}

.kfoot__legal {
  display: flex;
  gap: 10px;
}

.kfoot__legal a,
.kfoot__legal button {
  color: var(--color-k-fainter);
  text-decoration: none;
  transition: color 0.2s ease;
}

.kfoot__legal button {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  cursor: pointer;
}

.kfoot__legal a:hover,
.kfoot__legal button:hover {
  color: var(--color-k-text);
}

.kfoot__sep {
  color: #3A3A40;
}
</style>
