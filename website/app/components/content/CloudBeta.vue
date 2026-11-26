<script setup lang="ts">
/**
 * The Kinotic Cloud beta panel for a docs page: the free-beta banner and the sign-up button the
 * homepage's call to action carries, on a dark panel of its own in either docs theme.
 */
</script>

<template>
  <section class="cloud-beta not-prose" aria-labelledby="cloud-beta-docs-title">
    <div class="k-eyebrow">
      <span>KINOTIC CLOUD · FREE BETA</span>
    </div>
    <h3 id="cloud-beta-docs-title" class="k-heading cloud-beta__title">Try Kinotic Cloud beta for free</h3>
    <p class="cloud-beta__description">
      Kinotic Cloud runs Kinotic for you, with nothing to install. Sign up to create your organization
      and start building.
    </p>
    <HomeCloudBetaLink class="cloud-beta__signup" placement="docs" />
  </section>
</template>

<style scoped>
.cloud-beta {
  margin: 24px 0;
  padding: 32px;
  border: 1px solid var(--color-k-border);
  border-radius: 8px;
  background: radial-gradient(ellipse at 5% 90%, rgba(236, 31, 82, 0.08), transparent 65%), var(--color-k-bg-panel);
}

.cloud-beta__title {
  margin: 16px 0 0;
  color: var(--color-k-heading);
  font-size: 28px;
  line-height: 1.2;
}

.cloud-beta__description {
  margin: 12px 0 0;
  max-width: 560px;
  color: var(--color-k-body);
  font-size: 15px;
  line-height: 1.6;
}

.cloud-beta__signup {
  margin-top: 24px;
}

@media (max-width: 600px) {
  .cloud-beta {
    padding: 24px 20px;
  }
}
</style>
