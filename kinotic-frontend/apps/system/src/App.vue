<script setup lang="ts">
import Toast from 'primevue/toast'
// Importing the barrel also applies the persisted dark/light mode before first paint.
import { ConnectionOverlay } from '@kinotic-ai/frontend-common'
</script>

<template>
    <main>
        <Toast />
        <ConnectionOverlay />
        <router-view />
    </main>
</template>
