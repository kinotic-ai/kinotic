<script setup lang="ts">
/** A rendered frame of the live-usage chart: the polyline plus where each probe sits on it. */
interface ChartFrame {
  d: string
  redY: number
  headY: number
}

/** Horizontal gap between two samples, in viewBox units. */
const SPACING = 30
/** Where the newest sample — and the mint probe riding it — is pinned. */
const HEAD_X = 278
/** Where the red probe is pinned; it only ever travels vertically. */
const RED_X = 120
/** viewBox units the line travels per second. */
const SCROLL_SPEED = 16
const PEAK_TOP = 26
const PEAK_SPAN = 30
const VALLEY_TOP = 66
const VALLEY_SPAN = 32

// Seeded rather than generated so the server-rendered frame matches the first
// client frame; the walk takes over once the animation starts.
const values = [68, 92, 96, 96, 58, 78, 32, 66, 48, 74, 60, 68, 42]
let peak = true
let offset = 0

/**
 * The next sample scrolling in at the right edge, alternating between a high
 * and a low band so the incoming line keeps the amplitude of the seed. The
 * occasional repeat stops it reading as a perfect sawtooth.
 */
function nextValue(): number {
  if (Math.random() < 0.82) peak = !peak
  return peak
    ? PEAK_TOP + Math.random() * PEAK_SPAN
    : VALLEY_TOP + Math.random() * VALLEY_SPAN
}

/** Height of the line at `x`, interpolated between the two samples either side of it. */
function yAt(x: number): number {
  const last = values.length - 1
  const position = Math.min(last, Math.max(0, (x - HEAD_X - offset) / SPACING + last))
  const index = Math.floor(position)
  const start = values[index]!
  const end = values[Math.min(last, index + 1)]!
  return start + (end - start) * (position - index)
}

function buildFrame(): ChartFrame {
  // The samples sit on a lattice that slides left past both edges, so the path
  // is capped with an interpolated point at each edge rather than a sample.
  const segments = [`M 0 ${yAt(0).toFixed(2)}`]
  for (let i = 0; i < values.length; i++) {
    const x = HEAD_X + offset - (values.length - 1 - i) * SPACING
    if (x > 0 && x < HEAD_X) segments.push(`L ${x.toFixed(2)} ${values[i]!.toFixed(2)}`)
  }
  segments.push(`L ${HEAD_X} ${yAt(HEAD_X).toFixed(2)}`)
  return { d: segments.join(' '), redY: yAt(RED_X), headY: yAt(HEAD_X) }
}

/** The stages a pushed branch moves through, one per card in the deck. */
const PIPELINE_STEPS = [
  { branch: 'feature/checkout', stage: 'PUSH · a41f9c', icon: 'Folder', live: false },
  { branch: 'feature/checkout', stage: 'BUILD · 3 SERVICES', icon: 'Cog', live: false },
  { branch: 'feature/checkout', stage: 'DEPLOY · POD PENDING', icon: 'Refresh', live: false },
  { branch: 'feature/checkout', stage: 'LIVE · DEV · ENV', icon: 'ThLarge', live: true },
] as const

const chart = ref<ChartFrame>(buildFrame())
let frame = 0

onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

  let last = performance.now()
  const tick = (now: number) => {
    // Clamping the delta keeps the line from lurching after a background tab resumes.
    const delta = Math.min(42, now - last)
    last = now
    offset += delta * 0.001 * SCROLL_SPEED
    while (offset >= SPACING) {
      offset -= SPACING
      values.shift()
      values.push(nextValue())
    }
    chart.value = buildFrame()
    frame = requestAnimationFrame(tick)
  }
  frame = requestAnimationFrame(tick)
})

onBeforeUnmount(() => cancelAnimationFrame(frame))
</script>

<template>
  <section class="k-section features">
    <div class="k-wrap">
      <div class="k-eyebrow features__eyebrow" data-reveal>
        <span>FEATURES</span>
      </div>
      <h2 class="k-heading features__title" data-reveal>
        The software ecosystem production applications desperately need
      </h2>

      <div class="features__grid">
        <article class="features__card" data-reveal>
          <div class="features__visual features__visual--mint">
            <div class="features__badge">
              <span class="k-livedot features__badgedot" />Live usage
            </div>
            <svg class="features__chart" viewBox="0 0 300 120" fill="none" aria-hidden="true">
              <defs>
                <linearGradient id="usage-edge" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0" stop-color="#fff" stop-opacity="0" />
                  <stop offset="1" stop-color="#fff" stop-opacity="1" />
                </linearGradient>
                <!-- Dissolves the trailing edge so the line reads as streaming
                     off the left of the card rather than being cut off there. -->
                <mask id="usage-trail">
                  <rect x="0" y="0" width="72" height="120" fill="url(#usage-edge)" />
                  <rect x="72" y="0" width="228" height="120" fill="#fff" />
                </mask>
              </defs>
              <g mask="url(#usage-trail)">
                <path
                  :d="chart.d"
                  stroke="#28FEB4"
                  stroke-width="2.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </g>
              <circle :cx="RED_X" :cy="chart.redY" r="4.5" fill="#EC1F52" />
              <circle :cx="HEAD_X" :cy="chart.headY" r="4.5" fill="#28FEB4" />
            </svg>
          </div>
          <div class="features__caption">
            <div class="features__lead">Observable day one</div>
            <p>
              Traces, spans, metrics and logs aggregated at every level — including LLM prompt
              traces and token cost.
            </p>
          </div>
        </article>

        <article class="features__card" data-reveal>
          <div class="features__visual features__visual--mint">
            <div class="envstack" aria-hidden="true">
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
          </div>
          <div class="features__caption">
            <div class="features__lead">CI/CD day one</div>
            <p>
              Every feature branch gets its own live development environment. Changed code deploys
              wherever it's needed, automatically.
            </p>
          </div>
        </article>

        <article class="features__card" data-reveal>
          <div class="features__visual features__visual--globe">
            <svg class="features__globe" viewBox="0 0 513 300" fill="none" aria-hidden="true">
              <circle cx="256.5" cy="150" r="96" stroke="rgba(255,255,255,.15)" stroke-width="1.2" />
              <ellipse cx="256.5" cy="150" rx="96" ry="34" stroke="rgba(255,255,255,.15)" stroke-width="1.2" />
              <ellipse cx="256.5" cy="150" rx="96" ry="68" stroke="rgba(255,255,255,.15)" stroke-width="1.2" />
              <g class="globe__meridians">
                <ellipse cx="256.5" cy="150" rx="34" ry="96" stroke="rgba(255,255,255,.15)" stroke-width="1.2" />
                <ellipse cx="256.5" cy="150" rx="68" ry="96" stroke="rgba(255,255,255,.15)" stroke-width="1.2" />
              </g>
              <circle cx="221.5" cy="111" r="5" fill="#EC1F52" />
              <circle class="globe__ping" cx="221.5" cy="111" r="12" stroke="#EC1F52" stroke-width="1.2" />
              <circle cx="299.5" cy="143" r="5" fill="#28FEB4" />
              <circle class="globe__ping" cx="299.5" cy="143" r="14" stroke="rgba(40,254,180,.5)" stroke-width="1.4" style="animation-delay: .9s;" />
              <circle cx="259.5" cy="197" r="5" fill="#28FEB4" />
              <circle class="globe__ping" cx="259.5" cy="197" r="12" stroke="rgba(40,254,180,.45)" stroke-width="1.2" style="animation-delay: 1.7s;" />
              <rect x="93" y="100" width="102" height="23" rx="6" fill="#101012" stroke="rgba(255,255,255,.15)" />
              <text x="144" y="115.5" font-family="Fira Code, monospace" font-size="11" fill="#B4B4BB" text-anchor="middle">kinotic cloud</text>
            </svg>
          </div>
          <div class="features__caption">
            <div class="features__lead">Internet Scale day one</div>
            <p>
              Start on Kinotic OS Cloud, or forward-deploy the complete system into your own
              Kubernetes. Same platform either way.
            </p>
          </div>
        </article>

        <article class="features__card" data-reveal>
          <div class="features__visual features__visual--faint">
            <svg class="features__layers" viewBox="0 0 300 170" fill="none" aria-hidden="true">
              <g class="layer">
                <path d="M 150 22 L 216 50 L 150 78 L 84 50 Z" fill="rgba(40,254,180,.16)" stroke="#28FEB4" stroke-width="2" />
                <text x="238" y="54" font-family="Fira Code, monospace" font-size="11.5" fill="#28FEB4">Prod</text>
              </g>
              <g class="layer" style="animation-delay: -1.3s;">
                <path d="M 150 58 L 216 86 L 150 114 L 84 86 Z" fill="rgba(40,254,180,.08)" stroke="rgba(94,234,195,.6)" stroke-width="2" />
                <text x="238" y="90" font-family="Fira Code, monospace" font-size="11.5" fill="rgba(94,234,195,.7)">Stage</text>
              </g>
              <g class="layer" style="animation-delay: -2.6s;">
                <path d="M 150 94 L 216 122 L 150 150 L 84 122 Z" fill="rgba(255,255,255,.04)" stroke="#5D5D66" stroke-width="2" />
                <text x="238" y="126" font-family="Fira Code, monospace" font-size="11.5" fill="#8A8A92">Dev</text>
              </g>
            </svg>
          </div>
          <div class="features__caption">
            <div class="features__lead">Dev, Stage, Prod day one</div>
            <p>
              Every artifact is a versioned package — resolved, built in order, and reusable across
              applications.
            </p>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.features__eyebrow {
  margin-bottom: 18px;
}

.features__title {
  font-size: clamp(29px, 3.4vw, 44px);
  line-height: 1.18;
  margin-bottom: 56px;
  max-width: 560px;
}

.features__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 26px;
}

.features__card {
  border: 1px solid var(--color-k-border);
  background: var(--color-k-bg-card);
}

.features__visual {
  position: relative;
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid var(--color-k-border);
}

.features__visual--mint {
  background: radial-gradient(ellipse 55% 60% at 50% 55%, rgba(40, 254, 180, 0.07), transparent 75%);
}

.features__visual--globe {
  background: radial-gradient(ellipse 51% 50% at 51% 50%, rgba(40, 254, 180, 0.14), transparent 70%);
}

.features__visual--faint {
  background: radial-gradient(ellipse 55% 60% at 50% 55%, rgba(40, 254, 180, 0.06), transparent 75%);
}

.features__badge {
  position: absolute;
  top: 20px;
  right: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-k-mono);
  font-size: 11px;
  color: var(--color-k-muted);
}

.features__badgedot {
  background: var(--color-k-red);
}

.features__chart {
  width: 300px;
}

.features__chart path {
  filter: drop-shadow(0 0 8px rgba(40, 254, 180, 0.55));
}

.features__globe {
  width: 100%;
  height: 100%;
}

.features__layers {
  width: 300px;
  overflow: visible;
}

.features__caption {
  padding: 30px 34px 34px;
}

.features__lead {
  font-family: var(--font-k-mono);
  font-size: 19px;
  color: var(--color-k-text);
  margin-bottom: 10px;
}

.features__caption p {
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-k-muted);
  margin: 0;
  max-width: 380px;
}

/* ── Per-branch environment card ── */
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
  /* Hinging on the top edge is what makes the exit read as a fold: the body
     tips away from the viewer and slides under the card taking its place. */
  transform-origin: 50% 0%;
  backface-visibility: hidden;
  will-change: transform, opacity;
  animation-name: envstack-cycle;
  animation-duration: 13.6s;
  animation-timing-function: cubic-bezier(0.65, 0, 0.35, 1);
  animation-iteration-count: infinite;
  animation-delay: var(--envstack-phase);
}

/* Every card runs the same four-slot cycle one slot apart, so the deck always
   has a back, a mid, a front and one folding away. The pose declared here is
   the slot each card starts in, and the one reduced motion freezes it at. */
.envstack__card:nth-child(1) {
  --envstack-phase: -6.8s;
  transform: translateY(0) scale(1);
  opacity: 1;
  z-index: 3;
  box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
}

.envstack__card:nth-child(2) {
  --envstack-phase: -3.4s;
  transform: translateY(-14px) scale(0.93);
  opacity: 0.7;
  z-index: 2;
  border-color: rgba(40, 254, 180, 0.4);
}

.envstack__card:nth-child(3) {
  --envstack-phase: 0s;
  transform: translateY(-27px) scale(0.86);
  opacity: 0.45;
  z-index: 1;
  border-color: rgba(40, 254, 180, 0.25);
}

.envstack__card:nth-child(4) {
  --envstack-phase: -10.2s;
  opacity: 0;
  z-index: 0;
}

/* A card in one of the slots behind sits high enough that its contents clear
   the front card's top edge and show through, so a card only shows what it is
   carrying from the moment it lands in the front slot until it has folded away. */
.envstack__body {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 100%;
  opacity: 0;
  animation-name: envstack-reveal;
  animation-duration: 13.6s;
  animation-timing-function: cubic-bezier(0.65, 0, 0.35, 1);
  animation-iteration-count: infinite;
  animation-delay: var(--envstack-phase);
}

.envstack__card:nth-child(1) .envstack__body {
  opacity: 1;
}

@keyframes envstack-reveal {
  0%, 45% { opacity: 0; }
  49%, 75% { opacity: 1; }
  75.01%, 100% { opacity: 0; }
}

@keyframes envstack-cycle {
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

/* ── Globe ── */
.globe__meridians {
  transform-origin: 256.5px 150px;
  animation: globe-spin 34s linear infinite;
}

@keyframes globe-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.globe__ping {
  transform-origin: center;
  transform-box: fill-box;
  animation: globe-ping 2.6s cubic-bezier(0.16, 1, 0.3, 1) infinite;
}

@keyframes globe-ping {
  0% { opacity: 0.9; transform: scale(0.4); }
  70%, 100% { opacity: 0; transform: scale(1.5); }
}

/* ── Environment layers ── */
.layer {
  animation: layer-float 5.5s ease-in-out infinite;
}

@keyframes layer-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

@media (max-width: 930px) {
  .features__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .features__caption {
    padding: 24px 20px 28px;
  }
}
</style>
