<script setup lang="ts">
const groups = [
  {
    title: 'Enterprise features',
    note: 'Included in every plan, Free too',
    items: [
      'Sign-in through your own Okta or Microsoft Entra, for your team, your applications and each of their tenants',
      'Secrets kept in your own Azure Key Vault',
      'New Enterprise features in your edition as we ship them',
    ],
  },
  {
    title: 'Kinotic OS',
    note: 'The platform your applications run on',
    items: [
      'Services, persistence and multi-tenancy, generated from your application model',
      'Push-to-deploy, with isolated builds and environments',
      'Logs, traces and metrics for every application',
      'MCP tools, so AI agents can build and operate on Kinotic',
    ],
  },
  {
    title: 'No license needed',
    note: 'For everyone, at any size',
    items: [
      'Development and testing, on as many of your own machines as you like',
      'Kinotic RPC, open source under the Apache License 2.0',
      'The source of the whole platform, to read and build',
    ],
  },
]
</script>

<template>
  <section class="k-section included" aria-labelledby="included-title">
    <div class="k-wrap">
      <div class="k-eyebrow included__eyebrow" data-reveal>
        <span>EVERY PLAN INCLUDES</span>
      </div>
      <h2 id="included-title" class="k-heading included__title" data-reveal>
        One price, every feature. Your plan only reflects your size.
      </h2>

      <div class="included__grid" data-reveal>
        <div v-for="group in groups" :key="group.title" class="included__group">
          <h3 class="k-heading included__group-title">{{ group.title }}</h3>
          <p class="included__note">{{ group.note }}</p>
          <ul class="included__list">
            <li v-for="item in group.items" :key="item">
              <span class="included__check" aria-hidden="true">✓</span>{{ item }}
            </li>
          </ul>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.included {
  --k-section-padding: 96px 0;
  border-top: 1px solid var(--color-k-border);
}

.included__eyebrow {
  margin-bottom: 20px;
}

.included__title {
  max-width: 720px;
  margin-bottom: 48px;
  font-size: clamp(28px, 3.2vw, 42px);
  line-height: 1.15;
  text-wrap: balance;
}

.included__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 40px;
}

.included__group {
  min-width: 0;
  padding-top: 22px;
  border-top: 2px solid var(--color-k-red);
}

.included__group-title {
  font-size: 20px;
}

.included__note {
  margin: 6px 0 0;
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 12.5px;
}

.included__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 22px 0 0;
  padding: 0;
  list-style: none;
}

.included__list li {
  display: flex;
  gap: 10px;
  color: var(--color-k-body);
  font-size: 15px;
  line-height: 1.5;
}

.included__check {
  flex: none;
  color: var(--color-k-mint);
}

@media (max-width: 900px) {
  .included__grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}
</style>
