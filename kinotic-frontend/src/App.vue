<script setup lang="ts">
import Toast from 'primevue/toast'
import '@/composables/useTheme'
</script>

<template>
    <main>
        <Toast />
        <router-view />
    </main>
</template>
