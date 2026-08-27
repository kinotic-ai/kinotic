<script setup lang="ts">
/** One coloured run of text inside a code sample. */
interface Token {
  /**
   * comment · keyword · plain · type · annotation · dim · bright · success
   */
  kind: 'c' | 'k' | 'p' | 't' | 'a' | 'd' | 'b' | 's'
  text: string
}

/**
 * One exchange with the assistant: what you asked for in your own words, what
 * came back, and — where it makes the answer concrete — the few lines that were
 * written for you and what the app gained.
 */
interface Slide {
  ask: string[]
  reply: string[]
  code?: Token[][]
  got?: string[]
}

/** One beat of the build, shown as a rail entry and its exchanges. */
interface Step {
  id: string
  number: string
  title: string
  summary: string
  icon: 'Prompt' | 'Persistence' | 'Microservices' | 'Frontends' | 'McpTools'
  slides: Slide[]
}

const t = (kind: Token['kind'], text: string): Token => ({ kind, text })

const steps: Step[] = [
  {
    id: 'describe',
    number: '01',
    title: 'Describe it',
    summary: 'Say what you need. Your application, project and repository are created for you.',
    icon: 'Prompt',
    slides: [
      {
        ask: ['We need a todo app for our field techs.'],
        reply: [
          'Created it. Works the same in Claude, ChatGPT or Grok — the plugin talks to Kinotic OS over MCP.',
        ],
        got: ['Application  todo-app', 'Project  field-ops', 'a GitHub repo, provisioned', 'a Bun workspace, ready'],
      },
      {
        ask: ['Anything I need to install?'],
        reply: [
          'No. Clone it and run install — the CLI is already in the workspace.',
        ],
        code: [
          [t('d', '$ '), t('b', 'git clone'), t('p', ' … && '), t('b', 'bun install')],
        ],
        got: ['no global installs', 'no build to configure', 'no CI to wire up', 'a preview env per branch'],
      },
    ],
  },
  {
    id: 'model',
    number: '02',
    title: 'Model the domain',
    summary: 'Describe your data once. Storage, search and a typed repository come with it.',
    icon: 'Persistence',
    slides: [
      {
        ask: [
          'Field techs need todos — title, priority, done.',
          "Keep each customer's separate.",
        ],
        reply: ['Done. Stored, searchable, and isolated per tenant.'],
        code: [
          [t('a', '@Entity'), t('p', '('), t('t', 'MultiTenancyType.SHARED'), t('p', ')')],
          [t('k', 'export class'), t('p', ' Todo { … }')],
        ],
        got: ['storage', 'full-text search', 'per-tenant isolation', 'a typed repository'],
      },
      {
        ask: ['I need a count of what is still open.'],
        reply: ['Added to your repository. You declare it; the CLI writes the body.'],
        code: [
          [t('p', '  '), t('a', '@Query'), t('p', '('), t('t', "'SELECT COUNT(*) … :done'"), t('p', ')')],
          [t('p', '  countByDone(done: '), t('t', 'boolean'), t('p', ')')],
        ],
        got: ['aggregate queries', 'parameters bound by name', 'fully typed', 'no SQL in your code'],
      },
      {
        ask: ['Show me the open pump jobs.'],
        reply: ['Every call is already scoped to the right customer — no filter to remember.'],
        code: [
          [t('k', 'await'), t('p', ' todos.search('), t('t', "'pump'"), t('p', ')')],
          [t('k', 'await'), t('p', ' todos.countByDone('), t('t', 'false'), t('p', ')')],
        ],
        got: ['save · search · count', 'pagination built in', 'tenant filter automatic', 'nothing to configure'],
      },
    ],
  },
  {
    id: 'publish',
    number: '03',
    title: 'Publish the behavior',
    summary: 'Anything you publish is callable from anywhere in your app, fully typed.',
    icon: 'Microservices',
    slides: [
      {
        ask: ['Add a way to mark one done.'],
        reply: ['Published. Any class you publish becomes callable across your app.'],
        code: [
          [t('a', '@Publish'), t('p', '('), t('t', "'com.example'"), t('p', ')')],
          [t('k', 'export class'), t('p', ' TodoService {')],
          [t('p', '  '), t('k', 'async'), t('p', ' complete(id: '), t('t', 'string'), t('p', ') { … }')],
          [t('p', '}')],
        ],
        got: ['addressing and discovery', 'tracing across services', 'no transport to write', 'versioned'],
      },
      {
        ask: ['Let the console call it.'],
        reply: ['Calling it looks like calling a local object.'],
        code: [
          [t('k', 'const'), t('p', ' todos =')],
          [t('p', '  '), t('k', 'new'), t('p', ' TodoServiceProxy(Kinotic)')],
          [t('k', 'await'), t('p', ' todos.complete(id)')],
        ],
        got: ['typed end to end', 'auth carried for you', 'browser or Node', 'reconnects on its own'],
      },
      {
        ask: ['Push overdue ones to the console as they happen.'],
        reply: ['Return an Observable and it streams instead of answering once.'],
        code: [
          [t('p', 'watchOverdue(): '), t('t', 'Observable<Todo>')],
          [],
          [t('p', 'todos.watchOverdue()')],
          [t('p', '  .subscribe('), t('b', 'render'), t('p', ')')],
        ],
        got: ['live feeds and alerts', 'change data capture', 'the same proxy', 'unsubscribe when done'],
      },
    ],
  },
  {
    id: 'ship',
    number: '04',
    title: 'Ship the UI',
    summary: 'Any frontend framework, holding the same repositories your services hold.',
    icon: 'Frontends',
    slides: [
      {
        ask: ['Build a console — search, list, mark done.'],
        reply: [
          'React, Vue or plain TS. In a browser it connects with no credentials: the session is already there.',
        ],
        code: [
          [t('k', 'await'), t('p', ' Kinotic.connect()')],
          [t('k', 'const'), t('p', ' todos = '), t('k', 'new'), t('p', ' TodoRepository()')],
        ],
        got: ['the same repository', 'your OIDC session reused', 'no API client to write', 'no CORS to configure'],
      },
      {
        ask: ['Ship it.'],
        reply: ['Push. Kinotic OS syncs your entities and deploys the rest.'],
        code: [
          [t('d', '$ '), t('b', 'git push')],
          [t('s', '✓ '), t('b', 'Todo '), t('s', 'synced')],
          [t('s', '✓ '), t('b', 'todo-service '), t('s', 'live')],
          [t('s', '✓ '), t('b', 'todo-console '), t('s', 'deployed')],
        ],
        got: ['static hosting on a CDN', 'a preview env per branch', 'no pipeline to configure', 'nothing to provision'],
      },
    ],
  },
  {
    id: 'hand-back',
    number: '05',
    title: 'Hand it back',
    summary: 'Mark a method a tool, and the assistant that built the app can operate it.',
    icon: 'McpTools',
    slides: [
      {
        ask: ['Let my assistant use this too.'],
        reply: ['Marked as tools. The hints tell a host what is safe to run unattended.'],
        code: [
          [t('p', '  '), t('a', '@McpTool'), t('p', '({ title: '), t('t', "'Find Todos'"), t('p', ',')],
          [t('p', '             readOnlyHint: '), t('t', 'true'), t('p', ' })')],
          [t('p', '  search(q: '), t('t', 'string'), t('p', ') { … }')],
        ],
        got: ['served at POST /mcp', 'secured with OAuth 2.1', 'callers see only their tools', 'Claude, ChatGPT, Cursor'],
      },
      {
        ask: ['Which pump todos are still open?'],
        reply: [
          'Three. Two of them are assigned to Jane — want me to close those out?',
        ],
        code: [
          [t('d', 'TodoService.search'), t('p', '  ·  '), t('s', '3 results')],
        ],
        got: ['you wrote no endpoint', 'no schema', 'no auth check', 'and no client'],
      },
    ],
  },
]

/** How long each exchange holds before the walkthrough moves on, in milliseconds. */
const SLIDE_MS = 7000

const stepIndex = ref(0)
const slideIndex = ref(0)
const step = computed(() => steps[stepIndex.value]!)
const slide = computed(() => step.value.slides[slideIndex.value]!)
const tabs = useTemplateRef<HTMLButtonElement[]>('tabs')
const track = useTemplateRef<HTMLElement>('track')

const playing = ref(false)
const onscreen = ref(false)
const attended = ref(false)

// The walkthrough only advances while it is on screen and unattended, so a
// viewer reading one exchange is never pulled off it, and an off-screen section
// isn't burning frames.
const advancing = computed(() => playing.value && onscreen.value && !attended.value)

// Pointer or focus on the play control is an instruction to run, so it must not
// register as attention the way the rail and the transcript do. Hence mouseover
// rather than mouseenter: it reports the element actually under the pointer.
function onAttend(event: Event) {
  attended.value = !(event.target as HTMLElement).closest('.api__play')
}

let frame = 0
let previous = 0
let elapsed = 0

/** Writes the dot fill directly, to keep the per-frame progress out of Vue's reactivity. */
function paintProgress() {
  track.value?.style.setProperty('--k-progress', String(elapsed / SLIDE_MS))
}

/** Walks to the next exchange of this step, or to the first one of the next step. */
function advance() {
  if (slideIndex.value < step.value.slides.length - 1) {
    slideIndex.value += 1
  }
  else {
    stepIndex.value = (stepIndex.value + 1) % steps.length
    slideIndex.value = 0
  }
}

function tick(now: number) {
  if (previous) elapsed += now - previous
  previous = now

  if (elapsed >= SLIDE_MS) {
    elapsed = 0
    advance()
  }

  paintProgress()
  frame = requestAnimationFrame(tick)
}

/** Hands control to the viewer, per the ARIA carousel pattern: rotation stops for good. */
function take() {
  elapsed = 0
  playing.value = false
  paintProgress()
}

function selectStep(index: number) {
  stepIndex.value = index
  slideIndex.value = 0
  take()
}

function selectSlide(index: number) {
  slideIndex.value = index
  take()
}

watch(advancing, (on) => {
  if (on) {
    previous = 0
    frame = requestAnimationFrame(tick)
  }
  else {
    cancelAnimationFrame(frame)
    frame = 0
    paintProgress()
  }
})

/** Roving focus across the step list, per the WAI-ARIA tabs pattern. */
function onTabKeydown(event: KeyboardEvent) {
  const last = steps.length - 1
  let next: number
  if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = stepIndex.value === last ? 0 : stepIndex.value + 1
  else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') next = stepIndex.value === 0 ? last : stepIndex.value - 1
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = last
  else return

  event.preventDefault()
  selectStep(next)
  tabs.value?.[next]?.focus()
}

const pane = useTemplateRef<HTMLElement>('pane')
let observer: IntersectionObserver | undefined

onMounted(() => {
  playing.value = !window.matchMedia('(prefers-reduced-motion: reduce)').matches

  if (!('IntersectionObserver' in window)) {
    onscreen.value = true
    return
  }

  // Watches the transcript rather than the whole panel: on a phone the panel is
  // taller than the viewport, so a ratio threshold against it could never be met.
  observer = new IntersectionObserver(
    entries => { onscreen.value = entries[0]!.isIntersecting },
    { threshold: 0.5 },
  )
  observer.observe(pane.value!)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  cancelAnimationFrame(frame)
})
</script>

<template>
  <section class="k-section api">
    <div class="api__glow" />
    <div class="k-wrap api__inner">
      <div class="k-eyebrow api__eyebrow" data-reveal>
        <span>&quot;STUPID SIMPLE&quot; API</span>
      </div>
      <h2 class="k-heading api__title" data-reveal>From prompt to production</h2>
      <p class="api__lead" data-reveal>
        You describe the app to Claude, ChatGPT or Grok. Everything below is what it writes for you.
      </p>

      <div
        class="api__panel"
        data-reveal
        @mouseover="onAttend"
        @mouseleave="attended = false"
        @focusin="onAttend"
        @focusout="attended = false"
      >
        <div class="api__list" role="tablist" aria-label="Building a Kinotic app" @keydown="onTabKeydown">
          <button
            v-for="(entry, index) in steps"
            :id="`story-tab-${entry.id}`"
            :key="entry.id"
            ref="tabs"
            type="button"
            role="tab"
            class="api__tab"
            :class="{ 'api__tab--active': index === stepIndex }"
            :aria-selected="index === stepIndex"
            :aria-controls="`story-panel-${entry.id}`"
            :tabindex="index === stepIndex ? 0 : -1"
            @click="selectStep(index)"
          >
            <span class="api__tabicon">
              <KinoticIcon :name="entry.icon" :size="20" />
            </span>
            <span>
              <span class="api__tabtitle">
                <span class="api__tabnum">{{ entry.number }}</span>{{ entry.title }}
              </span>
              <span class="api__tabsummary">{{ entry.summary }}</span>
            </span>
          </button>
        </div>

        <div
          :id="`story-panel-${step.id}`"
          ref="pane"
          class="api__code"
          role="tabpanel"
          :aria-labelledby="`story-tab-${step.id}`"
          tabindex="0"
        >
          <div :key="`${step.id}-${slideIndex}`" class="chat">
            <div class="chat__turn">
              <span class="chat__who">You</span>
              <p v-for="(line, index) in slide.ask" :key="index" class="chat__said">{{ line }}</p>
            </div>

            <div class="chat__turn chat__turn--k">
              <span class="chat__who chat__who--k">Kinotic</span>
              <p v-for="(line, index) in slide.reply" :key="index" class="chat__said">{{ line }}</p>

              <pre v-if="slide.code" class="chat__code"><div v-for="(line, index) in slide.code" :key="index"><span
                v-for="(token, tokenIndex) in line"
                :key="tokenIndex"
                :class="`tok tok--${token.kind}`"
              >{{ token.text }}</span></div></pre>

              <ul v-if="slide.got" class="chat__got">
                <li v-for="(item, index) in slide.got" :key="index">{{ item }}</li>
              </ul>
            </div>
          </div>

          <div class="api__codebar">
            <!-- The window dots become this step's exchanges: one per slide, the
                 active one filling as its dwell time runs down. -->
            <span ref="track" class="api__dots" :class="{ 'api__dots--running': advancing }">
              <button
                v-for="(entry, index) in step.slides"
                :key="index"
                type="button"
                :class="{
                  'api__dot--active': index === slideIndex,
                  'api__dot--done': index < slideIndex,
                }"
                :aria-label="`Exchange ${index + 1} of ${step.slides.length}: ${entry.ask[0]}`"
                :aria-current="index === slideIndex"
                @click="selectSlide(index)"
              />
            </span>
            <button
              type="button"
              class="api__play"
              :aria-label="playing ? 'Pause the walkthrough' : 'Play the walkthrough'"
              @click="playing = !playing"
            >
              <svg width="11" height="11" viewBox="0 0 11 11" aria-hidden="true">
                <path v-if="playing" d="M2 1h2.4v9H2zM6.6 1H9v9H6.6z" fill="currentColor" />
                <path v-else d="M2.2 1l7 4.5-7 4.5z" fill="currentColor" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div class="api__cta" data-reveal>
        <a href="#cta" class="k-btn k-btn--red">Get Started ↗</a>
        <a
          href="https://github.com/kinotic-ai/claude-plugin"
          class="k-btn k-btn--ghost"
          target="_blank"
          rel="noopener"
        >Get the plugin ↗</a>
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
  margin-bottom: 22px;
}

.api__lead {
  max-width: 720px;
  margin: 0 auto 56px;
  text-align: center;
  font-size: 16px;
  line-height: 1.75;
  color: var(--color-k-muted);
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
  padding: 20px 30px;
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
  margin-bottom: 6px;
}

.api__tabnum {
  margin-right: 10px;
  font-size: 12px;
  color: var(--color-k-fainter);
  transition: color 0.2s ease;
}

.api__tab--active .api__tabnum {
  color: var(--color-k-mint);
}

.api__tabsummary {
  display: block;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--color-k-muted);
}

.api__code {
  display: flex;
  flex-direction: column;
  background: #0A0A0C;
  padding: 30px 34px 22px;
}

/* The transcript takes the slack so the dots stay pinned to the bottom edge,
   holding still while the exchanges above them change height. */
.chat {
  flex: 1;
}

.chat__turn {
  max-width: 46ch;
}

.chat__turn--k {
  margin-top: 24px;
}

.chat__who {
  display: block;
  font-family: var(--font-k-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-k-fainter);
  margin-bottom: 8px;
}

.chat__who--k {
  color: var(--color-k-mint);
}

.chat__said {
  margin: 0;
  font-size: 15.5px;
  line-height: 1.6;
  color: var(--color-k-text);
}

.chat__turn--k .chat__said {
  color: var(--color-k-body);
}

.chat__code {
  margin: 16px 0 0;
  padding: 14px 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  font-family: var(--font-k-mono);
  font-size: 12.5px;
  line-height: 1.85;
  color: var(--color-k-body);
  white-space: pre-wrap;
}

/* A row carrying no tokens is a deliberate blank line inside a sample; an empty
   div would otherwise collapse to no height. */
.chat__code > div:empty {
  height: 1lh;
}

.chat__got {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px 18px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.chat__got li {
  position: relative;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-k-muted);
}

.chat__got li::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: var(--color-k-mint);
}

.api__codebar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 26px;
}

.api__dots {
  display: inline-flex;
  gap: 7px;
}

.api__dots button {
  width: 9px;
  height: 9px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: #3A3A40;
  cursor: pointer;
  transition: background 0.3s ease;
}

.api__dots button.api__dot--done {
  background: var(--color-k-fainter);
}

/* The active dot fills like a pie chart as its exchange's dwell time runs down,
   from the --k-progress the walkthrough timer writes each frame. The inherited
   background transition has to go, or the fill lags the clock it reports. */
.api__dots button.api__dot--active {
  transition: none;
  background:
    conic-gradient(var(--color-k-mint) calc(var(--k-progress, 0) * 360deg), rgba(40, 254, 180, 0.22) 0);
}

.api__dots:not(.api__dots--running) button.api__dot--active {
  background: var(--color-k-mint);
}

.api__play {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  background: none;
  border: none;
  border-radius: 4px;
  color: var(--color-k-fainter);
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}

.api__play:hover {
  color: var(--color-k-text);
  background: rgba(255, 255, 255, 0.06);
}

.tok--c { color: var(--color-k-fainter); }
.tok--k { color: var(--color-k-mint); }
.tok--p { color: var(--color-k-body); }
.tok--t { color: #EC5C7F; }
.tok--a { color: var(--color-k-mint); }
.tok--d { color: var(--color-k-fainter); }
.tok--b { color: var(--color-k-text); }
.tok--s { color: var(--color-k-mint); }

.api__cta {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 14px;
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
    padding: 18px;
  }

  .api__code {
    padding: 22px 18px 18px;
  }

  .chat__said {
    font-size: 14.5px;
  }

  .chat__code {
    font-size: 11.5px;
  }

  .chat__got {
    grid-template-columns: 1fr;
  }
}
</style>
