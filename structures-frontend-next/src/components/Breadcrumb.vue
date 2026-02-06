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

<script lang="ts">
import { StructuresStates } from '@/states/index.js'
import { Vue, Component } from 'vue-facing-decorator'
import Breadcrumb from 'primevue/breadcrumb'
import { NavItem } from '@/components/NavItem'
@Component({
    components: {
        Breadcrumb
    }
})
export default class AppBreadcrumb extends Vue {
    get breadcrumbModel(): NavItem[] {
        return StructuresStates.getApplicationState().breadcrumbItems
    }

    navigate(item: NavItem): void {
        item.navigate()
    }
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