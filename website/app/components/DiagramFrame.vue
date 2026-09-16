<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

// A click fills the screen with the diagram. The Fullscreen API takes the whole display where
// the browser offers it for an element; where it does not (iOS Safari), a fixed overlay covers
// the viewport instead. The diagram never moves in the DOM either way, so the marker ids and
// the theme palette its own stylesheet carries keep resolving.
const frame = ref<HTMLElement | null>(null)
const expanded = ref(false)
let overlay = false

function lockScroll(lock: boolean) {
  document.documentElement.style.overflow = lock ? 'hidden' : ''
}

function teardown() {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.removeEventListener('keydown', onKeydown)
  if (overlay) {
    lockScroll(false)
    overlay = false
  }
}

// Fullscreen mode leaves on Escape, the browser's own exit gesture, or exitFullscreen(); all
// three arrive here, so this is the one place fullscreen state is read back
function onFullscreenChange() {
  expanded.value = document.fullscreenElement === frame.value
  if (!expanded.value) {
    teardown()
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close()
  }
}

async function open() {
  const element = frame.value
  if (expanded.value || !element) {
    return
  }
  if (element.requestFullscreen) {
    document.addEventListener('fullscreenchange', onFullscreenChange)
    try {
      await element.requestFullscreen()
      return
    } catch {
      // Refused (a browser policy, or a display that cannot go fullscreen): fall through to the overlay
      document.removeEventListener('fullscreenchange', onFullscreenChange)
    }
  }
  overlay = true
  expanded.value = true
  lockScroll(true)
  window.addEventListener('keydown', onKeydown)
}

function close() {
  if (!expanded.value) {
    return
  }
  if (document.fullscreenElement === frame.value) {
    document.exitFullscreen()
  } else {
    expanded.value = false
    teardown()
  }
}

function toggle() {
  if (expanded.value) {
    close()
  } else {
    open()
  }
}

// Expanded, the backdrop closes and the drawing itself does not, so a click while reading
// stays put
function onStageClick(event: MouseEvent) {
  if (!expanded.value) {
    open()
  } else if (!(event.target as Element).closest('svg')) {
    close()
  }
}

onBeforeUnmount(() => {
  if (expanded.value) {
    close()
  }
  teardown()
})
</script>

<template>
  <div ref="frame" class="diagram-frame" :class="{ 'is-expanded': expanded }">
    <div
      class="diagram-frame__stage"
      role="button"
      tabindex="0"
      :aria-label="expanded ? 'Close the expanded diagram' : 'Expand the diagram'"
      @click="onStageClick"
      @keydown.enter.prevent="toggle"
      @keydown.space.prevent="toggle"
    >
      <slot />
    </div>
    <button v-if="expanded" class="diagram-frame__close" type="button" aria-label="Close" @click.stop="close">
      <UIcon name="i-lucide-x" />
    </button>
    <span v-else class="diagram-frame__hint" aria-hidden="true">
      <UIcon name="i-lucide-maximize-2" />
    </span>
  </div>
</template>

<style>
.diagram-frame {
  position: relative;
  margin: 1.5rem 0;
}

.diagram-frame__stage {
  cursor: zoom-in;
  outline: none;
  border-radius: 6px;
}

.diagram-frame__stage:focus-visible {
  outline: 2px solid var(--ui-primary);
  outline-offset: 4px;
}

.diagram-frame__hint {
  position: absolute;
  top: 0.5rem;
  right: 0.75rem;
  display: inline-flex;
  padding: 0.35rem;
  border-radius: 6px;
  background: var(--ui-bg-elevated);
  color: var(--ui-text-muted);
  font-size: 1rem;
  line-height: 1;
  opacity: 0;
  transition: opacity 0.15s;
  pointer-events: none;
}

.diagram-frame:hover .diagram-frame__hint,
.diagram-frame__stage:focus-visible ~ .diagram-frame__hint {
  opacity: 1;
}

/* Expanded: one set of rules for the Fullscreen API and for the overlay fallback */
.diagram-frame.is-expanded {
  position: fixed;
  inset: 0;
  z-index: 1000;
  margin: 0;
  background: var(--ui-bg);
}

.diagram-frame:fullscreen::backdrop {
  background: var(--ui-bg);
}

.diagram-frame.is-expanded .diagram-frame__stage {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 2.5rem;
  box-sizing: border-box;
  border-radius: 0;
  cursor: zoom-out;
}

/* The slotted wrapper and its drawing fill the stage; the viewBox letterboxes the drawing in it */
.diagram-frame.is-expanded .diagram-frame__stage > * {
  width: 100%;
  height: 100%;
  margin: 0;
  overflow: hidden;
}

.diagram-frame.is-expanded .diagram-frame__stage svg {
  min-width: 0;
  width: 100%;
  height: 100%;
  cursor: default;
}

.diagram-frame__close {
  position: absolute;
  top: 1rem;
  right: 1rem;
  z-index: 1;
  display: inline-flex;
  padding: 0.5rem;
  border: 1px solid var(--ui-border);
  border-radius: 999px;
  background: var(--ui-bg-elevated);
  color: var(--ui-text);
  font-size: 1.25rem;
  line-height: 1;
  cursor: pointer;
}

.diagram-frame__close:hover {
  background: var(--ui-bg-accented);
}
</style>
