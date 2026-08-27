<script setup lang="ts">
// The overscroll area past the top and bottom of the page is painted from the
// canvas background, which propagates from html/body rather than from this
// layout's own element — so the marketing pages have to claim it by name.
// Scoped to the class rather than set globally, to leave the docs layer's
// light mode alone.
useHead({ htmlAttrs: { class: 'k-canvas-dark' } })
</script>

<template>
  <div class="k-marketing flex min-h-screen flex-col overflow-x-hidden bg-k-bg font-k-body text-k-text antialiased">
    <Header />
    <main class="flex-1">
      <slot />
    </main>
    <Footer />
  </div>
</template>
