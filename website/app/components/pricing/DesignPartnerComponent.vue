<script setup lang="ts">
const gets = [
  '50% off your plan for three years',
  'Then your locked price, on the same terms, for as long as your license stays active',
]

const gives = [
  'We may name your company as a Kinotic customer on our site',
  'We may describe what you use Kinotic for, in words you approve',
  'A case study we write together, published with your approval',
]
</script>

<template>
  <section id="design-partners" class="k-section partner" aria-labelledby="partner-title">
    <div class="k-wrap">
      <div class="partner__panel" data-reveal>
        <div class="partner__intro">
          <div class="k-eyebrow partner__eyebrow">
            <span>DESIGN PARTNERS</span>
          </div>
          <h2 id="partner-title" class="k-heading partner__title">Half price for three years.</h2>
          <p class="partner__lede">
            Design partners get half off any paid plan for three years, and in return let us tell people how they use
            Kinotic. We review every application.
          </p>
          <a class="k-btn k-btn--mint partner__cta" href="https://licenses.kinotic.ai/design-partner">
            Apply to be a design partner
          </a>
        </div>

        <div class="partner__terms">
          <div class="partner__group">
            <h3 class="partner__group-title">You get</h3>
            <ul class="partner__list">
              <li v-for="item in gets" :key="item">
                <span class="partner__check" aria-hidden="true">✓</span>{{ item }}
              </li>
            </ul>
          </div>
          <div class="partner__group">
            <h3 class="partner__group-title">In return</h3>
            <ul class="partner__list">
              <li v-for="item in gives" :key="item">
                <span class="partner__check" aria-hidden="true">✓</span>{{ item }}
              </li>
            </ul>
            <p class="partner__promise">
              You approve everything we publish about you before it goes out. We never share your code, your data or
              anything else you tell us is confidential.
            </p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.partner {
  --k-section-padding: 96px 0;
  scroll-margin-top: 76px;
  border-top: 1px solid var(--color-k-border);
}

.partner__panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 56px;
  padding: 44px;
  border: 1px solid var(--color-k-mint);
  border-radius: 12px;
  background: radial-gradient(ellipse at 0% 0%, rgba(40, 254, 180, 0.08), transparent 60%), var(--color-k-bg-panel);
}

.partner__intro {
  min-width: 0;
}

.partner__eyebrow {
  margin-bottom: 20px;
}

.partner__title {
  font-size: clamp(28px, 3.2vw, 40px);
  line-height: 1.15;
}

.partner__lede {
  margin: 16px 0 0;
  max-width: 460px;
  color: var(--color-k-body);
  font-size: 16px;
  line-height: 1.6;
}

.partner__cta {
  margin-top: 28px;
}

.partner__terms {
  display: flex;
  flex-direction: column;
  gap: 28px;
  min-width: 0;
}

.partner__group-title {
  margin: 0;
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.partner__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.partner__list li {
  display: flex;
  gap: 10px;
  color: var(--color-k-body);
  font-size: 15px;
  line-height: 1.5;
}

.partner__promise {
  margin: 16px 0 0;
  color: var(--color-k-muted);
  font-size: 13.5px;
  line-height: 1.55;
}

.partner__check {
  flex: none;
  color: var(--color-k-mint);
}

@media (max-width: 900px) {
  .partner__panel {
    grid-template-columns: 1fr;
    gap: 36px;
    padding: 28px 22px;
  }
}
</style>
