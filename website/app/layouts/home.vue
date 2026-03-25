<template>
  <div class="min-h-screen flex flex-col bg-[#171717]">
    <Header />
    <main class="flex-1">
      <slot />
      <ModalsModal />
    </main>
    <Footer />
  </div>
</template>
