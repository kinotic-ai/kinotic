<script setup lang="ts">
import Toast from 'primevue/toast'
// Barrel side effect: applies the persisted dark/light mode before first paint.
import '@kinotic-ai/frontend-common'
</script>

<template>
    <main>
        <Toast />
        <router-view />
    </main>
</template>
