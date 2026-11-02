<script setup lang="ts">
interface DemoVideo {
  videoId: string
  kicker: string
  name: string
  poster: string
  blurb: string
}

// The first video is the feature; the rest are listed below it.
const videos: DemoVideo[] = [
  {
    videoId: 'PLiqZyAafiU',
    kicker: '01 · 2:46',
    name: 'Why Kinotic?',
    poster: '/images/vc/why-kinotic-poster.webp',
    blurb: 'AI can write code. But can it ship? The gap between a prototype and a production system, '
      + 'and why Kinotic exists to close it.',
  },
  {
    videoId: 'wHOcN2r82t4',
    kicker: '02 · 3:02',
    name: 'Core Capabilities',
    poster: '/images/vc/core-capabilities-poster.webp',
    blurb: 'AI app development, CI/CD and cloud deployment in one platform — what Kinotic takes off '
      + 'your plate.',
  },
  {
    videoId: '8oOowmOBCzI',
    kicker: '03 · 4:50',
    name: 'Getting Started',
    poster: '/images/vc/getting-started-poster.webp',
    blurb: 'Build and deploy an AI application with Claude Code, from a blank workspace to a live '
      + 'environment.',
  },
]

defineEmits<{ play: [videoId: string] }>()
</script>

<template>
  <section class="k-section demo">
    <div class="k-wrap">
      <div class="k-eyebrow demo__eyebrow" data-reveal>
        <span>PRODUCT DEMO</span>
      </div>
      <h2 class="k-heading demo__title" data-reveal>See the product in motion</h2>
      <p class="demo__lede" data-reveal>
        Three short videos: why we built Kinotic, what it does, and how to start building on it.
      </p>

      <div id="demo" class="demo__list">
        <article
          v-for="(video, index) in videos"
          :key="video.videoId"
          class="demo__card"
          :class="index === 0 ? 'demo__card--feature' : 'demo__card--row'"
          data-reveal
        >
          <VcVideoPoster
            class="demo__player"
            :poster="video.poster"
            :label="`Play ${video.name}`"
            @play="$emit('play', video.videoId)"
          />
          <div class="demo__body">
            <div class="demo__kicker">{{ video.kicker }}</div>
            <h3 class="demo__name">{{ video.name }}</h3>
            <p class="demo__blurb">{{ video.blurb }}</p>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.demo {
  border-top: 1px solid var(--color-k-border);
}

.demo__eyebrow {
  margin-bottom: 18px;
}

.demo__title {
  margin-bottom: 16px;
  font-size: clamp(29px, 3.4vw, 44px);
}

.demo__lede {
  max-width: 560px;
  margin: 0 0 52px;
  color: var(--color-k-muted);
  font-size: 16px;
  line-height: 1.6;
}

.demo__list {
  display: flex;
  flex-direction: column;
  gap: 24px;
  scroll-margin-top: 132px;
}

.demo__card {
  border: 1px solid var(--color-k-border);
  background: var(--color-k-bg-card);
}

.demo__player {
  aspect-ratio: 16 / 9;
}

.demo__card--feature .demo__player {
  border-bottom: 1px solid var(--color-k-border);
}

.demo__card--feature .demo__body {
  padding: 30px 32px 34px;
}

.demo__card--row {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
  align-items: center;
}

.demo__card--row .demo__player {
  border-right: 1px solid var(--color-k-border);
}

.demo__card--row .demo__body {
  padding: 30px 36px;
}

.demo__kicker {
  margin-bottom: 12px;
  color: var(--color-k-mint);
  font-family: var(--font-k-mono);
  font-size: 11px;
  letter-spacing: 0.14em;
}

.demo__name {
  margin: 0 0 10px;
  color: var(--color-k-text);
  font-family: var(--font-k-mono);
  font-size: 19px;
  font-weight: 400;
}

.demo__blurb {
  margin: 0;
  color: var(--color-k-muted);
  font-size: 13.5px;
  line-height: 1.65;
}

@media (max-width: 768px) {
  .demo {
    --k-section-padding: 64px 0;
  }

  .demo__card--row {
    grid-template-columns: 1fr;
  }

  .demo__card--row .demo__player {
    border-right: none;
    border-bottom: 1px solid var(--color-k-border);
  }

  .demo__card--feature .demo__body,
  .demo__card--row .demo__body {
    padding: 26px 24px 30px;
  }
}
</style>
