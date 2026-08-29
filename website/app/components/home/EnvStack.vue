<script setup lang="ts">
/**
 * The branch-environment deck on the CI/CD feature card. Five cards run the same
 * cycle one slot apart, so the deck always holds a front, a mid, a back and a deep
 * card while a fifth is off-deck, and each pipeline stage takes the front slot in turn.
 *
 * `v1` folds the front card down and under the deck. `v2` runs that in reverse:
 * the next stage rises up in front while the cards already there recede, with no
 * fold — the card simply slides into the slot.
 */
defineProps<{ version: 'v1' | 'v2' }>()

/**
 * The stages a pushed branch moves through, one per card in the deck. The stage
 * is what the card leads with — it is the only line that changes for most of the
 * run, so the branch it is working on rides underneath as context.
 */
const PIPELINE_STEPS = [
  { stage: 'Push feature', context: 'feature/checkout', icon: 'Folder', live: false },
  { stage: 'Build', context: 'feature/checkout', icon: 'Cog', live: false },
  { stage: 'Unit tests', context: 'feature/checkout', icon: 'ThLarge', live: false },
  { stage: 'Integration tests', context: 'feature/checkout', icon: 'Database', live: false },
  { stage: 'Deploy to prod', context: 'main · production', icon: 'Refresh', live: true },
] as const
</script>

<template>
  <div class="envstack" :class="`envstack--${version}`" aria-hidden="true">
    <div v-for="step in PIPELINE_STEPS" :key="step.stage" class="envstack__card">
      <span class="envstack__body">
        <span class="envstack__icon"><KinoticIcon :name="step.icon" :size="16" /></span>
        <span class="envstack__meta">
          <span class="envstack__stage">{{ step.stage }}</span>
          <span class="envstack__context">{{ step.context }}</span>
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
  height: 150px;
  perspective: 820px;
  /* One trip through all five stages. Each card's phase is a fifth of this, so
     the pace of the whole deck is tuned from here. */
  --envstack-cycle: 32.5s;
}

.envstack__card {
  position: absolute;
  left: 0;
  top: 36px;
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
  animation-duration: var(--envstack-cycle);
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
  animation-duration: var(--envstack-cycle);
  animation-timing-function: cubic-bezier(0.65, 0, 0.35, 1);
  animation-iteration-count: infinite;
  animation-delay: var(--envstack-phase);
}

/* ── Slot poses ──
   Where each card sits before its animation takes over, and what reduced motion
   freezes it at. The two versions travel the deck in opposite directions, so they
   disagree about which card starts in which slot. */
.envstack__card:nth-child(1) {
  transform: translateY(0) scale(1);
  opacity: 1;
  z-index: 4;
  box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
}

.envstack__card:nth-child(1) .envstack__body {
  opacity: 1;
}

.envstack--v1 .envstack__card:nth-child(2),
.envstack--v2 .envstack__card:nth-child(5) {
  transform: translateY(-12px) scale(0.94);
  opacity: 0.72;
  z-index: 3;
  border-color: rgba(40, 254, 180, 0.45);
}

.envstack--v1 .envstack__card:nth-child(3),
.envstack--v2 .envstack__card:nth-child(4) {
  transform: translateY(-23px) scale(0.885);
  opacity: 0.5;
  z-index: 2;
  border-color: rgba(40, 254, 180, 0.3);
}

.envstack--v1 .envstack__card:nth-child(4),
.envstack--v2 .envstack__card:nth-child(3) {
  transform: translateY(-33px) scale(0.83);
  opacity: 0.3;
  z-index: 1;
  border-color: rgba(40, 254, 180, 0.2);
}

.envstack--v1 .envstack__card:nth-child(5),
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

/* Whole slots, unlike v2 below: a card folding away keeps its label the whole
   way down, so holding this version part-way through a handover would open the
   section with two labels on screen at once. */
.envstack--v1 .envstack__card:nth-child(1) { --envstack-phase: calc(var(--envstack-cycle) * -0.6); }
.envstack--v1 .envstack__card:nth-child(2) { --envstack-phase: calc(var(--envstack-cycle) * -0.4); }
.envstack--v1 .envstack__card:nth-child(3) { --envstack-phase: calc(var(--envstack-cycle) * -0.2); }
.envstack--v1 .envstack__card:nth-child(4) { --envstack-phase: 0s; }
.envstack--v1 .envstack__card:nth-child(5) { --envstack-phase: calc(var(--envstack-cycle) * -0.8); }

@keyframes envstack-fold {
  0%, 12% {
    transform: translateY(-33px) scale(0.83);
    opacity: 0.3;
    border-color: rgba(40, 254, 180, 0.2);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 1;
  }

  20%, 32% {
    transform: translateY(-23px) scale(0.885);
    opacity: 0.5;
    border-color: rgba(40, 254, 180, 0.3);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 2;
  }

  40%, 52% {
    transform: translateY(-12px) scale(0.94);
    opacity: 0.72;
    border-color: rgba(40, 254, 180, 0.45);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 3;
  }

  60%, 72% {
    transform: translateY(0) scale(1);
    opacity: 1;
    border-color: rgba(40, 254, 180, 0.8);
    box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
    z-index: 4;
  }

  /* Drops behind the rest of the deck for the whole fold, otherwise the card it
     is sliding under would be the one that gets covered. Nothing else belongs in
     here: a keyframe between 72% and 80% that carried a position would restart
     the easing and stall the card halfway through the fold. */
  72.01% {
    z-index: 0;
  }

  80% {
    transform: translateY(52px) scale(0.92) rotateX(72deg);
    opacity: 0;
    z-index: 0;
  }

  /* Back of the deck, still invisible: the return trip has to happen in one frame
     or the card would be seen travelling back up. */
  80.01%, 92% {
    transform: translateY(-33px) scale(0.83);
    opacity: 0;
    border-color: rgba(40, 254, 180, 0.2);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 1;
  }

  100% {
    transform: translateY(-33px) scale(0.83);
    opacity: 0.3;
    border-color: rgba(40, 254, 180, 0.2);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 1;
  }
}

@keyframes envstack-fold-reveal {
  0%, 52% { opacity: 0; }
  60%, 80% { opacity: 1; }
  80.01%, 100% { opacity: 0; }
}

/* ── v2: the next card rises in front and the deck recedes behind it ── */
.envstack--v2 .envstack__card {
  animation-name: envstack-rise;
}

.envstack--v2 .envstack__body {
  animation-name: envstack-rise-reveal;
}

/* Still a slot apart, but the whole cycle is backed up a little, so the first
   card is still climbing when the deck is released rather than already landed.
   Held on a whole slot it is the one card that never animates into place, which
   reads as a mistake next to every card that follows it. How far back is set by
   the two labels: any earlier and the card being replaced still has a readable
   label, so the section would open on the wrong one. */
.envstack--v2 .envstack__card:nth-child(1) { --envstack-phase: calc(var(--envstack-cycle) * -0.94); }
.envstack--v2 .envstack__card:nth-child(2) { --envstack-phase: calc(var(--envstack-cycle) * -0.74); }
.envstack--v2 .envstack__card:nth-child(3) { --envstack-phase: calc(var(--envstack-cycle) * -0.54); }
.envstack--v2 .envstack__card:nth-child(4) { --envstack-phase: calc(var(--envstack-cycle) * -0.34); }
.envstack--v2 .envstack__card:nth-child(5) { --envstack-phase: calc(var(--envstack-cycle) * -0.14); }

@keyframes envstack-rise {
  0%, 12% {
    transform: translateY(0) scale(1);
    opacity: 1;
    border-color: rgba(40, 254, 180, 0.8);
    box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
    z-index: 4;
  }

  /* Gives up the top of the deck the moment it starts receding, so the card
     rising out from under it passes in front rather than behind. */
  12.01% {
    z-index: 3;
  }

  20%, 32% {
    transform: translateY(-12px) scale(0.94);
    opacity: 0.72;
    border-color: rgba(40, 254, 180, 0.45);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 3;
  }

  40%, 52% {
    transform: translateY(-23px) scale(0.885);
    opacity: 0.5;
    border-color: rgba(40, 254, 180, 0.3);
    z-index: 2;
  }

  60%, 72% {
    transform: translateY(-33px) scale(0.83);
    opacity: 0.3;
    border-color: rgba(40, 254, 180, 0.2);
    z-index: 1;
  }

  80% {
    transform: translateY(-42px) scale(0.78);
    opacity: 0;
    z-index: 1;
  }

  /* Below the deck, still invisible: the drop has to happen in one frame or the
     card would be seen crossing the deck on its way down. A keyframe part-way
     through the rise would restart the easing and stall the card mid-flight, so
     the whole rise is one segment, decelerating — the card leaves with intent
     and settles, and the curve carries opacity too, so it is legible while it
     travels instead of only once it lands. */
  80.01%, 92% {
    transform: translateY(74px) scale(1);
    opacity: 0;
    border-color: rgba(40, 254, 180, 0.8);
    box-shadow: 0 0 0 rgba(40, 254, 180, 0);
    z-index: 4;
    animation-timing-function: cubic-bezier(0.22, 0.78, 0.3, 1);
  }

  100% {
    transform: translateY(0) scale(1);
    opacity: 1;
    border-color: rgba(40, 254, 180, 0.8);
    box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
    z-index: 4;
  }
}

/* The arriving label is switched on at the foot of the rise and travels up with
   the card, so the card carries its stage in rather than filling it in once it
   lands. Switching rather than fading is safe there: the card is still fully
   transparent at that point. That leaves the outgoing label with nowhere to hide,
   since the riser is legible from the first moments of its travel — so it clears
   during the tail of its own dwell, before the card underneath starts moving. Any
   later and the two labels interleave halfway up the rise. */
@keyframes envstack-rise-reveal {
  0%, 9% { opacity: 1; }
  12.5%, 91.9% { opacity: 0; }
  92%, 100% { opacity: 1; }
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

.envstack__stage {
  display: block;
  font-family: var(--font-k-mono);
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-k-text);
  white-space: nowrap;
}

.envstack__context {
  display: block;
  font-family: var(--font-k-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  color: var(--color-k-dim);
  margin-top: 3px;
  white-space: nowrap;
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
