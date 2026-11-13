<script setup lang="ts">
const questions = [
  {
    q: 'How do I know which plan I need?',
    a: 'Your plan is set by your company\'s annual revenue, including any companies it owns. The Free plan also '
      + 'needs your company to have raised under $5M in total, counting equity, SAFEs and convertible notes but not '
      + 'grants or loans; a company under $1M in revenue that has raised more is on Small. You confirm your revenue '
      + 'and funding when you buy, and again once a year at renewal.',
  },
  {
    q: 'Can I try a paid plan before I buy it?',
    a: 'Yes. Your company can try the plan its revenue sets free for three months, with every Enterprise feature and '
      + 'no card needed. Buy the plan before the trial ends to keep your license, on the price list and terms you '
      + 'started your trial on. Each company gets one trial.',
  },
  {
    q: 'What happens when my company grows?',
    a: 'At your annual renewal, if your revenue has moved you into a different plan, you move to that plan at the '
      + 'price on your locked price list. If your revenue falls, you move down the same way.',
  },
  {
    q: 'What does "your terms never change" mean?',
    a: 'When you first subscribe you get the price list in effect that day, for every plan, and the license terms in '
      + 'effect that day. You keep both for as long as your license stays active, even when our prices or terms change '
      + 'for new customers.',
  },
  {
    q: 'What counts as an active license?',
    a: 'One that is never cancelled, and whose failed payments are settled within 30 days. If a license lapses, '
      + 'renewing it starts you on the price list in effect at that time.',
  },
  {
    q: 'Do you host Kinotic for me?',
    a: 'Not yet. These plans license Kinotic\'s Enterprise features in your own cloud, on infrastructure you control. '
      + 'Kinotic Cloud, which we host for you, is coming: join the waitlist on our home page to hear first.',
    link: { label: 'Join the Kinotic Cloud waitlist', to: '/#cloud-waitlist' },
  },
  {
    q: 'Do I need a license to use Kinotic?',
    a: 'Only to use the Enterprise features. The rest of Kinotic is available under the Elastic License 2.0, and '
      + 'Kinotic RPC under the Apache License 2.0. If your company is under $1M in annual revenue and has raised under '
      + '$5M, an Enterprise license costs nothing.',
  },
  {
    q: 'Do I get new Enterprise features?',
    a: 'Yes. Every Enterprise feature we release is added to your plan at the same price, for as long as your license '
      + 'stays active, even if we later sell it in a higher tier.',
  },
  {
    q: 'What does the Free plan ask in return?',
    a: 'Only that we may name your company as a Kinotic customer, on our site and in what we publish about Kinotic.',
  },
  {
    q: 'How can I pay?',
    a: 'By card, or by bank account through Link. Paying annually saves 10%.',
  },
]
</script>

<template>
  <section class="k-section faq" aria-labelledby="faq-title">
    <div class="k-wrap faq__grid">
      <div class="faq__intro" data-reveal>
        <div class="k-eyebrow faq__eyebrow">
          <span>QUESTIONS</span>
        </div>
        <h2 id="faq-title" class="k-heading faq__title">Pricing questions, answered</h2>
      </div>

      <div class="faq__list" data-reveal>
        <details v-for="item in questions" :key="item.q" class="faq__item">
          <summary class="faq__question">{{ item.q }}</summary>
          <p class="faq__answer">
            {{ item.a }}
            <NuxtLink v-if="item.link" :to="item.link.to" class="faq__link">{{ item.link.label }} →</NuxtLink>
          </p>
        </details>
      </div>
    </div>
  </section>
</template>

<style scoped>
.faq {
  --k-section-padding: 96px 0;
  border-top: 1px solid var(--color-k-border);
}

.faq__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  gap: 56px;
}

.faq__eyebrow {
  margin-bottom: 20px;
}

.faq__title {
  font-size: clamp(28px, 3vw, 38px);
  line-height: 1.15;
  text-wrap: balance;
}

.faq__list {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.faq__item {
  border-bottom: 1px solid var(--color-k-border);
}

.faq__item:first-child {
  border-top: 1px solid var(--color-k-border);
}

.faq__question {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px 0;
  color: var(--color-k-heading);
  font-family: var(--font-k-display);
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  list-style: none;
}

.faq__question::-webkit-details-marker {
  display: none;
}

.faq__question::after {
  content: '+';
  flex: none;
  color: var(--color-k-mint);
  font-family: var(--font-k-mono);
  font-size: 20px;
  transition: transform 0.2s ease;
}

.faq__item[open] .faq__question::after {
  transform: rotate(45deg);
}

.faq__question:focus-visible {
  outline: 2px solid var(--color-k-mint);
  outline-offset: 4px;
}

.faq__link {
  display: inline-block;
  margin-top: 10px;
  color: var(--color-k-mint);
  font-family: var(--font-k-mono);
  font-size: 13px;
  text-decoration: none;
}

.faq__link:hover {
  color: var(--color-k-mint-hover);
}

.faq__answer {
  margin: 0 0 22px;
  max-width: 640px;
  color: var(--color-k-body);
  font-size: 15.5px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .faq__grid {
    grid-template-columns: 1fr;
    gap: 28px;
  }
}
</style>
