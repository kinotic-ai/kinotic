<script setup lang="ts">
/** One coloured run of text inside a code pane line. */
interface Token {
  /**
   * comment · keyword · plain · type · annotation · generated · dim · bright · success
   */
  kind: 'c' | 'k' | 'p' | 't' | 'a' | 'g' | 'd' | 'b' | 's'
  text: string
}

interface Capability {
  id: string
  title: string
  summary: string
  /** Inline SVG body for the 20x20 glyph, authored below — never user input. */
  glyph: string
  file: string
  code: Token[][]
}

const t = (kind: Token['kind'], text: string): Token => ({ kind, text })

const capabilities: Capability[] = [
  {
    id: 'frontends',
    title: 'Frontends',
    summary: 'Complete UIs deployed as static sites, wired to your services with auth included.',
    glyph: '<rect x="2.5" y="3.5" width="15" height="13" rx="1.5" /><path d="M2.5 7.5H17.5M6.5 7.5V16.5" />',
    file: 'orders/ui.k',
    code: [
      [t('c', '// a complete frontend')],
      [t('k', 'frontend'), t('p', ' OrdersConsole {')],
      [t('p', '  source: '), t('t', './console')],
      [t('p', '  auth: '), t('t', 'OIDC')],
      [t('p', '}')],
      [t('c', '// generated for you:')],
      [t('g', '→ static site, edge-served')],
      [t('g', '→ session wiring to your services')],
      [t('g', '→ per-branch preview URLs')],
      [t('d', '$ '), t('b', 'kinotic deploy')],
      [t('s', '✓ '), t('b', 'orders-console '), t('s', 'live')],
    ],
  },
  {
    id: 'microservices',
    title: 'Microservices',
    summary: 'Long-running services with a generated proxy for every published call.',
    glyph: '<circle cx="10" cy="4.5" r="2" /><circle cx="4.5" cy="14.5" r="2" /><circle cx="15.5" cy="14.5" r="2" /><path d="M8.9 6.2L5.6 12.8M11.1 6.2L14.4 12.8M6.5 14.5H13.5" />',
    file: 'orders/service.k',
    code: [
      [t('c', '// long-running service')],
      [t('k', 'service'), t('p', ' PricingEngine {')],
      [t('p', '  publish quote(order: '), t('t', 'Order'), t('p', '): '), t('t', 'Quote')],
      [t('p', '  publish reprice(): '), t('t', 'Batch')],
      [t('p', '}')],
      [t('c', '// generated for you:')],
      [t('g', '→ typed client proxy, every language')],
      [t('g', '→ service mesh wiring & retries')],
      [t('g', '→ traces on every published call')],
      [t('d', '$ '), t('b', 'kinotic deploy')],
      [t('s', '✓ '), t('b', 'pricing-engine '), t('d', 'v2.1.0 '), t('s', 'live')],
    ],
  },
  {
    id: 'mcp-tools',
    title: 'MCP Tools',
    summary: 'Every service doubles as an MCP tool — build against live data with Claude, ChatGPT or Cursor.',
    glyph: '<path d="M10 2.5V6M10 14V17.5M2.5 10H6M14 10H17.5" /><circle cx="10" cy="10" r="3.2" />',
    file: 'orders/mcp.k',
    code: [
      [t('c', '// every service, an MCP tool')],
      [t('k', 'mcp'), t('p', ' OrdersTools {')],
      [t('p', '  expose: '), t('t', 'PricingEngine')],
      [t('p', '  auth: '), t('t', 'OIDC')],
      [t('p', '}')],
      [t('c', '// generated for you:')],
      [t('g', '→ MCP endpoints, zero glue code')],
      [t('g', '→ works with Claude, ChatGPT, Cursor')],
      [t('g', '→ scoped, policy-checked tool calls')],
      [t('d', '$ '), t('b', 'kinotic deploy')],
      [t('s', '✓ '), t('b', 'orders-tools '), t('s', 'live')],
    ],
  },
  {
    id: 'persistence',
    title: 'Persistence',
    summary: 'Domain models with generated CRUD, named queries and PII annotations on any field.',
    glyph: '<ellipse cx="10" cy="4.5" rx="6" ry="2.3" /><path d="M4 4.5V15.5C4 16.8 6.7 17.8 10 17.8C13.3 17.8 16 16.8 16 15.5V4.5" /><path d="M4 10C4 11.3 6.7 12.3 10 12.3C13.3 12.3 16 11.3 16 10" />',
    file: 'orders/model.k',
    code: [
      [t('c', '// define the domain model')],
      [t('k', 'entity'), t('p', ' Order {')],
      [t('p', '  id: '), t('t', 'ID!')],
      [t('p', '  customer: '), t('t', 'String!'), t('a', ' @pii')],
      [t('p', '  total: '), t('t', 'Decimal')],
      [t('p', '  status: '), t('t', 'OrderStatus'), t('p', ' = '), t('t', 'PENDING')],
      [t('p', '}')],
      [t('c', '// generated for you:')],
      [t('g', '→ CRUD services & named queries')],
      [t('g', '→ search, pagination, real-time')],
      [t('g', '→ GraphQL · OpenAPI · MCP endpoints')],
      [t('d', '$ '), t('b', 'kinotic deploy')],
      [t('s', '✓ '), t('b', 'orders-core '), t('d', 'v1.4.0 '), t('s', 'live')],
    ],
  },
]

const activeIndex = ref(0)
const active = computed(() => capabilities[activeIndex.value]!)
const tabs = useTemplateRef<HTMLButtonElement[]>('tabs')

/** Roving focus across the tab list, per the WAI-ARIA tabs pattern. */
function onTabKeydown(event: KeyboardEvent) {
  const last = capabilities.length - 1
  let next: number
  if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = activeIndex.value === last ? 0 : activeIndex.value + 1
  else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') next = activeIndex.value === 0 ? last : activeIndex.value - 1
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = last
  else return

  event.preventDefault()
  activeIndex.value = next
  tabs.value?.[next]?.focus()
}
</script>

<template>
  <section class="k-section api">
    <div class="api__glow" />
    <div class="k-wrap api__inner">
      <div class="k-eyebrow api__eyebrow" data-reveal>
        <span>STUPID SIMPLE API</span>
      </div>
      <h2 class="k-heading api__title" data-reveal>Unparalleled time to delivery</h2>

      <div class="api__panel" data-reveal>
        <div class="api__list" role="tablist" aria-label="Kinotic capabilities" @keydown="onTabKeydown">
          <button
            v-for="(capability, index) in capabilities"
            :key="capability.id"
            ref="tabs"
            type="button"
            role="tab"
            class="api__tab"
            :class="{ 'api__tab--active': index === activeIndex }"
            :id="`api-tab-${capability.id}`"
            :aria-selected="index === activeIndex"
            :aria-controls="`api-panel-${capability.id}`"
            :tabindex="index === activeIndex ? 0 : -1"
            @click="activeIndex = index"
          >
            <span class="api__tabicon">
              <svg
                width="20"
                height="20"
                viewBox="0 0 20 20"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
                v-html="capability.glyph"
              />
            </span>
            <span>
              <span class="api__tabtitle">{{ capability.title }}</span>
              <span class="api__tabsummary">{{ capability.summary }}</span>
            </span>
          </button>
        </div>

        <div
          class="api__code"
          role="tabpanel"
          :id="`api-panel-${active.id}`"
          :aria-labelledby="`api-tab-${active.id}`"
          tabindex="0"
        >
          <div class="api__codebar">
            <span class="api__dots">
              <span /><span /><span />
            </span>
            <span class="api__file">{{ active.file }}</span>
          </div>
          <pre class="api__pre"><div v-for="(line, index) in active.code" :key="index"><span
            v-for="(token, tokenIndex) in line"
            :key="tokenIndex"
            :class="`tok tok--${token.kind}`"
          >{{ token.text }}</span></div></pre>
        </div>
      </div>

      <div class="api__cta" data-reveal>
        <a href="#cta" class="k-btn k-btn--red">Get Started ↗</a>
      </div>
    </div>
  </section>
</template>

<style scoped>
.api {
  position: relative;
  overflow: hidden;
}

.api__glow {
  position: absolute;
  top: 0;
  right: -120px;
  width: 520px;
  height: 380px;
  background: radial-gradient(ellipse, rgba(236, 31, 82, 0.12), transparent 70%);
  filter: blur(10px);
  pointer-events: none;
}

.api__inner {
  position: relative;
}

.api__eyebrow {
  justify-content: center;
  margin-bottom: 18px;
}

.api__title {
  font-size: clamp(29px, 3.6vw, 46px);
  text-align: center;
  margin-bottom: 64px;
}

.api__panel {
  display: grid;
  grid-template-columns: 5fr 7fr;
  border: 1px solid var(--color-k-border);
  background: var(--color-k-bg);
}

.api__list {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-k-border);
}

.api__tab {
  flex: 1;
  display: flex;
  gap: 18px;
  padding: 26px 30px;
  text-align: left;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--color-k-border);
  cursor: pointer;
  transition: background 0.2s ease;
}

.api__tab:last-child {
  border-bottom: none;
}

.api__tab:hover {
  background: rgba(255, 255, 255, 0.025);
}

.api__tab--active {
  background: rgba(40, 254, 180, 0.04);
}

.api__tabicon {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: var(--color-k-dim);
  background: var(--color-k-bg-panel);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s ease, color 0.2s ease;
}

.api__tab--active .api__tabicon {
  border-color: rgba(40, 254, 180, 0.55);
  color: var(--color-k-mint);
}

.api__tabtitle {
  display: block;
  font-family: var(--font-k-mono);
  font-size: 16.5px;
  color: var(--color-k-text);
  margin-bottom: 7px;
}

.api__tabsummary {
  display: block;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--color-k-muted);
}

.api__code {
  background: #0A0A0C;
  padding: 26px 34px;
  position: relative;
  min-height: 420px;
}

.api__codebar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 26px;
}

.api__dots {
  display: inline-flex;
  gap: 7px;
}

.api__dots span {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #D6D6DB;
}

.api__dots span:nth-child(2) {
  background: var(--color-k-fainter);
}

.api__dots span:nth-child(3) {
  background: #3A3A40;
}

.api__file {
  font-family: var(--font-k-mono);
  font-size: 11.5px;
  color: var(--color-k-fainter);
}

.api__pre {
  margin: 0;
  font-family: var(--font-k-mono);
  font-size: 13px;
  line-height: 2.05;
  color: var(--color-k-body);
  white-space: pre-wrap;
}

.tok--c { color: var(--color-k-fainter); }
.tok--k { color: var(--color-k-mint); }
.tok--p { color: var(--color-k-body); }
.tok--t { color: #EC5C7F; }
.tok--a { color: var(--color-k-mint); }
.tok--g { color: var(--color-k-dim); }
.tok--d { color: var(--color-k-fainter); }
.tok--b { color: var(--color-k-text); }
.tok--s { color: var(--color-k-mint); }

.api__cta {
  display: flex;
  justify-content: center;
  margin-top: 56px;
}

@media (max-width: 930px) {
  .api__panel {
    grid-template-columns: 1fr;
  }

  .api__list {
    border-right: none;
    border-bottom: 1px solid var(--color-k-border);
  }
}

@media (max-width: 600px) {
  .api__tab {
    padding: 22px 18px;
  }

  .api__code {
    padding: 20px 16px;
    min-height: 0;
  }

  .api__pre {
    font-size: 12px;
    line-height: 1.95;
  }
}
</style>
