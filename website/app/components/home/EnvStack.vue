<script setup lang="ts">
/**
 * The branch-environment deck on the CI/CD feature card. Four cards run the same
 * cycle one slot apart, so the deck always holds a front, a mid and a back card
 * while a fourth is off-deck, and each pipeline stage takes the front slot in turn.
 *
 * `v1` folds the front card down and under the deck. `v2` runs that in reverse:
 * the next stage rises up in front while the cards already there recede.
 */
defineProps<{ version: 'v1' | 'v2' }>()

/** The stages a pushed branch moves through, one per card in the deck. */
const PIPELINE_STEPS = [
  { branch: 'feature/checkout', stage: 'PUSH · a41f9c', icon: 'Folder', live: false },
  { branch: 'feature/checkout', stage: 'BUILD · 3 SERVICES', icon: 'Cog', live: false },
  { branch: 'feature/checkout', stage: 'DEPLOY · POD PENDING', icon: 'Refresh', live: false },
  { branch: 'feature/checkout', stage: 'LIVE · DEV · ENV', icon: 'ThLarge', live: true },
] as const
</script>

<template>
  <div class="envstack" :class="`envstack--${version}`" aria-hidden="true">
    <div v-for="step in PIPELINE_STEPS" :key="step.stage" class="envstack__card">
      <span class="envstack__body">
        <span class="envstack__icon"><KinoticIcon :name="step.icon" :size="16" /></span>
        <span class="envstack__meta">
          <span class="envstack__branch">{{ step.branch }}</span>
          <span class="envstack__tags">{{ step.stage }}</span>
        </span>
        <span class="k-livedot envstack__dot" :class="{ 'envstack__dot--live': step.live }" />
      </span>
    </div>
  </div>
</template>

<style scoped>
.envstack {
  position: relative;
  width: 260px;
  height: 132px;
  perspective: 820px;
}

.envstack__card {
  position: absolute;
  left: 0;
  top: 30px;
  width: 260px;
  height: 72px;
  padding: 0 20px;
  border: 1px solid rgba(40, 254, 180, 0.8);
  border-radius: 13px;
  background: #0E1511;
  /* Hinging on the top edge is what makes the card leaving or arriving read as a
     fold: the body tips away from the viewer as it passes under the deck. */
  transform-origin: 50% 0%;
  backface-visibility: hidden;
  will-change: transform, opacity;
  animation-duration: 13.6s;
  animation-timing-function: cubic-bezier(0.65, 0, 0.35, 1);
  animation-iteration-count: infinite;
  animation-delay: var(--envstack-phase);
}

/* A card in one of the slots behind sits high enough that its contents clear the
   front card's top edge and show through, so a card only shows what it is
   carrying over the stretch where it owns the front slot. */
.envstack__body {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 100%;
  opacity: 0;
  animation-duration: 13.6s;
  animation-timing-function: cubic-bezier(0.65, 0, 0.35, 1);
  animation-iteration-count: infinite;
  animation-delay: var(--envstack-phase);
}

/* ── Slot poses ──
   Where each card sits before its animation takes over, and what reduced motion
   freezes it at. The two versions travel the deck in opposite directions, so
   they disagree about which card starts at mid and which starts off-deck. */
.envstack__card:nth-child(1) {
  transform: translateY(0) scale(1);
  opacity: 1;
  z-index: 3;
  box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
}

.envstack__card:nth-child(1) .envstack__body {
  opacity: 1;
}

.envstack__card:nth-child(3) {
  transform: translateY(-27px) scale(0.86);
  opacity: 0.45;
  z-index: 1;
  border-color: rgba(40, 254, 180, 0.25);
}

.envstack--v1 .envstack__card:nth-child(2),
.envstack--v2 .envstack__card:nth-child(4) {
  transform: translateY(-14px) scale(0.93);
  opacity: 0.7;
  z-index: 2;
  border-color: rgba(40, 254, 180, 0.4);
}

.envstack--v1 .envstack__card:nth-child(4),
.envstack--v2 .envstack__card:nth-child(2) {
  opacity: 0;
  z-index: 0;
}

/* ── v1: the front card folds down and under the deck ── */
.envstack--v1 .envstack__card {
  animation-name: envstack-fold;
}

.envstack--v1 .envstack__body {
  animation-name: envstack-fold-reveal;
}

.envstack--v1 .envstack__card:nth-child(1) { --envstack-phase: -6.8s; }
.envstack--v1 .envstack__card:nth-child(2) { --envstack-phase: -3.4s; }
.envstack--v1 .envstack__card:nth-child(3) { --envstack-phase: 0s; }
.envstack--v1 .envstack__card:nth-child(4) { --envstack-phase: -10.2s; }

@keyframes envstack-fold {
  0%, 15% {
    transform: translateY(-27px) scale(0.86);
    opacity: 0.45;
    border-color: rgba(40, 254, 180, 0.25);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 1;
  }

  25%, 40% {
    transform: translateY(-14px) scale(0.93);
    opacity: 0.7;
    border-color: rgba(40, 254, 180, 0.4);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 2;
  }

  50%, 65% {
    transform: translateY(0) scale(1);
    opacity: 1;
    border-color: rgba(40, 254, 180, 0.8);
    box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
    z-index: 3;
  }

  /* Drops behind the rest of the deck for the whole fold, otherwise the card it
     is sliding under would be the one that gets covered. The fold leads with its
     travel so the card is clear of the front slot before the one replacing it
     brings its own label up. */
  65.01% {
    z-index: 0;
    animation-timing-function: cubic-bezier(0.3, 0.85, 0.4, 1);
  }

  69% {
    transform: translateY(32px) scale(0.96) rotateX(44deg);
    opacity: 0.55;
  }

  75% {
    transform: translateY(52px) scale(0.92) rotateX(72deg);
    opacity: 0;
    z-index: 0;
  }

  /* Back of the deck, still invisible: the return trip has to happen in one
     frame or the card would be seen travelling back up. */
  75.01%, 90% {
    transform: translateY(-27px) scale(0.86);
    opacity: 0;
    border-color: rgba(40, 254, 180, 0.25);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 1;
  }

  100% {
    transform: translateY(-27px) scale(0.86);
    opacity: 0.45;
    border-color: rgba(40, 254, 180, 0.25);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 1;
  }
}

@keyframes envstack-fold-reveal {
  0%, 45% { opacity: 0; }
  49%, 75% { opacity: 1; }
  75.01%, 100% { opacity: 0; }
}

/* ── v2: the next card rises in front and the deck recedes behind it ── */
.envstack--v2 .envstack__card {
  animation-name: envstack-rise;
}

.envstack--v2 .envstack__body {
  animation-name: envstack-rise-reveal;
}

.envstack--v2 .envstack__card:nth-child(1) { --envstack-phase: 0s; }
.envstack--v2 .envstack__card:nth-child(2) { --envstack-phase: -10.2s; }
.envstack--v2 .envstack__card:nth-child(3) { --envstack-phase: -6.8s; }
.envstack--v2 .envstack__card:nth-child(4) { --envstack-phase: -3.4s; }

@keyframes envstack-rise {
  0%, 15% {
    transform: translateY(0) scale(1) rotateX(0deg);
    opacity: 1;
    border-color: rgba(40, 254, 180, 0.8);
    box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
    z-index: 3;
  }

  /* Gives up the top of the deck the moment it starts receding, so the card
     rising out from under it passes in front rather than behind. */
  15.01% {
    z-index: 2;
  }

  25%, 40% {
    transform: translateY(-14px) scale(0.93) rotateX(0deg);
    opacity: 0.7;
    border-color: rgba(40, 254, 180, 0.4);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 2;
  }

  50%, 65% {
    transform: translateY(-27px) scale(0.86) rotateX(0deg);
    opacity: 0.45;
    border-color: rgba(40, 254, 180, 0.25);
    z-index: 1;
  }

  72% {
    transform: translateY(-38px) scale(0.8) rotateX(0deg);
    opacity: 0;
    z-index: 1;
  }

  /* Under the deck, still invisible: the drop has to happen in one frame or the
     card would be seen crossing the deck on its way down. */
  72.01%, 90% {
    transform: translateY(52px) scale(0.92) rotateX(72deg);
    opacity: 0;
    border-color: rgba(40, 254, 180, 0.8);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 3;
  }

  95% {
    transform: translateY(26px) scale(0.96) rotateX(38deg);
    opacity: 0.75;
  }

  100% {
    transform: translateY(0) scale(1) rotateX(0deg);
    opacity: 1;
    border-color: rgba(40, 254, 180, 0.8);
    box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
    z-index: 3;
  }
}

@keyframes envstack-rise-reveal {
  0%, 15% { opacity: 1; }
  18%, 90% { opacity: 0; }
  97%, 100% { opacity: 1; }
}

.envstack__icon {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  border: 1px solid rgba(40, 254, 180, 0.5);
  color: var(--color-k-mint);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.envstack__meta {
  flex: 1;
}

.envstack__branch {
  display: block;
  font-family: var(--font-k-mono);
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-k-text);
}

.envstack__tags {
  display: block;
  font-family: var(--font-k-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  color: var(--color-k-dim);
  margin-top: 3px;
}

.envstack__dot {
  width: 8px;
  height: 8px;
  background: var(--color-k-red);
}

.envstack__dot--live {
  background: var(--color-k-mint);
}
</style>
