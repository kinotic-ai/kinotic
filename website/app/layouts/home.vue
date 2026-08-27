<template>
  <div class="k-marketing flex min-h-screen flex-col overflow-x-hidden bg-[#08090A] text-[#EDEDEF] antialiased [font-family:var(--k-font-body)]">
    <Header />
    <main class="flex-1">
      <slot />
    </main>
    <Footer />
  </div>
</template>
