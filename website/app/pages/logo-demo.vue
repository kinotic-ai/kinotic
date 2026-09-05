<script setup lang="ts">
// An unlinked page shared by URL: nothing in the header, footer or sitemap points
// here, and the noindex tag below keeps it out of search results. `nuxt generate`
// only emits it because nuxt.config.ts lists /logo-demo under
// nitro.prerender.routes — the crawler has no link to discover it from.
definePageMeta({ layout: 'home', header: false, footer: false })

useHead({ titleTemplate: '%s' })
useSeoMeta({
  title: 'Kinotic — Logo motion demos',
  description: 'Three motion builds of the Kinotic mark.',
  robots: 'noindex, nofollow',
})

useMarketingMotion()

interface LogoDemo {
  number: string
  title: string
  blurb: string
  video: string
  poster: string
}

const demos: LogoDemo[] = [
  {
    number: '01',
    title: 'Blueprint build',
    blurb: 'Particles collapse into a filament cloud, then a dimensioned blueprint draws the mark. '
      + 'Signs off on BUILD APPS. NOT INFRASTRUCTURE.',
    video: '/logoDemo/Kinotic_logo_build_final.mp4',
    poster: '/logoDemo/Kinotic_logo_build_final-poster.jpg',
  },
  {
    number: '02',
    title: 'Circuit build',
    blurb: 'Board traces converge on a single node until the mark solders itself together, on the '
      + 'same sign-off. Colder palette, no red haze.',
    video: '/logoDemo/logo-build-v2.mp4',
    poster: '/logoDemo/logo-build-v2-poster.jpg',
  },
  {
    number: '03',
    title: 'Schematic build',
    blurb: 'Annotated nodes route into a cloud, light streaks pull through the frame, and pipework '
      + 'folds into the mark. Signs off on PROTOTYPE TO PRODUCTION.',
    video: '/logoDemo/And_another_one_that_really_dr.mp4',
    poster: '/logoDemo/And_another_one_that_really_dr-poster.jpg',
  },
]

const players = useTemplateRef<HTMLVideoElement[]>('players')

const onPlay = (event: Event) => {
  // Each clip carries its own sound design, so leaving the others running would
  // stack three audio tracks.
  const started = event.target
  players.value?.forEach((player) => {
    if (player !== started) {
      player.pause()
    }
  })
}
</script>

<template>
  <section class="demos">
    <div class="demos__glow" />
    <div class="k-wrap demos__inner">
      <div class="k-eyebrow demos__eyebrow" data-reveal>
        <span>BRAND MOTION</span>
      </div>
      <h1 class="k-heading demos__title" data-reveal>Logo demos</h1>
      <p class="demos__lede" data-reveal>
        Three ten-second builds of the Kinotic mark, all ending on the same lockup. This page is
        unlisted — nothing on the site links to it, so pass the URL to whoever needs to weigh in.
      </p>

      <div class="demos__list">
        <article v-for="demo in demos" :key="demo.video" class="demos__row" data-reveal>
          <div class="demos__frame">
            <video
              ref="players"
              class="demos__player"
              :src="demo.video"
              :poster="demo.poster"
              controls
              playsinline
              preload="metadata"
              @play="onPlay"
            />
          </div>
          <div class="demos__meta">
            <h2 class="demos__label">
              <span class="demos__number">{{ demo.number }}</span>
              <span class="demos__name">{{ demo.title }}</span>
            </h2>
            <p class="demos__blurb">{{ demo.blurb }}</p>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.demos {
  position: relative;
  overflow: hidden;
  padding: 168px 0 120px;
}

.demos__glow {
  position: absolute;
  top: -220px;
  left: 50%;
  transform: translateX(-50%);
  width: 1200px;
  height: 720px;
  pointer-events: none;
  background: radial-gradient(ellipse 50% 50% at 50% 40%, rgba(40, 254, 180, 0.13), transparent 70%);
}

.demos__inner {
  position: relative;
}

.demos__eyebrow {
  margin-bottom: 18px;
}

.demos__title {
  font-size: clamp(34px, 4.4vw, 56px);
  line-height: 1.08;
  letter-spacing: -0.02em;
  margin-bottom: 20px;
}

.demos__lede {
  font-family: var(--font-k-body);
  font-size: 16px;
  line-height: 1.7;
  color: var(--color-k-body);
  max-width: 620px;
  margin: 0 0 44px;
}

.demos__list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.demos__row {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  border: 1px solid var(--color-k-border);
  background: var(--color-k-bg-card);
}

.demos__frame {
  border-right: 1px solid var(--color-k-border);
}

.demos__player {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
}

.demos__meta {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  padding: 30px 34px;
}

.demos__label {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0;
}

.demos__number {
  font-family: var(--font-k-mono);
  font-size: 12px;
  letter-spacing: 0.14em;
  color: var(--color-k-faint);
}

.demos__name {
  font-family: var(--font-k-display);
  font-weight: 600;
  font-size: 20px;
  letter-spacing: -0.01em;
  color: var(--color-k-heading);
}

.demos__blurb {
  font-family: var(--font-k-body);
  font-size: 14.5px;
  line-height: 1.65;
  color: var(--color-k-muted);
  margin: 0;
}

/* Below this the copy column is too narrow to read beside a 16:9 frame, so the
   copy drops under the player it describes. */
@media (max-width: 860px) {
  .demos__row {
    grid-template-columns: 1fr;
  }

  .demos__frame {
    border-right: none;
    border-bottom: 1px solid var(--color-k-border);
  }

  .demos__meta {
    padding: 22px 24px;
  }
}

@media (max-width: 768px) {
  .demos {
    padding: 128px 0 72px;
  }
}
</style>
