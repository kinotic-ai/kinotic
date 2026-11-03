<script setup lang="ts">
interface DemoVideo {
  videoId: string
  kicker: string
  name: string
  poster: string
  blurb: string
}

const featured: DemoVideo = {
  videoId: 'PLiqZyAafiU',
  kicker: '01 · 2:46',
  name: 'Why Kinotic?',
  poster: '/images/vc/why-kinotic-poster.webp',
  blurb: 'AI can write code. But can it ship? The gap between a prototype and a production system, '
    + 'and why Kinotic exists to close it.',
}

const more: DemoVideo[] = [
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

      <div id="demo" class="demo__stage">
        <article class="demo__feature" data-reveal>
          <VcFeaturedVideo
            :poster="featured.poster"
            :label="`Play ${featured.name}`"
            @play="$emit('play', featured.videoId)"
          />
          <div class="demo__feature-body">
            <div>
              <div class="demo__kicker">{{ featured.kicker }}</div>
              <h3 class="demo__name demo__name--feature">{{ featured.name }}</h3>
            </div>
            <p class="demo__blurb demo__blurb--feature">{{ featured.blurb }}</p>
          </div>
        </article>

        <div class="demo__more">
          <article v-for="video in more" :key="video.videoId" class="demo__card" data-reveal>
            <VcVideoPoster
              class="demo__thumb"
              :poster="video.poster"
              :label="`Play ${video.name}`"
              @play="$emit('play', video.videoId)"
            />
            <div class="demo__kicker">{{ video.kicker }}</div>
            <h3 class="demo__name">{{ video.name }}</h3>
            <p class="demo__blurb">{{ video.blurb }}</p>
          </article>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.demo {
  overflow: hidden;
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
  margin: 0 0 64px;
  color: var(--color-k-muted);
  font-size: 16px;
  line-height: 1.6;
}

.demo__stage {
  scroll-margin-top: 132px;
}

.demo__feature-body {
  display: grid;
  grid-template-columns: 5fr 7fr;
  gap: 48px;
  align-items: start;
  margin-top: 34px;
}

.demo__more {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  margin-top: 48px;
}

.demo__card {
  padding: 24px 24px 30px;
  border: 1px solid var(--color-k-border);
  border-radius: 18px;
  background: var(--color-k-bg-card);
}

.demo__thumb {
  margin-bottom: 24px;
  aspect-ratio: 16 / 9;
  border: 1px solid var(--color-k-border);
  border-radius: 10px;
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

.demo__name--feature {
  margin: 0;
  font-size: 24px;
}

.demo__blurb {
  margin: 0;
  color: var(--color-k-muted);
  font-size: 13.5px;
  line-height: 1.65;
}

.demo__blurb--feature {
  font-size: 15px;
}

@media (max-width: 768px) {
  .demo {
    --k-section-padding: 64px 0;
  }

  .demo__feature-body,
  .demo__more {
    grid-template-columns: 1fr;
  }

  .demo__feature-body {
    gap: 12px;
    margin-top: 26px;
  }

  .demo__more {
    margin-top: 40px;
  }

  .demo__card {
    border-radius: 12px;
  }
}
</style>
