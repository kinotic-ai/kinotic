<template>
  <div class="min-h-screen flex flex-col dark:bg-[#101010] bg-white">
    <Header />
    <main class="flex-1">
      <slot />
    </main>
    <Footer />
    <CookieConsent />
  </div>
</template>
