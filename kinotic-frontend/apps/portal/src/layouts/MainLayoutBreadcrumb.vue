<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import Breadcrumb from 'primevue/breadcrumb';
import type { MenuItem } from 'primevue/menuitem';

defineProps<{
  separatorIcon?: string;
}>();

const route = useRoute();
const icons = import.meta.glob('../assets/*.svg', { eager: true, import: 'default' });

const getIconUrl = (icon: string | undefined): string => {
  if (!icon) return ''
  const match = icons[`../assets/${icon}`]
  return typeof match === 'string' ? match : ''
}


const breadcrumbs = computed<MenuItem[]>(() => {
  const matched = route.matched.filter((r) => r.meta.label);
  if (!matched.length) return [];

  const breadcrumbItems: MenuItem[] = [];

matched.forEach((r, index) => {
  const isLast = index === matched.length - 1;
  breadcrumbItems.push({
    label: r.meta.label as string,
    to: !isLast ? r.path : undefined,
    icon: r.meta.icon as string | undefined,
  });
});


  return breadcrumbItems;
});

</script>

<template>
  <div class="w-full pt-6 px-[33px] flex justify-between items-center">
    <Breadcrumb :model="breadcrumbs" aria-label="Breadcrumb" class="!px-0">

      <template #item="{ item }">
        <div class="flex items-center gap-2">
          <img v-if="item.icon" :src="getIconUrl(item.icon)" class="filter-(--filter-home) h-5 w-5" />
          <img v-if="item.icon && item.label" src="@/assets/chevron-right.svg" class="filter-(--filter-home) h-4 w-4" />
          <router-link v-if="item.to" :to="item.to" class="text-slate-500 text-[14px]">
            {{ item.label }}
          </router-link>
          <span v-else class="text-slate-500 text-[14px]">{{ item.label }}</span>
        </div>
      </template>
    </Breadcrumb>
    <div class="flex justify-between items-center gap-16">
      <div class="text-slate-500 text-[14px] flex gap-4">
        <router-link to="#">FeedBack</router-link>
        <router-link to="#">Changelog</router-link>
        <router-link to="#">Help</router-link>
        <router-link to="#">Docs</router-link>
      </div>
      <div class="w-8 h-8 rounded-full border border-text-slate-500 flex justify-center items-center cursor-pointer">
        <i class="pi pi-bell"></i>
      </div>
    </div>
  </div>
</template>
