<script setup lang="ts">
const RED: [number, number, number] = [236, 31, 82]
const MINT: [number, number, number] = [40, 254, 180]
const BLOB_COUNT = 24

interface Blob {
  x: number
  y: number
  r: number
  seed: number
  speed: number
  color: [number, number, number]
  alpha: number
}

const plasma = useTemplateRef<HTMLCanvasElement>('plasma')

let frame = 0
let stopResize: (() => void) | undefined

/**
 * Paints the drifting colour field behind the hero copy. Under
 * prefers-reduced-motion it renders a single static frame instead of animating.
 */
function startPlasma(canvas: HTMLCanvasElement) {
  const ctx = canvas.getContext('2d')
  const host = canvas.parentElement
  if (!ctx || !host) return

  const dpr = Math.min(2, window.devicePixelRatio || 1)
  let width = 0
  let height = 0

  const resize = () => {
    const rect = host.getBoundingClientRect()
    width = Math.max(1, rect.width)
    height = Math.max(1, rect.height)
    canvas.width = width * dpr
    canvas.height = height * dpr
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  }
  resize()
  window.addEventListener('resize', resize)
  stopResize = () => window.removeEventListener('resize', resize)

  const blobs: Blob[] = Array.from({ length: BLOB_COUNT }, () => ({
    x: Math.random() * width,
    y: Math.random() * height,
    r: 70 + Math.random() * 150,
    seed: Math.random() * 1000,
    speed: 0.28 + Math.random() * 0.5,
    color: Math.random() < 0.6 ? RED : MINT,
    alpha: 0.05 + Math.random() * 0.07,
  }))

  const paint = (blob: Blob, radius: number, fadeToTransparentColor: boolean) => {
    const [r, g, b] = blob.color
    const gradient = ctx.createRadialGradient(blob.x, blob.y, 0, blob.x, blob.y, radius)
    gradient.addColorStop(0, `rgba(${r},${g},${b},${blob.alpha})`)
    gradient.addColorStop(1, fadeToTransparentColor ? `rgba(${r},${g},${b},0)` : 'rgba(0,0,0,0)')
    ctx.fillStyle = gradient
    ctx.beginPath()
    ctx.arc(blob.x, blob.y, radius, 0, Math.PI * 2)
    ctx.fill()
  }

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    ctx.globalCompositeOperation = 'lighter'
    blobs.slice(0, 12).forEach(blob => paint(blob, blob.r, false))
    ctx.globalCompositeOperation = 'source-over'
    return
  }

  let last = performance.now()
  let elapsed = 0
  const tick = (now: number) => {
    // Clamping the delta keeps the field from jumping after a background tab resumes.
    const delta = Math.min(42, now - last)
    last = now
    elapsed += delta * 0.001

    ctx.clearRect(0, 0, width, height)
    ctx.globalCompositeOperation = 'lighter'
    for (const blob of blobs) {
      const angle = Math.sin(blob.x * 0.004 + elapsed * blob.speed + blob.seed)
        + Math.cos(blob.y * 0.004 - elapsed * blob.speed * 0.8 + blob.seed)
      blob.x += Math.cos(angle) * blob.speed * 1.25
      blob.y += Math.sin(angle) * blob.speed * 1.25 - 0.16
      if (blob.x < -180) blob.x = width + 180
      if (blob.x > width + 180) blob.x = -180
      if (blob.y < -180) blob.y = height + 180
      if (blob.y > height + 180) blob.y = -180
      paint(blob, blob.r * (0.72 + 0.28 * Math.sin(elapsed * 1.3 + blob.seed)), true)
    }
    ctx.globalCompositeOperation = 'source-over'
    frame = requestAnimationFrame(tick)
  }
  frame = requestAnimationFrame(tick)
}

onMounted(() => {
  if (plasma.value) startPlasma(plasma.value)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  stopResize?.()
})
</script>

<template>
  <section class="hero">
    <div class="hero__glow" />
    <canvas ref="plasma" class="hero__plasma" aria-hidden="true" />
    <div class="hero__grid" />

    <div class="k-wrap hero__inner">
      <div class="hero__copy">
        <div class="k-eyebrow" data-reveal>
          <span>KINOTIC OS</span>
        </div>
        <h1 class="k-heading hero__title" data-reveal>Prototype to production</h1>
        <p class="hero__lede" data-reveal>
          Linux abstracted the hardware. Kinotic OS abstracts the cloud, so humans and AI can build
          enterprise software at internet scale.
        </p>
        <div class="hero__actions" data-reveal>
          <a href="#cta" class="k-btn k-btn--mint">Get Started</a>
          <a
            href="https://github.com/kinotic-ai/kinotic"
            target="_blank"
            rel="noreferrer"
            class="k-btn k-btn--ghost"
          >View on Github</a>
        </div>
      </div>

      <!-- Software factory: ideas flow in on the red pipes, deployed apps leave on the mint ones. -->
      <div class="hero__visual" data-reveal>
        <svg class="hero__pipes" viewBox="0 0 560 460" preserveAspectRatio="none" aria-hidden="true">
          <path id="fpipe1" d="M 66 74 C 180 74 196 200 264 218" fill="none" stroke="rgba(255,255,255,.09)" stroke-width="1.5" />
          <path id="fpipe2" d="M 48 230 C 140 230 180 228 262 228" fill="none" stroke="rgba(255,255,255,.09)" stroke-width="1.5" />
          <path id="fpipe3" d="M 96 380 C 190 380 202 262 264 240" fill="none" stroke="rgba(255,255,255,.09)" stroke-width="1.5" />
          <path id="fout1" d="M 348 214 C 402 196 378 124 437 104" fill="none" stroke="rgba(40,254,180,.22)" stroke-width="1.5" />
          <path id="fout2" d="M 348 240 C 402 258 378 336 437 356" fill="none" stroke="rgba(40,254,180,.22)" stroke-width="1.5" />

          <rect width="9" height="9" rx="2.5" fill="#EC1F52">
            <animateMotion dur="3.4s" repeatCount="indefinite" rotate="auto"><mpath href="#fpipe1" /></animateMotion>
          </rect>
          <rect width="9" height="9" rx="2.5" fill="#EC1F52" opacity=".85">
            <animateMotion dur="3.4s" begin="-1.7s" repeatCount="indefinite" rotate="auto"><mpath href="#fpipe1" /></animateMotion>
          </rect>
          <rect width="9" height="9" rx="2.5" fill="#EC1F52">
            <animateMotion dur="3.9s" begin="-.9s" repeatCount="indefinite" rotate="auto"><mpath href="#fpipe2" /></animateMotion>
          </rect>
          <rect width="9" height="9" rx="2.5" fill="#EC1F52" opacity=".85">
            <animateMotion dur="3.9s" begin="-2.6s" repeatCount="indefinite" rotate="auto"><mpath href="#fpipe2" /></animateMotion>
          </rect>
          <rect width="9" height="9" rx="2.5" fill="#EC1F52">
            <animateMotion dur="3.6s" begin="-2.1s" repeatCount="indefinite" rotate="auto"><mpath href="#fpipe3" /></animateMotion>
          </rect>

          <g>
            <rect width="18" height="13" rx="3" fill="rgba(40,254,180,.14)" stroke="#28FEB4" stroke-width="1" />
            <line x1="0" y1="4" x2="18" y2="4" stroke="#28FEB4" stroke-width="1" />
            <animateMotion dur="4.2s" repeatCount="indefinite"><mpath href="#fout1" /></animateMotion>
          </g>
          <g opacity=".8">
            <rect width="18" height="13" rx="3" fill="rgba(40,254,180,.14)" stroke="#28FEB4" stroke-width="1" />
            <line x1="0" y1="4" x2="18" y2="4" stroke="#28FEB4" stroke-width="1" />
            <animateMotion dur="4.2s" begin="-2.1s" repeatCount="indefinite"><mpath href="#fout1" /></animateMotion>
          </g>
          <g>
            <rect width="18" height="13" rx="3" fill="rgba(40,254,180,.14)" stroke="#28FEB4" stroke-width="1" />
            <line x1="0" y1="4" x2="18" y2="4" stroke="#28FEB4" stroke-width="1" />
            <animateMotion dur="4.6s" begin="-1.4s" repeatCount="indefinite"><mpath href="#fout2" /></animateMotion>
          </g>
        </svg>

        <div class="hero__label" style="left: 11.8%; top: 16%;">idea</div>
        <div class="hero__label" style="left: 8.6%; top: 50%;">spec</div>
        <div class="hero__label" style="left: 17.1%; top: 82.6%;">prompt</div>

        <div class="hero__core">
          <div class="hero__coretile">
            <KinoticLogo variant="mark" :height="57" mark-color="#EC1F52" />
          </div>
          <div class="hero__corelabel">KINOTIC OS</div>
        </div>

        <div class="hero__chip" style="top: 22.6%;">
          <span class="k-livedot" />
          <span>app.live</span>
        </div>
        <div class="hero__chip" style="top: 77.4%;">
          <span class="k-livedot" />
          <span>api.live</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero {
  position: relative;
  padding: 190px 0 130px;
  overflow: hidden;
}

.hero__glow {
  position: absolute;
  top: -180px;
  left: 50%;
  transform: translateX(-50%);
  width: 1200px;
  height: 700px;
  background: radial-gradient(ellipse 50% 50% at 50% 40%, rgba(236, 31, 82, 0.16), transparent 70%);
  pointer-events: none;
}

.hero__plasma {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
  mask-image: linear-gradient(180deg, #000 78%, transparent 100%);
  -webkit-mask-image: linear-gradient(180deg, #000 78%, transparent 100%);
}

.hero__grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 85% 60% at 50% 30%, #000 30%, transparent 78%);
  -webkit-mask-image: radial-gradient(ellipse 85% 60% at 50% 30%, #000 30%, transparent 78%);
  animation: hero-grid 7s linear infinite;
}

@keyframes hero-grid {
  from { background-position: 0 0; }
  to { background-position: 0 56px; }
}

.hero__inner {
  position: relative;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 56px;
}

.hero__copy {
  flex: 1 1 440px;
  min-width: 380px;
}

.hero__title {
  font-size: clamp(36px, 5.2vw, 68px);
  line-height: 1.06;
  letter-spacing: -0.02em;
  margin-top: 22px;
}

.hero__lede {
  font-size: 16.5px;
  line-height: 1.65;
  color: var(--color-k-body);
  max-width: 480px;
  margin: 26px 0 0;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 36px;
}

.hero__visual {
  flex: 1 1 420px;
  max-width: 560px;
  min-width: 380px;
  position: relative;
  height: 440px;
  margin: 0 auto;
}

.hero__pipes {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.hero__label {
  position: absolute;
  transform: translate(-100%, -50%);
  font-family: var(--font-k-mono);
  font-size: 11px;
  color: var(--color-k-dim);
  background: var(--color-k-bg-chip);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 5px 10px;
  border-radius: 6px;
}

.hero__core {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -52%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.hero__coretile {
  width: 120px;
  height: 120px;
  border-radius: 28px;
  background: linear-gradient(165deg, rgba(236, 31, 82, 0.16), rgba(16, 16, 18, 0.9) 70%);
  border: 1px solid rgba(236, 31, 82, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 60px rgba(236, 31, 82, 0.25);
  animation: hero-float 7s ease-in-out infinite;
}

@keyframes hero-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-12px); }
}

.hero__corelabel {
  font-family: var(--font-k-mono);
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: var(--color-k-faint);
}

.hero__chip {
  position: absolute;
  left: 78%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 7px;
  background: rgba(40, 254, 180, 0.08);
  border: 1px solid rgba(40, 254, 180, 0.3);
  padding: 6px 11px;
  border-radius: 7px;
  font-family: var(--font-k-mono);
  font-size: 11px;
  color: var(--color-k-mint);
}

@media (max-width: 768px) {
  .hero {
    padding: 148px 0 84px;
  }

  .hero__copy,
  .hero__visual {
    min-width: 0;
  }
}

@media (max-width: 600px) {
  .hero__visual {
    height: 340px;
    flex-basis: 300px;
  }

  .hero__label {
    transform: translate(0, -50%);
  }

  .hero__chip {
    left: auto;
    right: 0;
  }
}
</style>
