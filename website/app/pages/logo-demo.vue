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

const activeIndex = ref(0)
const active = computed(() => demos[activeIndex.value]!)
const stage = useTemplateRef<HTMLVideoElement>('stage')

const select = async (index: number) => {
  activeIndex.value = index
  // Rebinding src restarts the media element's load algorithm, so play() has to
  // wait for the patched element. The click is the user gesture that lets it
  // start with sound; a rejected promise leaves the stage on its poster.
  await nextTick()
  await stage.value?.play().catch(() => {})
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
        Three ten-second builds of the Kinotic mark, all ending on the same lockup. Pick one below
        to watch it full size. This page is unlisted — nothing on the site links to it, so pass the
        URL to whoever needs to weigh in.
      </p>

      <div class="demos__stage" data-reveal>
        <video
          ref="stage"
          class="demos__player"
          :src="active.video"
          :poster="active.poster"
          controls
          playsinline
          preload="metadata"
        />
      </div>

      <div class="demos__picker" data-reveal>
        <button
          v-for="(demo, index) in demos"
          :key="demo.video"
          type="button"
          class="demos__tile"
          :class="{ 'demos__tile--active': index === activeIndex }"
          :aria-pressed="index === activeIndex"
          @click="select(index)"
        >
          <span class="demos__thumb">
            <img :src="demo.poster" alt="" width="1280" height="720">
            <span class="demos__play" aria-hidden="true">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 7.5l8 4.5-8 4.5z" fill="currentColor" /></svg>
            </span>
          </span>
          <span class="demos__meta">
            <span class="demos__label">
              <span class="demos__number">{{ demo.number }}</span>
              <span class="demos__name">{{ demo.title }}</span>
            </span>
            <span class="demos__blurb">{{ demo.blurb }}</span>
          </span>
        </button>
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

.demos__stage {
  position: relative;
  border: 1px solid var(--color-k-border);
  background: var(--color-k-bg-panel);
}

.demos__player {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
}

.demos__picker {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin-top: 18px;
}

.demos__tile {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px;
  text-align: left;
  cursor: pointer;
  background: var(--color-k-bg-card);
  border: 1px solid var(--color-k-border);
  transition: border-color 0.2s ease, background 0.2s ease;
}

.demos__tile:hover {
  border-color: #4A4954;
  background: var(--color-k-bg-chip);
}

.demos__tile--active {
  border-color: var(--color-k-mint);
}

.demos__thumb {
  position: relative;
  display: block;
  overflow: hidden;
  background: #000;
}

.demos__thumb img {
  display: block;
  width: 100%;
  height: auto;
  opacity: 0.72;
  transition: opacity 0.2s ease;
}

.demos__tile:hover .demos__thumb img,
.demos__tile--active .demos__thumb img {
  opacity: 1;
}

.demos__play {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: var(--color-k-bg);
  background: rgba(255, 255, 255, 0.82);
  transition: background 0.2s ease;
}

.demos__tile:hover .demos__play,
.demos__tile--active .demos__play {
  background: var(--color-k-mint);
}

.demos__play svg {
  width: 18px;
  height: 18px;
}

.demos__meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demos__label {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.demos__number {
  font-family: var(--font-k-mono);
  font-size: 12px;
  letter-spacing: 0.14em;
  color: var(--color-k-faint);
}

.demos__tile--active .demos__number {
  color: var(--color-k-mint);
}

.demos__name {
  font-family: var(--font-k-display);
  font-weight: 600;
  font-size: 17px;
  color: var(--color-k-heading);
}

.demos__blurb {
  font-family: var(--font-k-body);
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--color-k-muted);
}

@media (max-width: 900px) {
  .demos__picker {
    grid-template-columns: 1fr;
  }
}

/* One per row is wide enough to sit the thumbnail beside the copy, but only until
   the copy column gets too narrow to read — below that the tiles stack again. */
@media (min-width: 561px) and (max-width: 900px) {
  .demos__tile {
    flex-direction: row;
    align-items: center;
    gap: 16px;
  }

  .demos__thumb {
    flex: 0 0 200px;
  }

  .demos__meta {
    flex: 1;
  }
}

@media (max-width: 768px) {
  .demos {
    padding: 128px 0 72px;
  }
}
</style>
