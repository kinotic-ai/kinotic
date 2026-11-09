<script setup lang="ts">
type Billing = 'monthly' | 'annual'

interface Plan {
  id: string
  name: string
  band: string
  // The revenue band as the revenue picker labels it.
  revenue: string
  // The monthly list price in US dollars; 0 for the free plan.
  monthly: number
  summary: string
  includes: string[]
  cta: { label: string, to: string }
}

// Annual billing takes this fraction off twelve months at the monthly price.
const ANNUAL_DISCOUNT = 0.1

const plans: Plan[] = [
  {
    id: 'free',
    name: 'Free',
    band: 'Under $1M annual revenue',
    revenue: 'Under $1M',
    monthly: 0,
    summary: 'For founders and small teams getting started.',
    includes: ['Kinotic OS in production, in your own cloud', 'Every Enterprise feature', 'Community support'],
    cta: { label: 'Request a free license', to: 'https://licenses.kinotic.ai/free' },
  },
  {
    id: 'small',
    name: 'Small',
    band: '$1M to under $20M annual revenue',
    revenue: '$1M – under $20M',
    monthly: 5000,
    summary: 'For growing companies running Kinotic in production.',
    includes: ['Kinotic OS in production, in your own cloud', 'Every Enterprise feature', 'Email support'],
    cta: { label: 'Buy Small', to: 'https://licenses.kinotic.ai/buy?plan=small' },
  },
  {
    id: 'medium',
    name: 'Medium',
    band: '$20M – $100M annual revenue',
    revenue: '$20M – $100M',
    monthly: 10000,
    summary: 'For established companies building on Kinotic.',
    includes: ['Kinotic OS in production, in your own cloud', 'Every Enterprise feature', 'Priority support'],
    cta: { label: 'Buy Medium', to: 'https://licenses.kinotic.ai/buy?plan=medium' },
  },
  {
    id: 'large',
    name: 'Large',
    band: 'Over $100M annual revenue',
    revenue: 'Over $100M',
    monthly: 20000,
    summary: 'For enterprises running critical systems on Kinotic.',
    includes: ['Kinotic OS in production, in your own cloud', 'Every Enterprise feature', 'Priority support and a named contact'],
    cta: { label: 'Buy Large', to: 'https://licenses.kinotic.ai/buy?plan=large' },
  },
]

const billing = ref<Billing>('monthly')

// The plan matching the revenue the visitor picked; the free plan until they pick one.
const selected = ref<string>('free')

const usd = (amount: number) => '$' + Math.round(amount).toLocaleString('en-US')

// The monthly figure shown on a card: the list price, or the annual price spread over twelve months.
const shownMonthly = (plan: Plan) => billing.value === 'annual' ? plan.monthly * (1 - ANNUAL_DISCOUNT) : plan.monthly

const billedNote = (plan: Plan) => {
  let ret: string
  if (plan.monthly === 0) {
    ret = 'Free for as long as you qualify'
  } else if (billing.value === 'annual') {
    ret = `Billed ${usd(plan.monthly * 12 * (1 - ANNUAL_DISCOUNT))} a year`
  } else {
    ret = 'Billed monthly'
  }
  return ret
}

const ctaHref = (plan: Plan) => plan.monthly === 0 ? plan.cta.to : `${plan.cta.to}&billing=${billing.value}`
</script>

<template>
  <section class="plans" aria-labelledby="pricing-title">
    <div class="k-wrap">
      <div class="plans__intro" data-reveal>
        <div class="k-eyebrow plans__eyebrow">
          <span>PRICING · RUNS IN YOUR CLOUD</span>
        </div>
        <h1 id="pricing-title" class="k-heading plans__title">Free for small companies. Simple pricing as you grow.</h1>
        <p class="plans__lede">
          Each plan is a license to run Kinotic Enterprise in your own cloud. You only need a license for
          production, where Kinotic serves your users, customers or business; local, test and staging environments
          need none. Companies under $1M in annual revenue run production at no charge. Above that, your price is set
          by your company's annual revenue, and every plan includes every Enterprise feature.
        </p>

        <div class="plans__toggle" role="group" aria-label="Billing period">
          <button
            type="button"
            class="plans__toggle-option"
            :aria-pressed="billing === 'monthly'"
            @click="billing = 'monthly'"
          >Monthly</button>
          <button
            type="button"
            class="plans__toggle-option"
            :aria-pressed="billing === 'annual'"
            @click="billing = 'annual'"
          >Annual <span class="plans__save">Save 10%</span></button>
        </div>

        <div class="plans__revenue">
          <p id="revenue-label" class="plans__revenue-label">Your company's annual revenue</p>
          <div class="plans__chips" role="group" aria-labelledby="revenue-label">
            <button
              v-for="plan in plans"
              :key="plan.id"
              type="button"
              class="plans__chip"
              :aria-pressed="selected === plan.id"
              @click="selected = plan.id"
            >{{ plan.revenue }}</button>
          </div>
        </div>
      </div>

      <div class="plans__grid" data-reveal>
        <article
          v-for="plan in plans"
          :key="plan.id"
          class="plan"
          :class="{ 'plan--selected': selected === plan.id }"
          :aria-labelledby="`plan-${plan.id}`"
        >
          <span v-if="selected === plan.id" class="plan__flag">Your plan</span>
          <h2 :id="`plan-${plan.id}`" class="k-heading plan__name">{{ plan.name }}</h2>
          <p class="plan__band">{{ plan.band }}</p>

          <div class="plan__price">
            <span class="plan__amount">{{ usd(shownMonthly(plan)) }}</span>
            <span v-if="plan.monthly > 0" class="plan__per">/ month</span>
          </div>
          <p class="plan__billed">{{ billedNote(plan) }}</p>

          <p class="plan__summary">{{ plan.summary }}</p>

          <a
            :href="ctaHref(plan)"
            class="k-btn plan__cta"
            :class="selected === plan.id ? 'k-btn--mint' : 'k-btn--ghost'"
          >{{ plan.cta.label }}</a>

          <ul class="plan__includes">
            <li v-for="item in plan.includes" :key="item">
              <span class="plan__check" aria-hidden="true">✓</span>{{ item }}
            </li>
          </ul>
        </article>
      </div>

      <div class="lock" data-reveal>
        <span class="k-livedot lock__dot" aria-hidden="true" />
        <p>
          <strong>Your price is locked.</strong>
          The price list you join on stays yours for as long as your license stays active, even as your company
          grows into a larger plan.
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.plans {
  padding: 168px 0 96px;
  background: radial-gradient(ellipse at 50% 0%, rgba(40, 254, 180, 0.07), transparent 60%), var(--color-k-bg);
}

.plans__intro {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 760px;
  margin: 0 auto 56px;
}

.plans__eyebrow {
  margin-bottom: 22px;
}

.plans__title {
  font-size: clamp(36px, 5vw, 62px);
  line-height: 1.05;
  letter-spacing: -0.025em;
  text-wrap: balance;
}

.plans__lede {
  margin: 22px 0 0;
  max-width: 620px;
  color: var(--color-k-body);
  font-size: 17px;
  line-height: 1.6;
}

.plans__toggle {
  display: inline-flex;
  margin-top: 36px;
  padding: 4px;
  border: 1px solid var(--color-k-border);
  border-radius: 999px;
  background: var(--color-k-bg-chip);
}

.plans__toggle-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 20px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.plans__toggle-option[aria-pressed='true'] {
  background: var(--color-k-heading);
  color: var(--color-k-bg);
}

.plans__toggle-option:focus-visible,
.plan__cta:focus-visible {
  outline: 2px solid var(--color-k-mint);
  outline-offset: 2px;
}

.plans__revenue {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-top: 26px;
}

.plans__revenue-label {
  margin: 0;
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 12.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.plans__chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.plans__chip {
  padding: 9px 16px;
  border: 1px solid var(--color-k-border);
  border-radius: 999px;
  background: var(--color-k-bg-chip);
  color: var(--color-k-text);
  font-family: var(--font-k-mono);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.plans__chip:hover {
  border-color: var(--color-k-muted);
}

.plans__chip[aria-pressed='true'] {
  border-color: var(--color-k-mint);
  background: rgba(40, 254, 180, 0.12);
  color: var(--color-k-mint);
}

.plans__chip:focus-visible {
  outline: 2px solid var(--color-k-mint);
  outline-offset: 2px;
}

.plans__save {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(40, 254, 180, 0.14);
  color: var(--color-k-mint);
  font-size: 11px;
}

.plans__toggle-option[aria-pressed='true'] .plans__save {
  background: rgba(8, 9, 10, 0.12);
  color: #0b6b4a;
}

.plans__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

/* Each card spans seven rows of the grid and shares them through subgrid (name, band, price, billing note,
   summary, button, feature list), so every row lines up across cards whatever its content's length. */
.plan {
  position: relative;
  display: grid;
  grid-row: span 7;
  grid-template-rows: subgrid;
  row-gap: 0;
  align-content: start;
  min-width: 0;
  padding: 30px 26px 28px;
  border: 1px solid var(--color-k-border);
  border-radius: 10px;
  background: var(--color-k-bg-card);
}

.plan--selected {
  border-color: var(--color-k-mint);
  background: linear-gradient(180deg, rgba(40, 254, 180, 0.06), transparent 45%), var(--color-k-bg-card);
}

.plan__flag {
  position: absolute;
  top: -12px;
  left: 26px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--color-k-mint);
  color: var(--color-k-bg);
  font-family: var(--font-k-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.plan__name {
  font-size: 24px;
}

.plan__band {
  margin: 6px 0 0;
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 12.5px;
}

.plan__price {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 26px;
}

.plan__amount {
  font-family: var(--font-k-display);
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-k-heading);
  font-variant-numeric: tabular-nums;
}

.plan__per {
  color: var(--color-k-muted);
  font-size: 14px;
}

.plan__billed {
  margin: 4px 0 0;
  min-height: 1.4em;
  color: var(--color-k-dim);
  font-size: 13px;
}

.plan__summary {
  margin: 18px 0 0;
  color: var(--color-k-body);
  font-size: 14.5px;
  line-height: 1.5;
}

.plan__cta {
  align-self: start;
  justify-content: center;
  margin-top: 22px;
  padding: 13px 18px;
}

.plan__includes {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 24px 0 0;
  padding: 22px 0 0;
  border-top: 1px solid var(--color-k-border);
  list-style: none;
}

.plan__includes li {
  display: flex;
  gap: 10px;
  color: var(--color-k-text);
  font-size: 14px;
  line-height: 1.45;
}

.plan__check {
  color: var(--color-k-mint);
  flex: none;
}

.lock {
  display: flex;
  align-items: center;
  gap: 14px;
  max-width: 760px;
  margin: 40px auto 0;
  padding: 16px 22px;
  border: 1px solid var(--color-k-border);
  border-radius: 8px;
  background: var(--color-k-bg-panel);
}

.lock p {
  margin: 0;
  color: var(--color-k-body);
  font-size: 14.5px;
  line-height: 1.55;
}

.lock strong {
  color: var(--color-k-heading);
  font-weight: 600;
}

.lock__dot {
  flex: none;
}

@media (max-width: 1100px) {
  .plans__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 28px;
  }
}

@media (max-width: 640px) {
  .plans {
    padding: 128px 0 64px;
  }

  .plans__grid {
    grid-template-columns: 1fr;
  }
}
</style>
