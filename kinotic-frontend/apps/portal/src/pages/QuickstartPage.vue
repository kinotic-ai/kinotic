<template>
  <div class="flex flex-col">
    <PageHeader title="Quickstart" />

  <div class="mx-auto flex w-full max-w-[1120px] flex-col gap-6 pb-10">
    <header class="pb-8 pt-8 text-center md:pb-12 md:pt-14">
      <span class="text-base font-semibold text-teal-700 dark:text-teal-300">Getting started</span>
      <h2 class="mt-3 text-[2.25rem] font-medium leading-[2.75rem] tracking-[-0.015em] text-surface-950 dark:text-surface-0">
        Build and ship your first app on Kinotic
      </h2>
      <p class="mx-auto mt-4 max-w-[560px] text-sm text-muted-color">
        Four steps from an empty organization to an app running from your own repository.
      </p>
      <div class="mx-auto mt-8 flex max-w-[320px] items-center gap-3">
        <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700">
          <div class="h-full rounded-full bg-green-500 transition-all duration-500" :style="{ width: `${progress}%` }" />
        </div>
        <span class="whitespace-nowrap text-xs font-medium tabular-nums text-surface-700 dark:text-surface-200">
          <Skeleton v-if="loading" width="4rem" height="0.75rem" />
          <template v-else>{{ doneCount }} of {{ steps.length }} done</template>
        </span>
      </div>
    </header>

    <div class="grid items-stretch gap-6 lg:grid-cols-[minmax(0,1.45fr)_minmax(0,1fr)]">
      <section :class="CARD_CLASS">
        <h2 class="text-lg font-semibold text-surface-950 dark:text-surface-0">Get started</h2>
        <ol class="mt-5">
          <li v-for="(step, index) in steps" :key="step.id" class="relative flex gap-4 pb-7 last:pb-0">
            <!-- the line down to the next step, green once this one is done -->
            <span v-if="index < steps.length - 1" aria-hidden="true"
                  :class="['absolute left-[13px] top-8 bottom-1 w-0.5 rounded-full', step.done ? 'bg-green-500' : 'bg-surface-200 dark:bg-surface-700']" />
            <span :class="['relative z-[1] flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold', markerClass(step, index)]">
              <Check v-if="step.done" :size="15" :stroke-width="3" aria-hidden="true" />
              <template v-else>{{ index + 1 }}</template>
            </span>
            <div class="min-w-0 flex-1 pt-0.5">
              <div class="flex flex-wrap items-center gap-2">
                <h3 :class="['text-[15px] font-semibold', step.done ? 'text-green-700 dark:text-green-400' : index === currentIndex ? 'text-surface-950 dark:text-surface-0' : 'text-surface-500 dark:text-surface-400']">
                  {{ step.title }}
                </h3>
                <span v-if="step.done && step.doneDetail" class="text-xs text-muted-color">· {{ step.doneDetail }}</span>
              </div>
              <p class="mt-1 text-sm leading-6 text-muted-color">{{ step.description }}</p>

              <div v-if="step.id === 'build' && !loading" class="mt-3 overflow-hidden rounded-lg bg-surface-950 dark:ring-1 dark:ring-surface-700">
                <div v-for="command in PLUGIN_COMMANDS" :key="command"
                     class="group flex items-center gap-3 px-3.5 py-1.5 first:pt-3 last:pb-3">
                  <span class="select-none font-mono text-xs text-green-400">›</span>
                  <code class="flex-1 truncate font-mono text-xs text-surface-100">{{ command }}</code>
                  <button type="button" class="rounded p-1 text-surface-400 opacity-0 transition hover:bg-surface-800 hover:text-surface-0 focus:opacity-100 group-hover:opacity-100"
                          :aria-label="`Copy ${command}`" v-tooltip.top="copied === command ? 'Copied' : 'Copy'" @click="copy(command)">
                    <Copy :size="13" :stroke-width="1.75" aria-hidden="true" />
                  </button>
                </div>
              </div>

              <div v-if="!loading && !step.done && index === currentIndex" class="mt-3 flex flex-wrap gap-2">
                <Button v-for="action in step.actions" :key="action.label" :label="action.label"
                        :severity="action.primary ? undefined : 'secondary'" :outlined="!action.primary" size="small"
                        :loading="action.label === 'Link GitHub' && linking" @click="action.run">
                  <template #icon><component :is="action.icon" :size="15" :stroke-width="1.75" class="mr-1.5" aria-hidden="true" /></template>
                </Button>
              </div>
              <RouterLink v-else-if="!loading && step.done && step.revisit" :to="step.revisit.to"
                          class="mt-2 inline-flex items-center gap-1 text-xs font-medium text-surface-600 hover:text-surface-950 dark:text-surface-300 dark:hover:text-surface-0">
                {{ step.revisit.label }}
                <ArrowRight :size="12" :stroke-width="2" aria-hidden="true" />
              </RouterLink>
            </div>
          </li>
        </ol>
        <p v-if="error" class="mt-4 text-sm text-red-600">{{ error }}</p>
      </section>

      <aside :class="[CARD_CLASS, '!p-0 flex flex-col overflow-hidden']">
        <!-- Claude Code feeding Kinotic; decorative, and it fills whatever height the checklist beside it takes -->
        <div class="claude-canvas relative flex min-h-[240px] flex-1 flex-col items-center justify-center gap-7 px-6 py-8" aria-hidden="true">
          <div class="flex items-center gap-3">
            <!-- a light tracing round the outside of each tile, so both read as running -->
            <span class="relative flex h-16 w-16 items-center justify-center rounded-2xl bg-[#2b1d17] shadow-[0_0_0_1px_rgba(217,119,87,0.35),0_8px_30px_rgba(217,119,87,0.35)]">
              <svg class="pointer-events-none absolute -inset-1.5 h-[76px] w-[76px]" viewBox="0 0 76 76" fill="none">
                <rect x="1" y="1" width="74" height="74" rx="21" stroke="#D97757" stroke-opacity="0.18" stroke-width="1.5" />
                <rect class="claude-canvas__orbit claude-canvas__orbit--claude" x="1" y="1" width="74" height="74" rx="21" stroke="#D97757" stroke-width="2"
                      stroke-linecap="round" pathLength="100" stroke-dasharray="16 84" />
              </svg>
              <svg viewBox="0 0 24 24" class="h-9 w-9">
                <path fill="#D97757" fill-rule="evenodd" clip-rule="evenodd"
                      d="M20.998 10.949H24v3.102h-3v3.028h-1.487V20H18v-2.921h-1.487V20H15v-2.921H9V20H7.488v-2.921H6V20H4.487v-2.921H3V14.05H0V10.95h3V5h17.998v5.949zM6 10.949h1.488V8.102H6v2.847zm10.51 0H18V8.102h-1.49v2.847z" />
                <!-- lids that drop over the two eye holes when it blinks -->
                <rect class="claude-canvas__lid" x="6" y="8.102" width="1.488" height="2.847" fill="#D97757" />
                <rect class="claude-canvas__lid" x="16.51" y="8.102" width="1.49" height="2.847" fill="#D97757" />
              </svg>
            </span>
            <svg width="88" height="12" viewBox="0 0 88 12" class="overflow-visible">
              <defs>
                <!-- Claude Code's orange fading into Kinotic's green along the line -->
                <linearGradient id="quickstart-claude-flow" gradientUnits="userSpaceOnUse" x1="2" y1="6" x2="86" y2="6">
                  <stop offset="0%" stop-color="#D97757" />
                  <stop offset="100%" stop-color="#28FEB4" />
                </linearGradient>
              </defs>
              <!-- a faint track with a light running along it, the same style as the rings round the tiles -->
              <line x1="2" y1="6" x2="86" y2="6" stroke="url(#quickstart-claude-flow)" stroke-opacity="0.22" stroke-width="3" stroke-linecap="round" />
              <line x1="2" y1="6" x2="86" y2="6" class="claude-canvas__flow" stroke="url(#quickstart-claude-flow)" stroke-width="4" stroke-linecap="round"
                    pathLength="100" stroke-dasharray="22 200" />
            </svg>
            <span class="relative flex h-16 w-16 items-center justify-center rounded-2xl bg-black shadow-[0_0_0_1px_rgba(40,254,180,0.3),0_8px_30px_rgba(40,254,180,0.25)]">
              <svg class="pointer-events-none absolute -inset-1.5 h-[76px] w-[76px]" viewBox="0 0 76 76" fill="none">
                <rect x="1" y="1" width="74" height="74" rx="21" stroke="#28FEB4" stroke-opacity="0.18" stroke-width="1.5" />
                <rect class="claude-canvas__orbit claude-canvas__orbit--kinotic" x="1" y="1" width="74" height="74" rx="21" stroke="#28FEB4" stroke-width="2"
                      stroke-linecap="round" pathLength="100" stroke-dasharray="16 84" />
              </svg>
              <svg viewBox="0 0 27 24" class="h-7 w-8" fill="#28FEB4">
                <!-- the two windows are its eyes -->
                <g class="claude-canvas__kinotic-eyes">
                  <path d="M10.9385 14.5693H5.6845C5.47695 14.5693 5.3087 14.7363 5.3087 14.9424V17.6199C5.3087 17.826 5.47695 17.9931 5.6845 17.9931H10.9385C11.146 17.9931 11.3143 17.826 11.3143 17.6199V14.9424C11.3143 14.7363 11.146 14.5693 10.9385 14.5693Z" />
                  <path d="M18.8705 14.5693H13.6165C13.409 14.5693 13.2407 14.7363 13.2407 14.9424V17.6199C13.2407 17.826 13.409 17.9931 13.6165 17.9931H18.8705C19.078 17.9931 19.2463 17.826 19.2463 17.6199V14.9424C19.2463 14.7363 19.078 14.5693 18.8705 14.5693Z" />
                </g>
                <path d="M21.0924 12.062V20.2211C21.0924 20.3172 21.054 20.4094 20.9856 20.4773C20.9172 20.5453 20.8243 20.5834 20.7276 20.5834H3.81277C3.716 20.5834 3.6232 20.5453 3.55478 20.4773C3.48636 20.4094 3.44791 20.3172 3.44791 20.2211V13.0728C3.44775 13.0005 3.46934 12.9299 3.50993 12.8699L7.80796 6.31214C7.84883 6.25681 7.87302 6.19109 7.87776 6.12265C7.88249 6.0542 7.86756 5.98583 7.83469 5.92548C7.80182 5.86513 7.75235 5.8153 7.69208 5.78177C7.6318 5.74823 7.56318 5.73238 7.49419 5.73606H4.27614C4.21387 5.73493 4.15234 5.74965 4.09741 5.77882C4.04249 5.808 3.996 5.85066 3.96237 5.90273L0.0620418 11.8373C0.0219417 11.8989 0.000402877 11.9705 0 12.0438V23.6377C0 23.7338 0.0384455 23.8259 0.10687 23.8939C0.175294 23.9618 0.268091 24 0.364858 24H24.1645C24.2613 24 24.3541 23.9618 24.4225 23.8939C24.491 23.8259 24.5294 23.7338 24.5294 23.6377V13.0873C24.5292 13.015 24.5508 12.9444 24.5914 12.8844L26.9265 9.32291C26.9685 9.26761 26.9936 9.20152 26.9989 9.13247C27.0043 9.06342 26.9895 8.99431 26.9565 8.93332C26.9235 8.87232 26.8736 8.82199 26.8126 8.7883C26.7517 8.75461 26.6823 8.73898 26.6127 8.74323H23.3947C23.3318 8.74386 23.2701 8.75969 23.2147 8.78936C23.1594 8.81903 23.1122 8.86164 23.0772 8.9135L21.1545 11.8373C21.1109 11.904 21.0892 11.9825 21.0924 12.062Z" />
                <path d="M18.3889 0.000745744H15.1745C15.1112 -0.000626811 15.0487 0.0143457 14.9931 0.0441988C14.9374 0.0740518 14.8906 0.117749 14.857 0.17102L7.41759 11.5149C7.37535 11.5704 7.35013 11.6369 7.34493 11.7063C7.33972 11.7757 7.35476 11.8451 7.38824 11.9063C7.42172 11.9674 7.47223 12.0177 7.53373 12.0511C7.59522 12.0845 7.66511 12.0996 7.73502 12.0946H10.9531C11.0157 12.0953 11.0774 12.08 11.1324 12.0502C11.1874 12.0204 11.2336 11.977 11.2668 11.9243L18.7063 0.576803C18.7474 0.521232 18.7717 0.455164 18.7763 0.386361C18.7809 0.317558 18.7657 0.248871 18.7323 0.188379C18.699 0.127887 18.649 0.078102 18.5881 0.0448622C18.5273 0.0116223 18.4582 -0.00367456 18.3889 0.000745744Z" />
                <path d="M23.2086 3.6347H19.9906C19.928 3.63396 19.8662 3.64924 19.8113 3.67906C19.7563 3.70888 19.71 3.75226 19.6768 3.805L14.6199 11.5149C14.5779 11.5702 14.5528 11.6363 14.5474 11.7053C14.5421 11.7744 14.5569 11.8435 14.5899 11.9045C14.6229 11.9655 14.6728 12.0158 14.7337 12.0495C14.7947 12.0832 14.864 12.0989 14.9336 12.0946H18.1517C18.2143 12.0953 18.2761 12.0801 18.331 12.0502C18.386 12.0204 18.4323 11.977 18.4655 11.9243L23.5406 4.21078C23.5829 4.15422 23.6076 4.08665 23.6118 4.01636C23.616 3.94606 23.5995 3.87609 23.5642 3.81497C23.529 3.75386 23.4766 3.70427 23.4135 3.67225C23.3503 3.64023 23.2791 3.62718 23.2086 3.6347Z" />
              </svg>
            </span>
          </div>
          <div class="flex flex-wrap items-center justify-center gap-2">
            <span v-for="output in CLAUDE_OUTPUTS" :key="output.label"
                  class="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-2.5 py-1 font-mono text-[11px] text-surface-200">
              <component :is="output.icon" :size="12" :stroke-width="2" class="text-[#D97757]" />
              {{ output.label }}
            </span>
            <ArrowRight :size="14" :stroke-width="2" class="text-surface-500" />
            <span class="inline-flex items-center gap-1.5 rounded-full bg-[#28FEB4]/15 px-2.5 py-1 font-mono text-[11px] font-medium text-[#28FEB4]">
              <Check :size="12" :stroke-width="2.5" /> Deployed
            </span>
          </div>
        </div>
        <div class="p-6">
          <span class="inline-flex items-center gap-1.5 rounded-full border border-[#D97757]/30 bg-[#D97757]/10 px-2.5 py-0.5 text-xs font-medium text-[#b85a3c] dark:text-[#e8977a]">
            <img :src="claudeCodeIcon" alt="" class="h-3 w-3" /> Claude Code
          </span>
          <h2 class="mt-3 text-base font-semibold text-surface-950 dark:text-surface-0">Build it with Claude Code</h2>
          <p class="mt-1.5 text-sm leading-6 text-muted-color">
            The Kinotic plugin for Claude Code creates applications and projects for you, writes your entities,
            services and UIs in the project's repository, and every push deploys them.
          </p>
          <a :href="QUICK_START_URL" target="_blank" rel="noopener"
             class="mt-4 inline-flex items-center gap-1.5 rounded-md border border-surface-200 px-3 py-1.5 text-sm font-medium text-surface-800 transition-colors hover:bg-surface-100 dark:border-surface-700 dark:text-surface-100 dark:hover:bg-surface-800">
            Read the quick start
            <ArrowUpRight :size="14" :stroke-width="1.75" aria-hidden="true" />
          </a>
        </div>
      </aside>
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
      <h2 class="text-lg font-semibold text-surface-950 dark:text-surface-0">Before you start</h2>
      <div class="mt-5 grid gap-4 md:grid-cols-3">
        <a v-for="tool in PREREQUISITES" :key="tool.name" :href="tool.href" target="_blank" rel="noopener"
           class="group flex items-start gap-4 rounded-xl border border-surface-200 p-4 transition-colors hover:border-surface-300 hover:bg-surface-50 dark:border-surface-700 dark:hover:bg-surface-800">
          <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-surface-950 dark:ring-1 dark:ring-surface-700">
            <img v-if="tool.logo" :src="tool.logo" alt="" :class="['h-5 w-5', { invert: tool.invertLogo }]" />
            <component :is="tool.icon" v-else :size="20" :stroke-width="1.75" class="text-[#28FEB4]" aria-hidden="true" />
          </span>
          <span class="min-w-0">
            <span class="flex items-center gap-1 text-sm font-semibold text-surface-950 dark:text-surface-0">
              {{ tool.name }}
              <ArrowUpRight :size="13" :stroke-width="1.75" class="text-surface-400 transition-colors group-hover:text-surface-700" aria-hidden="true" />
            </span>
            <span class="mt-0.5 block text-xs leading-5 text-muted-color">{{ tool.why }}</span>
          </span>
        </a>
      </div>

      <h2 class="mt-8 text-lg font-semibold text-surface-950 dark:text-surface-0">Resources</h2>
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
import { computed, markRaw, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import { ArrowRight, ArrowUpRight, BookOpen, Boxes, Braces, ChartLine, Check, CloudUpload, Copy, Database, FileCode, FolderGit2, GitBranch,
         GitCommitHorizontal, Globe, Layers, Library, Package, Plus, Table, Terminal } from '@lucide/vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { GitHubAppInstallation, Project } from '@kinotic-ai/management-api'
import { createDebug, PageHeader, TINTS } from '@kinotic-ai/frontend-common'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { DOCUMENTATION_URL } from '@/util/externalLinks'
import githubLogo from '@/assets/github-icon.svg'
import claudeCodeIcon from '@/assets/claude-code.svg'

/**
 * The organization's onboarding checklist: link GitHub, create an application, create a project,
 * then build and deploy it with Claude Code. Each step is checked off from the organization's real
 * state, and the first one not done carries the actions that do it.
 */

const debug = createDebug('quickstart')

const QUICK_START_URL = 'https://kinotic.ai/apps/quick-start'
const PLUGIN_COMMANDS = ['/plugin marketplace add kinotic-ai/claude-plugin', '/plugin install kinotic@kinotic']
// How many projects the checklist reads to find the first one and whether any has deployed
const PROJECT_SAMPLE_SIZE = 20

const CARD_CLASS = 'rounded-2xl border border-surface-200 bg-surface-0 p-7 shadow-[0_1px_3px_rgba(0,0,0,0.04)] dark:border-surface-700 dark:bg-surface-800/30'

type FeatureId = 'data' | 'deploy' | 'observe'

const FEATURES: { id: FeatureId, title: string, icon: Component, bar: string, href: string, description: string }[] = [
  { id: 'data', title: 'Model your data', icon: markRaw(Table), bar: 'bg-purple-500', href: 'https://kinotic.ai/apps/persistence/overview',
    description: 'Define entities in TypeScript and get typed repositories, backing storage, and GraphQL and OpenAPI endpoints for them.' },
  { id: 'deploy', title: 'Deploy on every push', icon: markRaw(CloudUpload), bar: 'bg-sky-500', href: 'https://kinotic.ai/apps/deployment/push-to-deploy',
    description: 'Pushing to a project\'s default branch builds and deploys its microservices and publishes its UIs.' },
  { id: 'observe', title: 'See it running', icon: markRaw(ChartLine), bar: 'bg-green-500', href: 'https://kinotic.ai/apps/services/overview',
    description: 'Follow every deployment step by step, and read your services\' traces, metrics and logs.' }
]
const activeFeature = ref<FeatureId>('data')

// What the Claude Code illustration shows the plugin writing
const CLAUDE_OUTPUTS = [
  { label: 'entities', icon: markRaw(Database) },
  { label: 'services', icon: markRaw(Boxes) },
  { label: 'UIs', icon: markRaw(Globe) }
]

// Placeholders for the feature illustrations
const DATA_OUTPUTS = [
  { label: 'PersonRepository', icon: markRaw(Database), tint: TINTS.purple },
  { label: 'GraphQL API', icon: markRaw(Braces), tint: TINTS.red },
  { label: 'OpenAPI', icon: markRaw(FileCode), tint: TINTS.green }
]
const DEPLOY_STEPS = [
  { label: 'Sync', icon: markRaw(FolderGit2) },
  { label: 'Build', icon: markRaw(Package) },
  { label: 'Run', icon: markRaw(Boxes) },
  { label: 'Publish', icon: markRaw(Globe) }
]
const LOG_LINES = [
  { level: 'INFO', time: '12:04:31', text: 'PersonRepository.save 12ms' },
  { level: 'INFO', time: '12:04:32', text: 'GET /api/people 200 8ms' },
  { level: 'WARN', time: '12:04:35', text: 'slow query findAll 412ms' }
]

// A dark logo is inverted to show on the dark tile
const PREREQUISITES: { name: string, why: string, href: string, logo?: string, invertLogo?: boolean, icon?: Component }[] = [
  { name: 'GitHub', why: 'Holds your projects\' repositories; pushes to them deploy.', href: 'https://github.com', logo: githubLogo, invertLogo: true },
  { name: 'Claude Code', why: 'Builds your app with the Kinotic plugin.', href: 'https://claude.com/claude-code', logo: claudeCodeIcon },
  { name: 'Bun', why: 'Runs a project\'s scripts on your machine.', href: 'https://bun.sh', icon: markRaw(Layers) }
]

const RESOURCES = [
  { label: 'Quick start', href: QUICK_START_URL, icon: markRaw(BookOpen) },
  { label: 'Application structure', href: 'https://kinotic.ai/apps/application-structure/overview', icon: markRaw(Layers) },
  { label: 'CLI reference', href: 'https://kinotic.ai/apps/cli-reference', icon: markRaw(Terminal) },
  { label: 'All documentation', href: DOCUMENTATION_URL, icon: markRaw(Library) }
]

interface StepAction {
  label: string
  icon: Component
  primary: boolean
  run: () => void
}

interface Step {
  id: 'github' | 'application' | 'project' | 'build'
  title: string
  description: string
  done: boolean
  /** What is done, e.g. the linked account, shown beside a checked step. */
  doneDetail?: string
  actions: StepAction[]
  /** Where to see what a checked step made. */
  revisit?: { label: string, to: string }
}

const router = useRouter()

const loading = ref(true)
const linking = ref(false)
const error = ref<string | null>(null)
const copied = ref<string | null>(null)
const installation = ref<GitHubAppInstallation | null>(null)
const projects = ref<Project[]>([])
const deployedProject = ref<Project | null>(null)

const applications = computed(() => APPLICATION_STATE.allApplications)
const firstApplication = computed(() => applications.value[0] ?? null)
/** The project updated most recently, which the checklist names and links to. */
const latestProject = computed(() => [...projects.value]
    .sort((a, b) => new Date(b.updated ?? 0).getTime() - new Date(a.updated ?? 0).getTime())[0] ?? null)

function projectPath(project: Project): string {
  return `/application/${encodeURIComponent(project.applicationId)}/project/${encodeURIComponent(project.id ?? '')}`
}

const steps = computed<Step[]>(() => [
  {
    id: 'github',
    title: 'Link GitHub',
    description: 'Kinotic creates a repository for each of your projects under the account you link, and deploys a project when you push to it.',
    done: installation.value !== null,
    doneDetail: installation.value ? `linked as ${installation.value.accountLogin}` : undefined,
    actions: [{ label: 'Link GitHub', icon: markRaw(GitBranch), primary: true, run: linkGitHub }],
    revisit: { label: 'Integrations', to: '/organization-settings' }
  },
  {
    id: 'application',
    title: 'Create an application',
    description: 'An application groups the projects your organization builds and deploys, with its own users, data and settings.',
    done: applications.value.length > 0,
    doneDetail: `${applications.value.length} ${applications.value.length === 1 ? 'application' : 'applications'}`,
    actions: [{ label: 'New application', icon: markRaw(Plus), primary: true, run: () => router.push('/applications?add=true') }],
    revisit: { label: 'Applications', to: '/applications' }
  },
  {
    id: 'project',
    title: 'Create a project',
    description: 'A project gets its own GitHub repository, scaffolded for Kinotic, and deploys from it.',
    done: projects.value.length > 0,
    doneDetail: latestProject.value?.name,
    actions: [{
      label: 'New project', icon: markRaw(FolderGit2), primary: true,
      run: () => router.push(firstApplication.value
          ? `/application/${encodeURIComponent(firstApplication.value.id)}/projects?openNewProject=1`
          : '/applications?add=true')
    }],
    revisit: latestProject.value ? { label: 'Open project', to: projectPath(latestProject.value) } : undefined
  },
  {
    id: 'build',
    title: 'Build and deploy with Claude Code',
    description: 'Install the Kinotic plugin in Claude Code, open your project\'s repository, and build. Every push to its default branch deploys it.',
    done: deployedProject.value !== null,
    doneDetail: deployedProject.value ? `${deployedProject.value.name} deployed` : undefined,
    actions: [
      ...(latestProject.value?.repoFullName
          ? [{ label: 'Open repository', icon: markRaw(GitBranch), primary: true,
               run: () => window.open(`https://github.com/${latestProject.value?.repoFullName}`, '_blank', 'noopener') }]
          : []),
      { label: 'Read the quick start', icon: markRaw(ArrowUpRight), primary: !latestProject.value?.repoFullName,
        run: () => window.open(QUICK_START_URL, '_blank', 'noopener') }
    ],
    revisit: deployedProject.value ? { label: 'View deployment', to: `${projectPath(deployedProject.value)}/deployment` } : undefined
  }
])

const doneCount = computed(() => steps.value.filter(step => step.done).length)
const progress = computed(() => loading.value ? 0 : Math.round(doneCount.value / steps.value.length * 100))
// The first step not done is the one to work on now
const currentIndex = computed(() => steps.value.findIndex(step => !step.done))

function markerClass(step: Step, index: number): string {
  let ret: string
  if (step.done) {
    ret = 'bg-green-500 text-white'
  } else if (index === currentIndex.value) {
    ret = 'bg-surface-0 text-surface-950 ring-2 ring-surface-950 dark:bg-surface-900 dark:text-surface-0 dark:ring-surface-0'
  } else {
    ret = 'bg-surface-0 text-surface-400 ring-1 ring-surface-300 dark:bg-surface-900 dark:ring-surface-600'
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

async function copy(command: string): Promise<void> {
  await navigator.clipboard.writeText(command)
  copied.value = command
}

async function loadInstallation(): Promise<void> {
  try {
    installation.value = await Kinotic.githubAppInstallations.findForCurrentOrg()
  } catch (err) {
    debug('Failed to read the GitHub link: %O', err)
  }
}

// The organization's projects, read application by application until enough are found
async function loadProjects(): Promise<void> {
  if (APPLICATION_STATE.allApplications.length === 0) {
    await APPLICATION_STATE.loadAllApplications()
  }
  const found: Project[] = []
  for (const application of APPLICATION_STATE.allApplications) {
    if (found.length >= PROJECT_SAMPLE_SIZE) break
    const page = await Kinotic.projects.findAllForApplication(application.id, Pageable.create(0, PROJECT_SAMPLE_SIZE))
    found.push(...(page.content ?? []))
  }
  projects.value = found
}

async function loadDeployedProject(): Promise<void> {
  const deployments = await Promise.all(projects.value.map(project =>
      Kinotic.projects.findDeployment(project.id ?? '').then(deployment => ({ project, deployment })).catch(() => null)))
  deployedProject.value = deployments.find(entry => entry?.deployment)?.project ?? null
}

onMounted(async () => {
  try {
    await Promise.all([loadInstallation(), loadProjects()])
    await loadDeployedProject()
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
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

.claude-canvas {
  background-color: #0c0a09;
  background-image:
    radial-gradient(circle, rgba(255, 255, 255, 0.07) 1px, transparent 1.2px),
    radial-gradient(60% 70% at 25% 40%, rgba(217, 119, 87, 0.22), transparent 70%),
    radial-gradient(60% 70% at 80% 55%, rgba(40, 254, 180, 0.16), transparent 70%);
  background-size: 14px 14px, 100% 100%, 100% 100%;
}

/*
 * One relay on a shared 5s cycle: the orange light circles Claude Code and stops at its right edge,
 * which launches the light along the line; Kinotic's ring picks it up at its left edge and circles
 * Kinotic, then everything rests before the next lap.
 *
 * Every path has pathLength 100 and the dash covers [-offset, -offset + length]. A ring is a
 * 74×74 rect with rx 21 drawn clockwise from the end of its top-left corner: its perimeter is
 * 4·32 + 4·33 = 260, so the middle of the right side sits at 81/260 = 31.15 and the middle of
 * the left side at 211/260 = 81.15.
 */
.claude-canvas__orbit,
.claude-canvas__flow {
  animation-duration: 5s;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

/* the 16-unit light ends a full lap with its head (offset -15.15 + 16) on the right side */
.claude-canvas__orbit--claude {
  animation-name: claude-orbit;
}

@keyframes claude-orbit {
  0% { stroke-dashoffset: 184.85; opacity: 0; }
  3% { opacity: 1; }
  35% { stroke-dashoffset: 84.85; opacity: 1; }
  39%, 100% { stroke-dashoffset: 84.85; opacity: 0; }
}

/* the 22-unit light leaves Claude Code as the orange arrives, and reaches Kinotic at 55%; its
   200-unit gap is longer than the line, so no second light repeats onto it */
.claude-canvas__flow {
  animation-name: claude-flow;
}

@keyframes claude-flow {
  0%, 34% { stroke-dashoffset: 22; opacity: 0; }
  35% { stroke-dashoffset: 22; opacity: 1; }
  55% { stroke-dashoffset: -100; opacity: 1; }
  56%, 100% { stroke-dashoffset: -100; opacity: 0; }
}

/* the 16-unit light starts with its tail (offset -81.15) on the left side, where the line arrives */
.claude-canvas__orbit--kinotic {
  animation-name: kinotic-orbit;
}

@keyframes kinotic-orbit {
  0%, 54% { stroke-dashoffset: 18.85; opacity: 0; }
  55% { stroke-dashoffset: 18.85; opacity: 1; }
  88% { stroke-dashoffset: -81.15; opacity: 1; }
  92%, 100% { stroke-dashoffset: -81.15; opacity: 0; }
}

/* Claude Code's lids drop from the top of each eye; Kinotic's windows squash shut a beat later */
.claude-canvas__lid {
  transform-box: fill-box;
  transform-origin: top;
  transform: scaleY(0);
  animation: claude-lid 4s ease-in-out infinite;
}

.claude-canvas__kinotic-eyes {
  transform-box: fill-box;
  transform-origin: center;
  animation: kinotic-blink 4s ease-in-out 2s infinite;
}

@keyframes claude-lid {
  0%, 88%, 100% { transform: scaleY(0); }
  92% { transform: scaleY(1); }
}

@keyframes kinotic-blink {
  0%, 88%, 100% { transform: scaleY(1); }
  92% { transform: scaleY(0.12); }
}

@media (prefers-reduced-motion: reduce) {
  .claude-canvas__flow,
  .claude-canvas__orbit,
  .claude-canvas__lid,
  .claude-canvas__kinotic-eyes {
    animation: none;
  }
}
</style>
