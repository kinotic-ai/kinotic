<script setup lang="ts">
const features = [
  {
    title: 'Sign-in through your own identity provider',
    body: 'Okta or Microsoft Entra, for your team, your applications and each of their tenants.',
  },
  {
    title: 'Secrets in your own vault',
    body: 'Your secrets kept in your own Azure Key Vault.',
  },
  {
    title: 'New Enterprise features as we ship them',
    body: 'Every feature we add to your edition is yours at no extra cost.',
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
        Every Enterprise feature, Free plan too. Your plan only reflects your size.
      </h2>

      <div class="included__grid" data-reveal>
        <div v-for="feature in features" :key="feature.title" class="included__feature">
          <h3 class="k-heading included__feature-title">{{ feature.title }}</h3>
          <p class="included__body">{{ feature.body }}</p>
        </div>
      </div>

      <p class="included__license" data-reveal>
        The rest of Kinotic is available under the Elastic License 2.0.
      </p>
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

.included__feature {
  min-width: 0;
  padding-top: 22px;
  border-top: 2px solid var(--color-k-red);
}

.included__feature-title {
  font-size: 20px;
  line-height: 1.3;
}

.included__body {
  margin: 10px 0 0;
  color: var(--color-k-body);
  font-size: 15px;
  line-height: 1.5;
}

.included__license {
  margin: 48px 0 0;
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 13px;
}

@media (max-width: 900px) {
  .included__grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}
</style>
