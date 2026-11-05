<script setup lang="ts">
const FOUNDERS_VIDEO = 'K3du2BlRYPI'
const FULL_STORY_VIDEO = '9b5afQQb0o0'

defineEmits<{ play: [videoId: string] }>()
</script>

<template>
  <section id="about" class="about">
    <div class="k-wrap about__inner">
      <div class="k-eyebrow about__eyebrow" data-reveal>
        <span>ABOUT US</span>
      </div>
      <h1 class="k-heading about__title" data-reveal>Our Story</h1>
      <p class="about__lede" data-reveal>
        Kinotic did not begin with the AI boom. It is the convergence of more than two decades of
        building software, infrastructure and products together.
      </p>

      <VcFeaturedVideo
        class="about__video"
        data-reveal
        poster="/images/vc/founders-video-poster.webp"
        label="Play the founders video"
        @play="$emit('play', FOUNDERS_VIDEO)"
      />

      <div class="about__story" data-reveal>
        <div class="about__story-copy">
          <span class="about__chip">LONG-FORM · 1 HR 13 MIN</span>
          <h3 class="k-heading about__story-title">Really want to know our story?</h3>
          <p class="about__story-blurb">
            The full founders conversation — where the idea came from, why now, and how two decades
            of shipping led to Kinotic.
          </p>
          <button type="button" class="k-btn k-btn--red about__story-button" @click="$emit('play', FULL_STORY_VIDEO)">
            <svg width="12" height="12" viewBox="0 0 44 44" fill="none" aria-hidden="true">
              <path d="M35.5 18.6c2.9 1.7 2.9 5.1 0 6.8L15.9 36.7c-2.9 1.7-6.5-.4-6.5-3.4V10.7c0-3 3.6-5.1 6.5-3.4L35.5 18.6Z" fill="#fff" />
            </svg>
            <span>Watch the full story</span>
          </button>
        </div>
        <VcVideoPoster
          class="about__story-thumb"
          poster="/images/vc/full-story-poster.webp"
          label="Play the full founders story"
          @play="$emit('play', FULL_STORY_VIDEO)"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.about {
  position: relative;
  padding: 180px 0 90px;
  overflow: hidden;
  scroll-margin-top: 76px;
}

.about__inner {
  position: relative;
  z-index: 2;
}

.about__eyebrow {
  justify-content: center;
  margin-bottom: 22px;
}

.about__title {
  font-size: clamp(48px, 6.2vw, 86px);
  font-weight: 500;
  line-height: 1.04;
  text-align: center;
}

.about__lede {
  max-width: 820px;
  margin: 34px auto 0;
  color: var(--color-k-muted);
  font-size: 21px;
  line-height: 1.55;
  text-align: center;
}

.about__video {
  max-width: 900px;
  margin: 64px auto 0;
}

.about__story {
  display: flex;
  align-items: center;
  gap: 48px;
  max-width: 900px;
  margin: 40px auto 0;
  padding: 40px 44px;
  border: 1px solid var(--color-k-border);
  border-radius: 18px;
  background: var(--color-k-bg-card);
}

.about__story-copy {
  flex: 1;
  min-width: 0;
}

.about__chip {
  display: inline-flex;
  margin-bottom: 18px;
  padding: 6px 10px;
  border-radius: 3px;
  background: #101E19;
  color: var(--color-k-mint);
  font-family: var(--font-k-mono);
  font-size: 11px;
  letter-spacing: 0.14em;
}

.about__story-title {
  margin-bottom: 12px;
  font-size: clamp(22px, 2.2vw, 28px);
  line-height: 1.2;
}

.about__story-blurb {
  max-width: 460px;
  margin: 0 0 26px;
  color: var(--color-k-muted);
  font-size: 14.5px;
  line-height: 1.65;
}

.about__story-button {
  padding: 13px 22px;
  border: 0;
  font-size: 13px;
  cursor: pointer;
}

.about__story-thumb {
  flex: none;
  width: 300px;
  aspect-ratio: 16 / 9;
  border: 1px solid var(--color-k-border);
  border-radius: 10px;
}

@media (max-width: 768px) {
  .about {
    padding: 148px 0 64px;
  }

  .about__lede {
    font-size: 18px;
  }

  .about__story {
    flex-direction: column;
    align-items: stretch;
    gap: 28px;
    padding: 28px 24px;
    border-radius: 12px;
  }

  .about__story-thumb {
    width: 100%;
  }
}
</style>
