<template>
  <div class="k-marketing flex min-h-screen flex-col overflow-x-hidden bg-k-bg font-k-body text-k-text antialiased">
    <Header />
    <main class="flex-1">
      <slot />
    </main>
    <Footer />
  </div>
</template>
