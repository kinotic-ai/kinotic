<template>
  <section id="cloud-beta" class="cta" aria-labelledby="cloud-beta-title">
    <div id="cta" class="k-wrap cta__grid">
      <div class="cta__copy">
        <div class="k-eyebrow cta__eyebrow">
          <span>KINOTIC CLOUD · FREE BETA</span>
        </div>
        <h2 id="cloud-beta-title" class="k-heading cta__title">Try Kinotic Cloud beta for free</h2>
        <p class="cta__description">Kinotic, managed for you. Nothing to install.</p>

        <HomeCloudBetaLink class="cta__signup" placement="cta" />
      </div>

      <FloatingCubes class="cta__visual" data-reveal />
    </div>
  </section>
</template>

<style scoped>
.cta {
  padding: 76px 0;
  border-top: 1px solid var(--color-k-border);
  border-bottom: 1px solid var(--color-k-border);
  background: radial-gradient(ellipse at 5% 90%, rgba(236, 31, 82, 0.08), transparent 65%), var(--color-k-bg-panel);
  scroll-margin-top: 150px;
}

.cta__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: 64px;
}

.cta__copy {
  min-width: 0;
}

.cta__eyebrow {
  margin-bottom: 20px;
}

.cta__eyebrow > span {
  font-size: 12px;
  letter-spacing: 0.12em;
}

.cta__title {
  font-size: clamp(30px, 3.6vw, 48px);
  line-height: 1.1;
  letter-spacing: -0.02em;
  max-width: 480px;
}

.cta__description {
  margin: 18px 0 0;
  color: var(--color-k-body);
  font-size: 16px;
}

.cta__signup {
  margin-top: 32px;
}

.cta__visual {
  height: 415px;
}

@media (max-width: 930px) {
  .cta__grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .cta__visual {
    height: 330px;
  }
}

@media (max-width: 600px) {
  .cta {
    padding: 52px 0;
  }
}
</style>
