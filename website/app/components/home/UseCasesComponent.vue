<script setup lang="ts">
const stories = [
  {
    slug: 'mauiwine',
    name: 'MauiWine',
    body: 'Island winery running its club, allocations and tasting-room bookings on one Kinotic app.',
  },
  {
    slug: 'hula-polish',
    name: 'Hula Polish',
    body: 'D2C beauty brand that scaffolded its storefront, inventory and fulfillment in a weekend.',
  },
  {
    slug: 'bobs-auto',
    name: 'Bob’s Auto',
    body: 'A 50-year-old family shop with modern booking, service history and fleet invoicing.',
  },
]
</script>

<template>
  <section class="k-section usecases">
    <div class="k-wrap">
      <div class="k-eyebrow usecases__eyebrow" data-reveal>
        <span>USE CASES</span>
      </div>
      <h2 class="k-heading usecases__title" data-reveal>Who builds with Kinotic</h2>

      <div class="usecases__grid">
        <article v-for="story in stories" :key="story.slug" class="usecases__card" data-reveal>
          <div class="usecases__image">
            <picture>
              <source :srcset="`/images/use-cases/${story.slug}.webp`" type="image/webp">
              <img :src="`/images/use-cases/${story.slug}.png`" :alt="story.name" loading="lazy" width="760" height="680">
            </picture>
          </div>
          <div class="usecases__copy">
            <div class="usecases__name">{{ story.name }}</div>
            <p>{{ story.body }}</p>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.usecases {
  --k-section-padding: 80px 0 120px;
}

.usecases__eyebrow {
  justify-content: center;
  margin-bottom: 18px;
}

.usecases__title {
  font-size: clamp(27px, 3.2vw, 42px);
  text-align: center;
  margin-bottom: 56px;
}

.usecases__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.usecases__card {
  border: 1px solid var(--color-k-border);
  background: var(--color-k-bg-card);
}

.usecases__image {
  position: relative;
  height: 340px;
  overflow: hidden;
}

.usecases__image img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.usecases__copy {
  padding: 38px 34px 42px;
}

.usecases__name {
  font-family: var(--font-k-mono);
  font-size: 23px;
  color: var(--color-k-text);
  margin-bottom: 14px;
}

.usecases__copy p {
  font-size: 16px;
  line-height: 1.6;
  color: var(--color-k-body);
  margin: 0;
}

@media (max-width: 1020px) {
  .usecases__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .usecases__image {
    height: 250px;
  }
}
</style>
