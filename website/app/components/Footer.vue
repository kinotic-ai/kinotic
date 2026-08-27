<template>
  <footer class="kfoot">
    <div class="k-wrap">
      <div class="kfoot__top">
        <div class="kfoot__brand">
          <KinoticLogo :height="26" />
          <p>The crystallization of thought into software. Where ideas become applications.</p>
        </div>

        <div class="kfoot__social">
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

.kfoot__legal a {
  color: var(--color-k-fainter);
  text-decoration: none;
  transition: color 0.2s ease;
}

.kfoot__legal a:hover {
  color: var(--color-k-text);
}

.kfoot__sep {
  color: #3A3A40;
}
</style>
