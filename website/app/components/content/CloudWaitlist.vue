<script setup lang="ts">
/**
 * The Kinotic OS Cloud coming-soon panel for a docs page: the early-access banner and the waitlist
 * signup the homepage's call to action carries, on a dark panel of its own in either docs theme.
 */
</script>

<template>
  <section class="cloud-waitlist not-prose" aria-labelledby="cloud-waitlist-docs-title">
    <div class="k-eyebrow">
      <span>KINOTIC OS CLOUD · COMING SOON</span>
    </div>
    <h3 id="cloud-waitlist-docs-title" class="k-heading cloud-waitlist__title">Be first to build on Kinotic OS Cloud</h3>
    <p class="cloud-waitlist__description">
      Kinotic OS Cloud runs Kinotic for you, with nothing to install. Join the waitlist and we'll email you
      when early access opens. Until then, run Kinotic on your own machine with the Local tab.
    </p>
    <HomeWaitlistForm class="cloud-waitlist__signup" submit-label="Join cloud waitlist ↗" />
  </section>
</template>

<style scoped>
.cloud-waitlist {
  margin: 24px 0;
  padding: 32px;
  border: 1px solid var(--color-k-border);
  border-radius: 8px;
  background: radial-gradient(ellipse at 5% 90%, rgba(236, 31, 82, 0.08), transparent 65%), var(--color-k-bg-panel);
}

.cloud-waitlist__title {
  margin: 16px 0 0;
  color: var(--color-k-heading);
  font-size: 28px;
  line-height: 1.2;
}

.cloud-waitlist__description {
  margin: 12px 0 0;
  max-width: 560px;
  color: var(--color-k-body);
  font-size: 15px;
  line-height: 1.6;
}

.cloud-waitlist__signup {
  margin-top: 24px;
}

@media (max-width: 600px) {
  .cloud-waitlist {
    padding: 24px 20px;
  }
}
</style>
