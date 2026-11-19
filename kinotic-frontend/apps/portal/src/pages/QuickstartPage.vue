<template>
  <div class="flex flex-col">
    <PageHeader title="Quickstart" />

  <div class="mx-auto flex w-full max-w-[1120px] flex-col gap-6 pb-10">
    <header class="pb-2 pt-8 text-center md:pt-14">
      <span class="text-base font-semibold text-teal-700 dark:text-teal-300">Getting started</span>
      <h2 class="mt-3 text-[2.25rem] font-medium leading-[2.75rem] tracking-[-0.015em] text-surface-950 dark:text-surface-0">
        Build your first app with your AI
      </h2>
      <p class="mx-auto mt-4 max-w-[560px] text-sm text-muted-color">
        Pick the AI you use and paste one message into it. It installs the Kinotic plugin, helps you sign in,
        and asks what you want to build.
      </p>
    </header>

    <div class="mx-auto flex w-full max-w-[760px] flex-col gap-8">
      <div v-if="!loading && linkRequired"
           class="flex flex-wrap items-center gap-4 rounded-xl border border-amber-300 bg-amber-50 px-5 py-4 dark:border-amber-500/40 dark:bg-amber-500/10">
        <div class="min-w-[240px] flex-1">
          <div class="text-[15px] font-semibold text-amber-900 dark:text-amber-200">GitHub isn't linked yet</div>
          <p class="text-sm leading-6 text-amber-900 dark:text-amber-200">
            Your AI creates your project's repository under the GitHub account you link, so link it before you ask.
          </p>
        </div>
        <Button label="Link GitHub" size="small" :loading="linking" @click="linkGitHub">
          <template #icon><GitBranch :size="15" :stroke-width="1.75" class="mr-1.5" aria-hidden="true" /></template>
        </Button>
      </div>

      <section class="flex flex-col gap-3.5">
        <h2 :class="STEP_HEADING_CLASS"><span :class="STEP_NUMBER_CLASS">1</span>Choose your AI</h2>
        <div class="grid gap-3 sm:grid-cols-3">
          <button v-for="option in ASSISTANTS" :key="option.id" type="button" :aria-pressed="option.id === assistantId"
                  :class="['flex items-center gap-3 rounded-xl border px-4 py-3.5 text-left transition-colors',
                           option.id === assistantId
                             ? 'border-surface-950 bg-surface-50 ring-1 ring-surface-950 dark:border-surface-0 dark:bg-surface-800 dark:ring-surface-0'
                             : 'border-surface-300 bg-surface-0 hover:border-surface-400 dark:border-surface-600 dark:bg-surface-900']"
                  @click="assistantId = option.id">
            <span class="min-w-0 flex-1">
              <span class="block text-base font-semibold text-surface-950 dark:text-surface-0">{{ option.name }}</span>
              <span class="block text-xs text-muted-color">{{ option.agent }}</span>
            </span>
            <span :class="['flex h-5 w-5 shrink-0 items-center justify-center rounded-full',
                           option.id === assistantId
                             ? 'bg-surface-950 text-surface-0 dark:bg-surface-0 dark:text-surface-950'
                             : 'ring-1 ring-inset ring-surface-400 dark:ring-surface-500']">
              <Check v-if="option.id === assistantId" :size="12" :stroke-width="3.5" aria-hidden="true" />
            </span>
          </button>
        </div>
      </section>

      <section class="flex flex-col gap-3.5">
        <h2 :class="STEP_HEADING_CLASS"><span :class="STEP_NUMBER_CLASS">2</span>Copy this and paste it into {{ assistant.agent }}</h2>
        <div class="overflow-hidden rounded-xl bg-surface-950 dark:ring-1 dark:ring-surface-700">
          <pre class="whitespace-pre-wrap break-words px-5 pb-4 pt-5 font-mono text-[13px] leading-6 text-surface-100">{{ message }}</pre>
          <div class="flex flex-wrap items-center justify-between gap-3 border-t border-surface-800 py-3 pl-5 pr-3">
            <span class="text-xs text-surface-400">Paste it into a new {{ assistant.agent }} session.</span>
            <button type="button"
                    :class="['inline-flex items-center gap-2 rounded-md px-4 py-2 text-sm font-semibold transition-colors',
                             copied === message ? 'bg-green-500 text-white' : 'bg-surface-0 text-surface-950 hover:bg-surface-200']"
                    @click="copy(message)">
              <Check v-if="copied === message" :size="15" :stroke-width="3" aria-hidden="true" />
              <Copy v-else :size="15" :stroke-width="2" aria-hidden="true" />
              {{ copied === message ? 'Copied' : 'Copy message' }}
            </button>
          </div>
        </div>

        <details class="text-sm text-muted-color">
          <summary class="cursor-pointer py-1 font-medium text-surface-700 hover:text-surface-950 dark:text-surface-200 dark:hover:text-surface-0">
            Prefer to set it up yourself?
          </summary>
          <div v-for="block in manualSetup" :key="block.caption" class="mt-3">
            <p>{{ block.caption }}</p>
            <div class="mt-2 overflow-hidden rounded-lg bg-surface-950 dark:ring-1 dark:ring-surface-700">
              <div class="group flex items-start gap-3 px-3.5 py-3">
                <pre class="min-w-0 flex-1 overflow-x-auto font-mono text-xs leading-5 text-surface-100">{{ block.text }}</pre>
                <button type="button" class="rounded p-1 text-surface-400 transition hover:bg-surface-800 hover:text-surface-0"
                        :aria-label="`Copy: ${block.caption}`" v-tooltip.top="copied === block.text ? 'Copied' : 'Copy'" @click="copy(block.text)">
                  <Copy :size="13" :stroke-width="1.75" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>
        </details>
      </section>

      <section class="flex flex-col gap-3.5">
        <h2 :class="STEP_HEADING_CLASS"><span :class="STEP_NUMBER_CLASS">3</span>Then follow along</h2>
        <ol class="overflow-hidden rounded-xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
          <li v-for="(step, index) in steps" :key="step.id"
              class="flex items-start gap-4 border-b border-surface-100 px-5 py-4 last:border-b-0 dark:border-surface-800">
            <span :class="['mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full', markerClass(step, index)]">
              <Check v-if="step.done" :size="13" :stroke-width="3.5" aria-hidden="true" />
              <X v-else-if="step.failed" :size="13" :stroke-width="3.5" aria-hidden="true" />
              <LoaderCircle v-else-if="!loading && index === currentIndex" :size="20" :stroke-width="2" class="animate-spin" aria-hidden="true" />
            </span>
            <div class="min-w-0 flex-1">
              <h3 class="text-[15px] font-semibold text-surface-950 dark:text-surface-0">{{ step.title }}</h3>
              <p class="mt-0.5 text-sm leading-6 text-muted-color">{{ step.description }}</p>
              <RouterLink v-if="!loading && step.link" :to="step.link.to"
                          class="mt-1 inline-flex items-center gap-1 text-xs font-medium text-surface-600 hover:text-surface-950 dark:text-surface-300 dark:hover:text-surface-0">
                {{ step.link.label }}
                <ArrowRight :size="12" :stroke-width="2" aria-hidden="true" />
              </RouterLink>
            </div>
            <Skeleton v-if="loading" width="4rem" height="0.75rem" class="mt-1.5" />
            <span v-else-if="step.done" class="mt-0.5 whitespace-nowrap text-xs font-semibold text-green-700 dark:text-green-400">{{ step.doneDetail }}</span>
            <span v-else-if="step.failed" class="mt-0.5 whitespace-nowrap text-xs font-semibold text-red-600 dark:text-red-400">Failed</span>
            <span v-else-if="index === currentIndex" class="mt-0.5 whitespace-nowrap text-xs font-semibold text-teal-700 dark:text-teal-300">Waiting</span>
          </li>
        </ol>
        <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
      </section>
    </div>

    <section :class="[CARD_CLASS, 'grid items-center gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]']">
      <div>
        <h2 class="text-lg font-semibold text-surface-950 dark:text-surface-0">What you can do</h2>
        <div class="mt-5 flex flex-col gap-1" role="tablist" aria-label="What you can do">
          <button v-for="feature in FEATURES" :key="feature.id" type="button" role="tab" :aria-selected="activeFeature === feature.id"
                  class="group flex gap-4 rounded-r-lg py-2 pr-2 text-left" @click="activeFeature = feature.id">
            <span :class="['w-1 shrink-0 rounded-full transition-colors', activeFeature === feature.id ? feature.bar : 'bg-surface-200 group-hover:bg-surface-300 dark:bg-surface-700']" />
            <span class="min-w-0">
              <span :class="['flex items-center gap-2 text-[15px] font-semibold transition-colors', activeFeature === feature.id ? 'text-surface-950 dark:text-surface-0' : 'text-surface-500 group-hover:text-surface-800 dark:text-surface-400']">
                <component :is="feature.icon" :size="17" :stroke-width="1.75" aria-hidden="true" />
                {{ feature.title }}
              </span>
              <span v-if="activeFeature === feature.id" class="mt-1.5 block text-sm leading-6 text-muted-color">
                {{ feature.description }}
                <a :href="feature.href" target="_blank" rel="noopener" class="ml-0.5 inline-flex items-center gap-0.5 font-medium text-surface-950 hover:underline dark:text-surface-0" @click.stop>
                  Learn more <ArrowUpRight :size="12" :stroke-width="2" aria-hidden="true" />
                </a>
              </span>
            </span>
          </button>
        </div>
      </div>

      <!-- An illustration of the selected feature; decorative, so it is hidden from assistive technology -->
      <div class="quickstart-canvas rounded-2xl border border-surface-200 p-6 dark:border-surface-700" aria-hidden="true">
        <div v-if="activeFeature === 'data'" class="grid grid-cols-[minmax(0,1.2fr)_auto_minmax(0,1fr)] items-center gap-3">
          <div class="rounded-xl bg-surface-950 p-4 font-mono text-[11px] leading-5 text-surface-300 shadow-sm">
            <div><span class="text-purple-300">@Entity</span>()</div>
            <div><span class="text-sky-300">export class</span> <span class="text-surface-0">Person</span> {</div>
            <div class="pl-3"><span class="text-purple-300">@AutoGeneratedId</span> id</div>
            <div class="pl-3"><span class="text-purple-300">@NotNull</span> firstName</div>
            <div class="pl-3">age: <span class="text-green-300">number</span></div>
            <div>}</div>
          </div>
          <ArrowRight :size="18" :stroke-width="1.75" class="text-surface-400" />
          <div class="flex flex-col gap-2">
            <span v-for="output in DATA_OUTPUTS" :key="output.label"
                  class="flex items-center gap-2 rounded-lg border border-surface-200 bg-surface-0 px-3 py-2 text-xs font-medium text-surface-800 shadow-sm dark:border-surface-700 dark:bg-surface-900 dark:text-surface-100">
              <span :class="['flex h-6 w-6 items-center justify-center rounded-md', output.tint]">
                <component :is="output.icon" :size="13" :stroke-width="2" />
              </span>
              {{ output.label }}
            </span>
          </div>
        </div>

        <div v-else-if="activeFeature === 'deploy'" class="flex flex-col gap-4">
          <div class="flex items-center gap-2 rounded-lg border border-surface-200 bg-surface-0 px-3 py-2 font-mono text-[11px] text-surface-700 shadow-sm dark:border-surface-700 dark:bg-surface-900 dark:text-surface-200">
            <GitCommitHorizontal :size="14" :stroke-width="2" class="text-surface-400" />
            git push origin main
          </div>
          <div class="flex items-center">
            <template v-for="(step, index) in DEPLOY_STEPS" :key="step.label">
              <span v-if="index > 0" class="h-0.5 flex-1 bg-green-500" />
              <span class="flex flex-col items-center gap-1.5">
                <span class="flex h-10 w-10 items-center justify-center rounded-xl border border-green-300 bg-green-50 text-green-700 shadow-[0_0_0_3px_white,0_0_0_5px_#22c55e] dark:border-green-500/40 dark:bg-green-500/10 dark:text-green-300 dark:shadow-[0_0_0_3px_#171717,0_0_0_5px_#22c55e]">
                  <component :is="step.icon" :size="17" :stroke-width="1.75" />
                </span>
                <span class="text-[10px] font-semibold uppercase tracking-wider text-surface-500">{{ step.label }}</span>
              </span>
            </template>
          </div>
          <div class="flex items-center gap-2 self-start rounded-full bg-green-100 px-3 py-1 text-xs font-medium text-green-700 dark:bg-green-500/15 dark:text-green-300">
            <Check :size="13" :stroke-width="2.5" /> Deployed in 47s
          </div>
        </div>

        <div v-else class="flex flex-col gap-3">
          <div class="rounded-xl border border-surface-200 bg-surface-0 p-4 shadow-sm dark:border-surface-700 dark:bg-surface-900">
            <div class="mb-2 flex items-center justify-between text-[11px]">
              <span class="font-semibold text-surface-950 dark:text-surface-0">Requests</span>
              <span class="text-muted-color">last hour</span>
            </div>
            <svg viewBox="0 0 240 60" class="h-16 w-full" preserveAspectRatio="none">
              <defs>
                <linearGradient id="quickstart-area" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stop-color="#22c55e" stop-opacity="0.35" />
                  <stop offset="100%" stop-color="#22c55e" stop-opacity="0" />
                </linearGradient>
              </defs>
              <path d="M0 48 L20 44 L40 46 L60 36 L80 40 L100 28 L120 32 L140 20 L160 26 L180 14 L200 22 L220 10 L240 16 L240 60 L0 60 Z" fill="url(#quickstart-area)" />
              <path d="M0 48 L20 44 L40 46 L60 36 L80 40 L100 28 L120 32 L140 20 L160 26 L180 14 L200 22 L220 10 L240 16" fill="none" stroke="#22c55e" stroke-width="2" vector-effect="non-scaling-stroke" />
            </svg>
          </div>
          <div class="rounded-xl bg-surface-950 p-3 font-mono text-[10.5px] leading-5 text-surface-300 shadow-sm">
            <div v-for="line in LOG_LINES" :key="line.text" class="flex gap-2 truncate">
              <span :class="['w-8', line.level === 'INFO' ? 'text-green-400' : 'text-amber-300']">{{ line.level }}</span>
              <span class="text-surface-500">{{ line.time }}</span>
              <span class="truncate">{{ line.text }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section :class="CARD_CLASS">
      <h2 class="text-lg font-semibold text-surface-950 dark:text-surface-0">Resources</h2>
      <div class="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        <a v-for="resource in RESOURCES" :key="resource.href" :href="resource.href" target="_blank" rel="noopener"
           class="group flex items-center gap-3 rounded-lg px-3 py-2.5 transition-colors hover:bg-surface-100 dark:hover:bg-surface-800">
          <component :is="resource.icon" :size="17" :stroke-width="1.75" class="shrink-0 text-surface-500" aria-hidden="true" />
          <span class="flex-1 text-sm font-medium text-surface-900 dark:text-surface-100">{{ resource.label }}</span>
          <ArrowUpRight :size="13" :stroke-width="1.75" class="text-surface-400 transition-colors group-hover:text-surface-700" aria-hidden="true" />
        </a>
      </div>
    </section>
  </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, onUnmounted, ref, type Component } from 'vue'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import { ArrowRight, ArrowUpRight, BookOpen, Boxes, ChartLine, Check, CloudUpload, Copy, Database, FolderGit2, GitBranch,
         GitCommitHorizontal, Globe, Layers, Library, LoaderCircle, Package, Table, Terminal, X } from '@lucide/vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import { DelegateType, type DelegatingParticipantIdentity, DeploymentStatusType, type GitHubAppInstallation, type Project,
         RepoHostType, type RepositoryHost } from '@kinotic-ai/management-api'
import { apiUrl, createDebug, PageHeader, TINTS } from '@kinotic-ai/frontend-common'
import { repositoryHost } from '@/util/repositoryHost'
import { DOCUMENTATION_URL } from '@/util/externalLinks'

/**
 * The organization's onboarding page: the person picks the AI they use and pastes one message into
 * its coding agent, which installs the Kinotic plugin, explains the sign-in and asks what to build.
 * The steps that follow are checked off from the organization's real state as they happen.
 */

const debug = createDebug('quickstart')

const QUICK_START_URL = 'https://kinotic.ai/getting-started/quick-start'
// This deployment's MCP server
const MCP_URL = new URL(apiUrl('/mcp'), window.location.origin).toString()
// The plugin's own MCP server is Kinotic Cloud's, so a Kinotic running on this machine is added beside it
const LOCAL_MCP_SERVER = ['localhost', '127.0.0.1'].includes(new URL(MCP_URL).hostname)
const LOCAL_SERVER_NAME = 'kinotic-local'
// How many projects the page reads to find the most recently updated one
const PROJECT_SAMPLE_SIZE = 20
// How many of the user's delegates the page reads to find an authorized MCP client
const DELEGATE_SAMPLE_SIZE = 50
const POLL_INTERVAL_MS = 5000
// How long a copy button reads "Copied"
const COPIED_RESET_MS = 2000

const CARD_CLASS = 'rounded-2xl border border-surface-200 bg-surface-0 p-7 shadow-[0_1px_3px_rgba(0,0,0,0.04)] dark:border-surface-700 dark:bg-surface-800/30'
const STEP_HEADING_CLASS = 'flex items-center gap-3 text-lg font-semibold text-surface-950 dark:text-surface-0'
const STEP_NUMBER_CLASS = 'flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-surface-950 text-xs font-semibold text-surface-0 dark:bg-surface-0 dark:text-surface-950'

type AssistantId = 'claude' | 'chatgpt' | 'grok'

interface Assistant {
  id: AssistantId
  /** The AI, as the person knows it. */
  name: string
  /** Its coding agent, which the Kinotic plugin installs into. */
  agent: string
  /** Shell commands that install the Kinotic plugin into the agent. */
  installCommands: string[]
  /** How this deployment's MCP server is added to the agent beside the plugin's own. */
  localServer: { how: string, lines: string[] }
  /** The plugin's MCP server as the agent lists it. */
  pluginServer: string
  /** What the person does to sign the agent in to the given MCP server. */
  signIn: (server: string) => string
}

const ASSISTANTS: Assistant[] = [
  {
    id: 'claude',
    name: 'Claude',
    agent: 'Claude Code',
    installCommands: ['claude plugin marketplace add kinotic-ai/agent-plugin', 'claude plugin install kinotic@kinotic'],
    localServer: { how: 'by running', lines: [`claude mcp add -s user --transport http ${LOCAL_SERVER_NAME} ${MCP_URL}`] },
    pluginServer: 'plugin:kinotic:kinotic-os',
    signIn: server => `Run /mcp in Claude Code, select ${server} and authenticate, then approve the consent screen that opens in your browser. If ${server} isn't listed, run /reload-plugins first.`
  },
  {
    id: 'chatgpt',
    name: 'ChatGPT',
    agent: 'Codex',
    installCommands: ['codex plugin marketplace add kinotic-ai/agent-plugin', 'codex plugin add kinotic@kinotic'],
    localServer: { how: 'by running', lines: [`codex mcp add ${LOCAL_SERVER_NAME} --url ${MCP_URL}`] },
    pluginServer: 'kinotic-os',
    signIn: server => `Run codex mcp login ${server} in a terminal, then approve the consent screen that opens in your browser.`
  },
  {
    id: 'grok',
    name: 'Grok',
    agent: 'Grok Build',
    installCommands: ['grok plugin install kinotic-ai/agent-plugin --trust'],
    localServer: {
      how: 'by adding this to ~/.grok/config.toml',
      // Grok Build has no client metadata document of its own, so it signs in as the one Kinotic publishes
      lines: [`[mcp_servers.${LOCAL_SERVER_NAME}]`, `url = "${MCP_URL}"`, 'oauth_client_id = "https://kinotic.ai/oauth/grok-build-client.json"']
    },
    pluginServer: 'kinotic-os',
    signIn: server => `Run /mcps in Grok Build, select ${server} and press i, then approve the consent screen that opens in your browser.`
  }
]

type FeatureId = 'data' | 'deploy' | 'observe'

const FEATURES: { id: FeatureId, title: string, icon: Component, bar: string, href: string, description: string }[] = [
  { id: 'data', title: 'Model your data', icon: markRaw(Table), bar: 'bg-purple-500', href: 'https://kinotic.ai/apps/persistence/overview',
    description: 'Define entities in TypeScript and get typed repositories and backing storage for them.' },
  { id: 'deploy', title: 'Deploy on every push', icon: markRaw(CloudUpload), bar: 'bg-sky-500', href: 'https://kinotic.ai/apps/deployment/push-to-deploy',
    description: 'Pushing to a project\'s default branch builds and deploys its microservices and publishes its UIs.' },
  { id: 'observe', title: 'See it running', icon: markRaw(ChartLine), bar: 'bg-green-500', href: 'https://kinotic.ai/apps/services/overview',
    description: 'Follow every deployment step by step, and read your services\' traces, metrics and logs.' }
]
const activeFeature = ref<FeatureId>('data')

// Placeholders for the feature illustrations
const DATA_OUTPUTS = [
  { label: 'PersonRepository', icon: markRaw(Database), tint: TINTS.purple }
]
const DEPLOY_STEPS = [
  { label: 'Sync', icon: markRaw(FolderGit2) },
  { label: 'Build', icon: markRaw(Package) },
  { label: 'Run', icon: markRaw(Boxes) },
  { label: 'Publish', icon: markRaw(Globe) }
]
const LOG_LINES = [
  { level: 'INFO', time: '12:04:31', text: 'PersonService.createPerson 12ms' },
  { level: 'INFO', time: '12:04:32', text: 'PersonRepository.save 8ms' },
  { level: 'WARN', time: '12:04:35', text: 'slow query findAll 412ms' }
]

const RESOURCES = [
  { label: 'Quick start', href: QUICK_START_URL, icon: markRaw(BookOpen) },
  { label: 'Application structure', href: 'https://kinotic.ai/apps/application-structure/overview', icon: markRaw(Layers) },
  { label: 'CLI reference', href: 'https://kinotic.ai/apps/cli-reference', icon: markRaw(Terminal) },
  { label: 'All documentation', href: DOCUMENTATION_URL, icon: markRaw(Library) }
]

interface Step {
  id: 'signIn' | 'ask' | 'deploy'
  title: string
  description: string
  done: boolean
  failed?: boolean
  /** What is done, e.g. the connected client, shown beside a checked step. */
  doneDetail?: string
  /** Where to see what the step made. */
  link?: { label: string, to: string }
}

const loading = ref(true)
const linking = ref(false)
const error = ref<string | null>(null)
// The text last copied, while its button reads "Copied"
const copied = ref<string | null>(null)
const assistantId = ref<AssistantId>('claude')
const installation = ref<GitHubAppInstallation | null>(null)
const repoHost = ref<RepositoryHost | null>(null)
const projects = ref<Project[]>([])
const mcpClient = ref<DelegatingParticipantIdentity | null>(null)
// Where the latest project's deployment stands, null until it has one
const deploymentPhase = ref<DeploymentStatusType | null>(null)

const assistant = computed(() => ASSISTANTS.find(option => option.id === assistantId.value) ?? ASSISTANTS[0])

/** The message the person pastes into their coding agent. */
const message = computed(() => {
  const tasks = [`Install the Kinotic plugin by running:\n${indented(assistant.value.installCommands)}`]
  if (LOCAL_MCP_SERVER) {
    tasks.push(`Add my local Kinotic ${assistant.value.localServer.how}:\n${indented(assistant.value.localServer.lines)}`)
  }
  tasks.push(LOCAL_MCP_SERVER
      ? `Tell me what I need to do to sign in to my local Kinotic, the ${LOCAL_SERVER_NAME} server.`
      : 'Tell me what I need to do to sign in to Kinotic.')
  tasks.push('Then ask me what I want to build.')
  return `I want to build and deploy an app on Kinotic. Set yourself up first:\n\n${tasks.map((task, index) => `${index + 1}. ${task}`).join('\n')}`
})

/** The same setup for a person who would rather do it by hand. */
const manualSetup = computed(() => {
  const blocks = [{ caption: 'Install the Kinotic plugin by running these in a terminal:', text: assistant.value.installCommands.join('\n') }]
  if (LOCAL_MCP_SERVER) {
    blocks.push({ caption: `Add your local Kinotic ${assistant.value.localServer.how}:`, text: assistant.value.localServer.lines.join('\n') })
  }
  return blocks
})

function indented(lines: string[]): string {
  return lines.map(line => `   ${line}`).join('\n')
}

/** The project updated most recently, which the page names and links to. */
const latestProject = computed(() => [...projects.value]
    .sort((a, b) => new Date(b.updated ?? 0).getTime() - new Date(a.updated ?? 0).getTime())[0] ?? null)

function projectPath(project: Project): string {
  return `/application/${encodeURIComponent(project.applicationId)}/project/${encodeURIComponent(project.id ?? '')}`
}

// GitHub must be linked before the agent can create a project's repository there; another host needs no link
const linkRequired = computed(() => repoHost.value?.type === RepoHostType.GITHUB && installation.value === null)

const steps = computed<Step[]>(() => [
  {
    id: 'signIn',
    title: 'Sign in to Kinotic',
    description: assistant.value.signIn(LOCAL_MCP_SERVER ? LOCAL_SERVER_NAME : assistant.value.pluginServer),
    done: mcpClient.value !== null,
    doneDetail: mcpClient.value?.displayName ? `${mcpClient.value.displayName} connected` : 'Connected',
    link: mcpClient.value ? { label: 'Connected apps', to: '/account/connected-apps' } : undefined
  },
  {
    id: 'ask',
    title: 'Say what you want to build',
    description: `${assistant.value.agent} asks what you want. Answer in your own words, and it creates the application and its project, writes the entities, services and UIs, and pushes.`,
    done: projects.value.length > 0,
    doneDetail: latestProject.value?.name,
    link: latestProject.value ? { label: 'Open project', to: projectPath(latestProject.value) } : undefined
  },
  {
    id: 'deploy',
    title: 'Watch it deploy',
    description: 'The push deploys your app, and so does every later push to the project\'s default branch.',
    done: deploymentPhase.value === DeploymentStatusType.RUNNING,
    failed: deploymentPhase.value === DeploymentStatusType.FAILED,
    doneDetail: 'Deployed',
    link: latestProject.value && deploymentPhase.value !== null
        ? { label: 'Open deployment', to: `${projectPath(latestProject.value)}/deployment` }
        : undefined
  }
])

// The first step not done is the one the page is waiting on
const currentIndex = computed(() => steps.value.findIndex(step => !step.done))

function markerClass(step: Step, index: number): string {
  let ret: string
  if (step.done) {
    ret = 'bg-green-500 text-white'
  } else if (step.failed) {
    ret = 'bg-red-500 text-white'
  } else if (!loading.value && index === currentIndex.value) {
    ret = 'text-teal-600 dark:text-teal-300'
  } else {
    ret = 'ring-1 ring-inset ring-surface-300 dark:ring-surface-600'
  }
  return ret
}

async function linkGitHub(): Promise<void> {
  linking.value = true
  try {
    window.location.href = await Kinotic.githubAppInstallations.startInstall('/quickstart')
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
    linking.value = false
  }
}

let copiedTimer: ReturnType<typeof setTimeout> | undefined

async function copy(text: string): Promise<void> {
  await navigator.clipboard.writeText(text)
  copied.value = text
  clearTimeout(copiedTimer)
  copiedTimer = setTimeout(() => { copied.value = null }, COPIED_RESET_MS)
}

async function loadInstallation(): Promise<void> {
  try {
    installation.value = await Kinotic.githubAppInstallations.findForCurrentOrg()
  } catch (err) {
    debug('Failed to read the GitHub link: %O', err)
  }
}

async function loadRepositoryHost(): Promise<void> {
  repoHost.value = await repositoryHost()
}

async function loadProjects(): Promise<void> {
  const page = await Kinotic.projects.findAll(Pageable.create(0, PROJECT_SAMPLE_SIZE))
  projects.value = page.content ?? []
}

async function loadMcpClient(): Promise<void> {
  const page = await Kinotic.delegates.findMyDelegates(Pageable.create(0, DELEGATE_SAMPLE_SIZE))
  mcpClient.value = (page.content ?? []).find(delegate => delegate.enabled && delegate.delegateType === DelegateType.MCP_CLIENT) ?? null
}

async function loadDeployment(): Promise<void> {
  const projectId = latestProject.value?.id
  if (!projectId) return
  const deployment = await Kinotic.projects.findDeployment(projectId)
  deploymentPhase.value = deployment?.state.observed?.phase ?? null
}

/** Reads the state the steps are checked off from. */
async function loadProgress(): Promise<void> {
  await Promise.all([loadProjects(), loadMcpClient()])
  await loadDeployment()
}

let pollTimer: ReturnType<typeof setInterval> | undefined

async function poll(): Promise<void> {
  try {
    await loadProgress()
  } catch (err) {
    debug('Failed to refresh the quickstart steps: %O', err)
  }
  if (currentIndex.value === -1) {
    clearInterval(pollTimer)
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadInstallation(), loadProgress(), loadRepositoryHost()])
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
  if (currentIndex.value !== -1) {
    pollTimer = setInterval(() => { void poll() }, POLL_INTERVAL_MS)
  }
})

onUnmounted(() => {
  clearInterval(pollTimer)
  clearTimeout(copiedTimer)
})
</script>

<style scoped>
.quickstart-canvas {
  background-image:
    radial-gradient(circle, color-mix(in srgb, var(--p-surface-400) 16%, transparent) 1px, transparent 1.2px),
    linear-gradient(135deg, var(--p-sky-50), color-mix(in srgb, var(--p-indigo-50) 60%, transparent), color-mix(in srgb, var(--p-violet-50) 70%, transparent));
  background-size: 14px 14px, 100% 100%;
}

.dark .quickstart-canvas {
  background-image:
    radial-gradient(circle, color-mix(in srgb, var(--p-surface-500) 14%, transparent) 1px, transparent 1.2px),
    linear-gradient(135deg, color-mix(in srgb, var(--p-sky-500) 10%, transparent), color-mix(in srgb, var(--p-indigo-500) 5%, transparent), color-mix(in srgb, var(--p-violet-500) 10%, transparent));
}
</style>
