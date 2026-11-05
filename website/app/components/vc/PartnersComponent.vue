<template>
  <section id="partners" class="partners" aria-labelledby="partners-title">
    <div class="k-wrap partners__grid">
      <div>
        <div class="k-eyebrow partners__eyebrow" data-reveal>
          <span>BUILDING TOGETHER</span>
        </div>
        <h2 id="partners-title" class="k-heading partners__title" data-reveal>The partners we’re looking for</h2>
        <div class="partners__copy" data-reveal>
          <p>
            Between us, we’ve built successful software services businesses. Kinotic is our first
            software product business, and we recognize that building a great platform and building a
            repeatable path to market require different experience.
          </p>
          <p>
            We’re looking for investors who want to be actively involved: challenging our assumptions,
            helping us sharpen our business strategy, and mentoring us as we find our first customers
            and build market traction. Our commercial plan is still taking shape, and we want partners
            who bring experience, judgment, and relationships to help us develop and test it.
          </p>
          <p>
            We bring deep technical and design experience, a history of working together, and the
            commitment to execute. We’re seeking investors whose involvement goes beyond capital.
          </p>
        </div>
      </div>

      <div class="diagram" data-reveal>
        <div class="diagram__grid" />
        <div class="diagram__glow" />
        <svg
          viewBox="0 0 480 480"
          fill="none"
          role="img"
          aria-label="Our experience and your guidance join into a shared path toward market traction"
        >
          <defs>
            <linearGradient id="partners-out" x1="0" y1="262" x2="0" y2="396" gradientUnits="userSpaceOnUse">
              <stop offset="0" stop-color="#28FEB4" stop-opacity=".55" />
              <stop offset="1" stop-color="#28FEB4" />
            </linearGradient>
          </defs>
          <g font-family="Fira Code, monospace" text-anchor="middle">
            <text x="104" y="62" font-size="12.5" letter-spacing="2.2" fill="#28FEB4">OUR EXPERIENCE</text>
            <text x="104" y="86" font-size="11" letter-spacing=".6" fill="#7C7C85">Building &amp; delivering</text>
            <text x="376" y="62" font-size="12.5" letter-spacing="2.2" fill="#FF3D6E">YOUR GUIDANCE</text>
            <text x="376" y="86" font-size="11" letter-spacing=".6" fill="#7C7C85">Strategy &amp; mentorship</text>
          </g>
          <path d="M104 116 V150 C104 214 240 192 240 250" stroke="#28FEB4" stroke-opacity=".18" stroke-width="8" stroke-linecap="round" />
          <path d="M376 116 V150 C376 214 240 192 240 250" stroke="#EC1F52" stroke-opacity=".18" stroke-width="8" stroke-linecap="round" />
          <path d="M104 116 V150 C104 214 240 192 240 250" stroke="#28FEB4" stroke-width="2" stroke-linecap="round" />
          <path d="M376 116 V150 C376 214 240 192 240 250" stroke="#EC1F52" stroke-width="2" stroke-linecap="round" />
          <path class="flow" d="M104 116 V150 C104 214 240 192 240 250" stroke="#E6FFF5" stroke-width="2" stroke-linecap="round" />
          <path class="flow" d="M376 116 V150 C376 214 240 192 240 250" stroke="#FFE3EA" stroke-width="2" stroke-linecap="round" />
          <circle cx="104" cy="116" r="4.5" fill="#28FEB4" />
          <circle cx="376" cy="116" r="4.5" fill="#EC1F52" />
          <path d="M240 280 V390" stroke="#28FEB4" stroke-opacity=".16" stroke-width="10" stroke-linecap="round" />
          <path d="M240 280 V392" stroke="url(#partners-out)" stroke-width="2.5" stroke-linecap="round" />
          <path class="flow flow--out" d="M240 280 V392" stroke="#E6FFF5" stroke-width="2.5" stroke-linecap="round" />
          <path d="M231 384 L240 394 L249 384" stroke="#28FEB4" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
          <circle cx="240" cy="265" r="15" fill="#08090A" stroke="#28FEB4" stroke-width="1.6" />
          <circle cx="240" cy="265" r="24" stroke="#28FEB4" stroke-opacity=".18" stroke-width="1" />
          <circle cx="240" cy="265" r="5" fill="#28FEB4" />
          <line x1="268" y1="265" x2="296" y2="265" stroke="#3A3A42" stroke-width="1" />
          <text x="306" y="269" font-family="Fira Code, monospace" font-size="11.5" letter-spacing="2.2" fill="#C9C9CE">TOGETHER</text>
          <text x="240" y="436" text-anchor="middle" font-family="Fira Code, monospace" font-size="14" letter-spacing="2.6" fill="#28FEB4">MARKET TRACTION</text>
        </svg>
      </div>
    </div>
  </section>
</template>

<style scoped>
.partners {
  padding: 110px 0 120px;
  border-top: 1px solid var(--color-k-border);
  scroll-margin-top: 76px;
}

.partners__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 64px;
  align-items: end;
}

.partners__eyebrow {
  margin-bottom: 22px;
}

.partners__title {
  max-width: 560px;
  margin-bottom: 48px;
  font-size: clamp(36px, 4.6vw, 62px);
  font-weight: 500;
  line-height: 1.06;
  letter-spacing: -0.02em;
}

.partners__copy {
  display: flex;
  flex-direction: column;
  gap: 26px;
  max-width: 560px;
}

.partners__copy p {
  margin: 0;
  color: var(--color-k-muted);
  font-size: 16.5px;
  line-height: 1.75;
  text-wrap: pretty;
}

.diagram {
  position: relative;
  width: 100%;
  max-width: 520px;
  aspect-ratio: 1 / 1;
  justify-self: end;
}

.diagram__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 40px 40px;
  background-position: -1px -1px;
  mask-image: radial-gradient(ellipse 60% 60% at 50% 55%, #000 30%, transparent 78%);
}

.diagram__glow {
  position: absolute;
  left: 20%;
  top: 30%;
  width: 60%;
  height: 55%;
  background: radial-gradient(ellipse 50% 50% at 50% 50%, rgba(40, 254, 180, 0.1), transparent 70%);
  filter: blur(10px);
}

.diagram svg {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: visible;
}

/* A single bright dash per 64-unit period, travelling along each path. */
.flow {
  stroke-dasharray: 1 63;
  stroke-opacity: 0.9;
  animation: partners-flow 2.4s linear infinite;
}

.flow--out {
  animation-delay: -0.8s;
}

@keyframes partners-flow {
  to {
    stroke-dashoffset: -64;
  }
}

@media (max-width: 930px) {
  .partners__grid {
    grid-template-columns: 1fr;
    gap: 48px;
  }

  .diagram {
    max-width: 440px;
    justify-self: center;
  }
}

@media (max-width: 768px) {
  .partners {
    padding: 64px 0;
  }
}
</style>
