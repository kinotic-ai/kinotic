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
              <path
                d="M 10 96 L 50 96 L 78 58 L 100 78 L 120 32 L 146 66 L 172 48 L 200 74 L 226 60 L 250 68 L 278 42"
                stroke="#28FEB4"
                stroke-width="2.6"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <circle cx="120" cy="32" r="4.5" fill="#EC1F52" />
              <circle cx="278" cy="42" r="4.5" fill="#28FEB4" />
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
            <div class="envstack">
              <div class="envstack__shadow envstack__shadow--back" />
              <div class="envstack__shadow envstack__shadow--mid" />
              <div class="envstack__front">
                <span class="envstack__icon"><KinoticIcon name="Folder" :size="16" /></span>
                <span class="envstack__meta">
                  <span class="envstack__branch">feature/checkout</span>
                  <span class="envstack__tags">POD · DEV · ENV</span>
                </span>
                <span class="k-livedot envstack__dot" />
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
  height: 110px;
}

.envstack__shadow {
  position: absolute;
  border-radius: 10px;
  background: var(--color-k-bg-panel);
}

.envstack__shadow--back {
  left: 18px;
  top: 0;
  width: 224px;
  height: 20px;
  border: 1px solid rgba(40, 254, 180, 0.25);
}

.envstack__shadow--mid {
  left: 9px;
  top: 11px;
  width: 242px;
  height: 22px;
  border: 1px solid rgba(40, 254, 180, 0.4);
  border-radius: 11px;
}

.envstack__front {
  position: absolute;
  left: 0;
  top: 24px;
  width: 260px;
  height: 72px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 20px;
  border: 1px solid rgba(40, 254, 180, 0.8);
  border-radius: 13px;
  background: #0E1511;
  box-shadow: 0 0 34px rgba(40, 254, 180, 0.18);
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
