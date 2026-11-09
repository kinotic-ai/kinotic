<template>
  <a class="coupon-card" href="#design-partners">
    <span class="coupon-card__stub" aria-hidden="true">
      <span class="coupon-card__amount">50%</span>
      <span class="coupon-card__off">OFF</span>
    </span>
    <span class="coupon-card__body">
      <span class="coupon-card__label">DESIGN PARTNERS</span>
      <span class="coupon-card__text">Half off any paid plan for three years.</span>
      <span class="coupon-card__more">See the offer <span aria-hidden="true">→</span></span>
    </span>
  </a>
</template>

<style scoped>
.coupon-card {
  display: flex;
  align-items: stretch;
  border: 1px dashed rgba(40, 254, 180, 0.55);
  border-radius: 12px;
  background: radial-gradient(ellipse at 0% 0%, rgba(40, 254, 180, 0.1), transparent 70%), var(--color-k-bg-panel);
  color: inherit;
  text-align: left;
  text-decoration: none;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.coupon-card:hover {
  border-color: var(--color-k-mint);
}

.coupon-card:focus-visible {
  outline: 2px solid var(--color-k-mint);
  outline-offset: 3px;
}

.coupon-card__stub {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 96px;
  border-right: 1px dashed rgba(40, 254, 180, 0.45);
}

.coupon-card__amount {
  color: var(--color-k-mint);
  font-family: var(--font-k-display);
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
}

.coupon-card__off {
  margin-top: 4px;
  color: var(--color-k-mint);
  font-family: var(--font-k-mono);
  font-size: 11px;
  letter-spacing: 0.2em;
}

.coupon-card__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px;
}

.coupon-card__label {
  color: var(--color-k-muted);
  font-family: var(--font-k-mono);
  font-size: 11px;
  letter-spacing: 0.16em;
}

.coupon-card__text {
  margin-top: 6px;
  color: var(--color-k-heading);
  font-size: 14.5px;
  line-height: 1.4;
}

.coupon-card__more {
  margin-top: 10px;
  color: var(--color-k-mint);
  font-family: var(--font-k-mono);
  font-size: 13px;
}

.coupon-card:hover .coupon-card__more {
  color: var(--color-k-mint-hover);
}

@media (max-width: 480px) {
  .coupon-card__stub {
    width: 84px;
  }
}
</style>
