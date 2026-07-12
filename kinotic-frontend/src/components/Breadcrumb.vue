<template>
    <Breadcrumb :model="breadcrumbModel" class="py-2">
        <template #item="{ item }">
            <router-link :to="item.path" class="p-breadcrumb-item-link" @click.prevent="navigate(item)">
                <i v-if="item.icon" :class="item.icon" class="mr-2" />
                <span>{{ item.label }}</span>
            </router-link>
        </template>
    </Breadcrumb>
</template>

<script setup lang="ts">
import { StructuresStates } from '@/states/index.js'
import { computed } from 'vue'
import Breadcrumb from 'primevue/breadcrumb'
import type { MenuItem } from 'primevue/menuitem'
import { NavItem } from '@/components/NavItem'

const breadcrumbModel = computed<NavItem[]>(() => StructuresStates.getApplicationState().breadcrumbItems)

// The #item slot types its argument as PrimeVue's MenuItem, but breadcrumbModel
// only ever holds NavItems.
function navigate(item: MenuItem): void {
    (item as NavItem).navigate()
}
</script>

<style scoped>
.p-breadcrumb {
    background: none;
    border: none;
    padding: 0.5rem 1rem;
}

.p-breadcrumb-item-link {
    text-decoration: none;
    color: var(--surface-700);
}

.p-breadcrumb-item-link:hover {
    color: var(--primary-color);
}
</style>