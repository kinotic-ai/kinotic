<script setup lang="ts">
const questions = [
  {
    q: 'How do I know which plan I need?',
    a: 'Your plan is set by your company\'s annual revenue, including any companies it owns. You confirm it when you '
      + 'buy, and again once a year at renewal.',
  },
  {
    q: 'What happens when my company grows?',
    a: 'At your annual renewal, if your revenue has moved you into a different plan, you move to that plan at the '
      + 'price on your locked price list. If your revenue falls, you move down the same way.',
  },
  {
    q: 'What does the price lock mean?',
    a: 'When you first subscribe you get the price list in effect that day, for every plan. You keep it for as long '
      + 'as your license stays active, even when our prices go up for new customers.',
  },
  {
    q: 'What counts as an active license?',
    a: 'One that is never cancelled, and whose failed payments are settled within 30 days. If a license lapses, '
      + 'renewing it starts you on the price list in effect at that time.',
  },
  {
    q: 'Do you host Kinotic for me?',
    a: 'Not yet. These plans license Kinotic Enterprise to run in your own cloud, on infrastructure you control. '
      + 'Kinotic Cloud, which we host for you, is coming: join the waitlist on our home page to hear first.',
    link: { label: 'Join the Kinotic Cloud waitlist', to: '/#cloud-waitlist' },
  },
  {
    q: 'Do I need a license to try Kinotic?',
    a: 'No. You only need a license for production: an environment where Kinotic serves your users, customers or '
      + 'business. Local, test and staging environments that serve no real users need none, at any company size, '
      + 'and run on your own machines or cloud. A production license costs nothing if your company is under $1M in '
      + 'annual revenue.',
  },
  {
    q: 'Will there be more editions?',
    a: 'Yes. Every plan today includes every Enterprise feature we have. As we add more, we\'ll offer higher editions '
      + 'you can upgrade to. Your current edition keeps every feature it has.',
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
