<script setup lang="ts">
const columns = [
  {
    title: 'PLATFORM',
    links: [
      { label: 'Architecture', to: '/platform/architecture' },
      { label: 'Deployment', to: '/platform/deployment-guide' },
      { label: 'Configuration', to: '/platform/configuration' },
      { label: 'Observability', to: '/platform/observability' },
    ],
  },
  {
    title: 'DEVELOPERS',
    links: [
      { label: 'Documentation', to: '/apps/introduction' },
      { label: 'Quick start', to: '/apps/quick-start' },
      { label: 'CLI reference', to: '/apps/cli-reference' },
      { label: 'MCP tools', to: '/platform/mcp-tools' },
    ],
  },
  {
    title: 'PROJECT',
    links: [
      { label: 'GitHub', to: 'https://github.com/kinotic-ai/kinotic', external: true },
      { label: 'Contributing', to: '/platform/contributing' },
      { label: 'Security', to: '/platform/system-security' },
      { label: 'Test reports', to: '/test-results/', external: true },
    ],
  },
]
</script>

<template>
  <footer class="kfoot">
    <div class="k-wrap">
      <div class="kfoot__top">
        <div class="kfoot__brand">
          <KinoticLogo :height="26" />
          <p>The crystallization of thought into software. Where ideas become applications.</p>
        </div>

        <div class="kfoot__cols">
          <div v-for="column in columns" :key="column.title">
            <div class="kfoot__coltitle">{{ column.title }}</div>
            <div class="kfoot__collinks">
              <template v-for="link in column.links" :key="link.label">
                <a v-if="link.external" :href="link.to">{{ link.label }}</a>
                <NuxtLink v-else :to="link.to">{{ link.label }}</NuxtLink>
              </template>
            </div>
          </div>
        </div>
      </div>

      <div class="kfoot__bottom">
        <span>Copyright © 2018–present Kinotic</span>
        <span class="kfoot__legal">
          <NuxtLink to="/terms">Terms of use</NuxtLink>
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
  font-family: var(--k-font-body);
}

.kfoot__top {
  display: flex;
  flex-wrap: wrap;
  gap: 48px;
  justify-content: space-between;
  margin-bottom: 56px;
}

.kfoot__brand {
  max-width: 300px;
}

.kfoot__brand p {
  font-size: 14px;
  line-height: 1.65;
  color: var(--k-dim);
  margin: 20px 0 0;
}

.kfoot__cols {
  display: flex;
  flex-wrap: wrap;
  gap: 72px;
}

.kfoot__coltitle {
  font-family: var(--k-font-mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--k-fainter);
  margin-bottom: 16px;
}

.kfoot__collinks {
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.kfoot__collinks a {
  font-size: 14px;
  color: var(--k-muted);
  text-decoration: none;
  transition: color 0.2s ease;
}

.kfoot__collinks a:hover {
  color: var(--k-text);
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
  color: var(--k-fainter);
}

.kfoot__legal {
  display: flex;
  gap: 18px;
}

.kfoot__legal a {
  color: var(--k-fainter);
  text-decoration: none;
  transition: color 0.2s ease;
}

.kfoot__legal a:hover {
  color: var(--k-text);
}

@media (max-width: 600px) {
  .kfoot__cols {
    gap: 34px 44px;
  }
}
</style>
