<template>
  <section class="k-section enterprise">
    <div class="k-wrap">
      <h2 class="k-heading enterprise__title" data-reveal>
        Everything enterprise needs baked in from day one
      </h2>

      <div class="enterprise__row" data-reveal>
        <div class="enterprise__copy enterprise__copy--tall enterprise__divider">
          <div class="enterprise__lead">Enterprise guardrails, built in</div>
          <p>
            Fine-grained authorization, SBOMs, observability, service communication and deployment
            are included — not bolted on.
          </p>
        </div>

        <div class="enterprise__visual">
          <div class="enterprise__glow enterprise__glow--right" />
          <svg class="enterprise__cubes" viewBox="0 0 360 260" fill="none" aria-hidden="true">
            <path d="M 62 200 L 118 172 L 174 200 L 118 228 Z" stroke="#3A3A40" stroke-width="2.2" opacity=".55" />
            <path d="M 208 214 L 258 190 L 308 214 L 258 238 Z" stroke="#3A3A40" stroke-width="2.2" opacity=".55" />
            <line class="dashline" x1="130" y1="152" x2="130" y2="208" stroke="rgba(236,31,82,.5)" stroke-width="2.2" />
            <line class="dashline" x1="180" y1="170" x2="180" y2="226" stroke="rgba(236,31,82,.5)" stroke-width="2.2" />
            <line class="dashline" x1="230" y1="152" x2="230" y2="208" stroke="rgba(236,31,82,.5)" stroke-width="2.2" />
            <g class="cube">
              <path d="M 180 40 L 250 75 L 180 110 L 110 75 Z" fill="rgba(236,31,82,.07)" stroke="#EC1F52" stroke-width="2.6" />
              <path d="M 110 75 L 110 128 L 180 163 L 180 110 Z" fill="rgba(236,31,82,.05)" stroke="#EC1F52" stroke-width="2.6" />
              <path d="M 250 75 L 250 128 L 180 163 L 180 110 Z" fill="rgba(236,31,82,.1)" stroke="#EC1F52" stroke-width="2.6" />
            </g>
          </svg>
        </div>
      </div>

      <div class="enterprise__row enterprise__row--stacked" data-reveal>
        <div class="enterprise__visual enterprise__visual--tall enterprise__divider">
          <div class="enterprise__glow enterprise__glow--left" />
          <div class="iam">
            <div class="iam__actors">
              <span class="iam__pill">HUMAN</span>
              <span class="iam__pill">AGENT</span>
            </div>
            <svg class="iam__wires" viewBox="0 0 290 30" fill="none" aria-hidden="true">
              <path d="M 73 2 C 73 18 145 18 145 18 M 217 2 C 217 18 145 18 145 18 M 145 18 L 145 30" stroke="rgba(40,254,180,.4)" stroke-width="1.5" />
            </svg>
            <span class="iam__gate">
              <KinoticIcon name="Key" :size="13" />
              OIDC · RBAC
              <span class="k-caret iam__caret" />
            </span>
            <svg class="iam__wires" viewBox="0 0 290 30" fill="none" aria-hidden="true">
              <path d="M 145 0 L 145 12 M 145 12 C 145 12 25 12 25 28 M 145 12 C 145 12 265 12 265 28 M 145 12 L 145 28" stroke="rgba(40,254,180,.4)" stroke-width="1.5" />
            </svg>
            <div class="iam__scopes">
              <span class="iam__pill">READ ✓</span>
              <span class="iam__pill">WRITE ✓</span>
              <span class="iam__pill iam__pill--denied">ADMIN ✕</span>
              <span class="iam__pill iam__pill--faded">DATA</span>
              <span class="iam__pill iam__pill--faded">API</span>
              <span class="iam__pill iam__pill--faded">PII</span>
            </div>
          </div>
        </div>

        <div class="enterprise__copy">
          <div class="enterprise__lead">Identity &amp; Access Management</div>
          <p>
            The CLI and declarative model give agents a constrained, inspectable way to create
            production systems — not arbitrary infrastructure.
          </p>
        </div>
      </div>

      <div class="enterprise__row" data-reveal>
        <div class="enterprise__copy enterprise__copy--tall enterprise__divider">
          <div class="enterprise__lead">Prototype-to-production continuity</div>
          <p>
            The same application survives from scaffold to internet scale. No rewrite after the AI
            prototype.
          </p>
        </div>

        <div class="enterprise__visual enterprise__visual--stack">
          <div class="enterprise__glow enterprise__glow--top" />
          <!-- One app crate slides from the dashed prototype box into the live production box,
               recolouring mint as it lands. -->
          <svg class="enterprise__p2p" viewBox="60 20 320 160" fill="none" aria-hidden="true">
            <rect x="76" y="46" width="128" height="98" rx="10" stroke="rgba(255,255,255,.22)" stroke-width="1.5" stroke-dasharray="6 5" />
            <text x="140" y="170" font-family="Fira Code, monospace" font-size="11" letter-spacing="1.5" fill="#8A8A92" text-anchor="middle">PROTOTYPE</text>
            <rect x="236" y="34" width="128" height="122" rx="10" fill="rgba(40,254,180,.05)" stroke="rgba(40,254,180,.7)" stroke-width="1.5" />
            <circle cx="252" cy="50" r="3.5" fill="#28FEB4">
              <animate attributeName="opacity" values="1;.3;1" dur="1.8s" repeatCount="indefinite" />
            </circle>
            <text x="300" y="170" font-family="Fira Code, monospace" font-size="11" letter-spacing="1.5" fill="#28FEB4" text-anchor="middle">PRODUCTION</text>
            <path id="p2ppath" d="M 140 95 C 200 95 240 95 300 95" stroke="none" />
            <line x1="176" y1="82" x2="212" y2="82" stroke="rgba(40,254,180,.35)" stroke-width="1.6" stroke-linecap="round">
              <animate attributeName="opacity" values="0;.9;0" dur="1.6s" repeatCount="indefinite" />
            </line>
            <line x1="184" y1="95" x2="228" y2="95" stroke="rgba(40,254,180,.5)" stroke-width="1.6" stroke-linecap="round">
              <animate attributeName="opacity" values="0;.9;0" dur="1.6s" begin="-.5s" repeatCount="indefinite" />
            </line>
            <line x1="176" y1="108" x2="208" y2="108" stroke="rgba(40,254,180,.35)" stroke-width="1.6" stroke-linecap="round">
              <animate attributeName="opacity" values="0;.9;0" dur="1.6s" begin="-1s" repeatCount="indefinite" />
            </line>
            <g>
              <g transform="translate(-32,-24)">
                <rect width="64" height="48" rx="6" fill="#101113" stroke="#5D5D66" stroke-width="1.6">
                  <animate attributeName="stroke" values="#5D5D66;#5D5D66;#28FEB4;#28FEB4" keyTimes="0;.38;.46;1" dur="8s" repeatCount="indefinite" />
                  <animate attributeName="fill" values="#101113;#101113;#0E1511;#0E1511" keyTimes="0;.38;.46;1" dur="8s" repeatCount="indefinite" />
                </rect>
                <rect x="8" y="9" width="10" height="30" rx="2.5" fill="#3A3A40">
                  <animate attributeName="fill" values="#3A3A40;#3A3A40;#1E5C46;#1E5C46" keyTimes="0;.38;.46;1" dur="8s" repeatCount="indefinite" />
                </rect>
                <rect x="24" y="10" width="26" height="5" rx="2.5" fill="#55555C">
                  <animate attributeName="fill" values="#55555C;#55555C;#28FEB4;#28FEB4" keyTimes="0;.38;.46;1" dur="8s" repeatCount="indefinite" />
                </rect>
                <rect x="24" y="21" width="30" height="5" rx="2.5" fill="#494950">
                  <animate attributeName="fill" values="#494950;#494950;#6B6B73;#6B6B73" keyTimes="0;.38;.46;1" dur="8s" repeatCount="indefinite" />
                </rect>
                <rect x="24" y="32" width="20" height="6" rx="3" fill="#55555C">
                  <animate attributeName="fill" values="#55555C;#55555C;#EC1F52;#EC1F52" keyTimes="0;.38;.46;1" dur="8s" repeatCount="indefinite" />
                </rect>
              </g>
              <animate attributeName="opacity" values="0;1;1;0" keyTimes="0;.08;.93;1" dur="8s" repeatCount="indefinite" />
              <animateMotion dur="8s" repeatCount="indefinite" keyPoints="0;0;1;1" keyTimes="0;.14;.38;1" calcMode="linear">
                <mpath href="#p2ppath" />
              </animateMotion>
            </g>
          </svg>
          <div class="enterprise__timing">hours <span>not months</span></div>
          <span class="enterprise__mcp">
            <span class="enterprise__mcp-tag">MCP</span>· build with Claude, ChatGPT, Cursor…
            <span class="k-caret" />
          </span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.enterprise {
  --k-section-padding: 60px 0 110px;
}

.enterprise__title {
  font-size: clamp(29px, 3.4vw, 44px);
  line-height: 1.15;
  margin-bottom: 44px;
  max-width: 620px;
}

.enterprise__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border: 1px solid var(--k-border);
}

.enterprise__row + .enterprise__row {
  border-top: none;
}

.enterprise__divider {
  border-right: 1px solid var(--k-border);
}

.enterprise__copy {
  padding: 64px 56px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.enterprise__copy--tall {
  min-height: 400px;
}

.enterprise__lead {
  font-family: var(--k-font-mono);
  font-size: 21px;
  color: var(--k-text);
  margin-bottom: 14px;
}

.enterprise__copy p {
  font-size: 14px;
  line-height: 1.65;
  color: var(--k-muted);
  margin: 0;
  max-width: 420px;
}

.enterprise__visual {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--k-bg-panel);
  overflow: hidden;
}

.enterprise__visual--tall {
  min-height: 400px;
}

.enterprise__visual--stack {
  flex-direction: column;
  gap: 22px;
  padding: 44px 0;
}

.enterprise__glow {
  position: absolute;
  background: radial-gradient(ellipse, rgba(40, 254, 180, 0.07), transparent 70%);
}

.enterprise__glow--right {
  top: -40px;
  right: -40px;
  width: 260px;
  height: 200px;
}

.enterprise__glow--left {
  top: -40px;
  left: -40px;
  width: 260px;
  height: 200px;
}

.enterprise__glow--top {
  top: -40px;
  left: 30%;
  width: 280px;
  height: 200px;
  background: radial-gradient(ellipse, rgba(40, 254, 180, 0.08), transparent 70%);
}

.enterprise__cubes {
  width: 360px;
  max-width: 88%;
  overflow: visible;
}

.dashline {
  stroke-dasharray: 4 3;
  animation: enterprise-dash 1.6s linear infinite;
}

@keyframes enterprise-dash {
  to { stroke-dashoffset: -28; }
}

.cube {
  animation: enterprise-cube 6s ease-in-out infinite;
}

@keyframes enterprise-cube {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-9px); }
}

/* ── Identity & access illustration ── */
.iam {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.iam__actors {
  display: flex;
  gap: 56px;
}

.iam__wires {
  width: 290px;
  height: 30px;
}

.iam__pill {
  font-family: var(--k-font-mono);
  font-size: 10.5px;
  letter-spacing: 0.06em;
  color: var(--k-mint);
  border: 1px solid rgba(40, 254, 180, 0.7);
  border-radius: 6px;
  padding: 7px 0;
  width: 88px;
  text-align: center;
}

.iam__pill--denied {
  color: rgba(236, 31, 82, 0.85);
  border-color: rgba(236, 31, 82, 0.5);
}

.iam__pill--faded {
  color: rgba(40, 254, 180, 0.55);
  border-color: rgba(40, 254, 180, 0.3);
}

.iam__gate {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-family: var(--k-font-mono);
  font-size: 11.5px;
  letter-spacing: 0.06em;
  color: var(--k-bg);
  background: var(--k-mint);
  border-radius: 7px;
  padding: 8px 18px;
}

.iam__caret {
  background: var(--k-bg);
  height: 11px;
}

.iam__scopes {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px 14px;
}

/* ── Prototype to production illustration ── */
.enterprise__p2p {
  width: 380px;
  max-width: 88%;
}

.enterprise__timing {
  font-family: var(--k-font-mono);
  font-size: 20px;
  color: var(--k-text);
}

.enterprise__timing span {
  font-size: 11.5px;
  color: var(--k-muted);
}

.enterprise__mcp {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--k-font-mono);
  font-size: 11.5px;
  color: var(--k-dim);
  background: var(--k-bg-chip);
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 8px;
  padding: 9px 14px;
}

.enterprise__mcp-tag {
  color: var(--k-mint);
}

@media (max-width: 930px) {
  .enterprise__row {
    grid-template-columns: 1fr;
  }

  .enterprise__divider {
    border-right: none;
  }

  /* Copy always leads on a single column, even where the desktop row puts the
     illustration first. */
  .enterprise__row--stacked .enterprise__copy {
    order: -1;
  }

  .enterprise__copy {
    padding: 46px 30px;
    border-bottom: 1px solid var(--k-border);
  }

  .enterprise__copy--tall {
    min-height: 0;
  }

  .enterprise__visual {
    min-height: 330px;
  }
}

@media (max-width: 600px) {
  .enterprise__copy {
    padding: 38px 22px;
  }

  .enterprise__visual {
    min-height: 300px;
  }
}

@media (max-width: 380px) {
  .iam {
    transform: scale(0.84);
  }
}
</style>
