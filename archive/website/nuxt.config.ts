import Aura from '@primeuix/themes/aura';

export default defineNuxtConfig({
  ssr: true,
  compatibilityDate: '2025-05-15',
  devtools: { enabled: true },
  modules: ['@nuxtjs/color-mode', '@primevue/nuxt-module', '@nuxtjs/tailwindcss'],
  css: [
    '~/assets/css/main.css',
    'primeicons/primeicons.css',
  ],
  colorMode: {
    classSuffix: '',
    preference: 'dark',
    fallback: 'light',
    storageKey: 'nuxt-color-mode',
  },
  primevue: {
    options: {
      theme: {
        preset: Aura,
        options: {
          prefix: 'p',
          darkModeSelector: '.dark',
          cssLayer: {
            name: 'primevue',
            order: 'tailwind-base, primevue, tailwind-utilities',
          },
        },
      },
    },
  },
  build: {
    transpile: ['primevue'],
  },
  app: {
    baseURL: '/website/',
  },
})