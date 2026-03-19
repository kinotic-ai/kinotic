<template>
    <ul class="h-full">
        <template v-for="(item) of navItems" :key="index">
            <li class="last:mt-auto">
                <div :class="[
                    'z-30 relative flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer',
                    isParentNavSelected(item) ? 'text-white/80 border-none bg-[#2D2F39]' : 'border-transparent text-white/80 hover:border-none hover:bg-[#2D2F39] text-white/50',
                    collapsed ? 'justify-center' : ''
                ]" @click="navClicked(item)" v-tooltip.right="{
                    value: collapsed ? item.label : null, pt: {
                        text: '!bg-[#2D2F39] !text-primary-contrast !font-medium'
                    }
                }">
                    <img :src="getIconUrl(item.icon)" class="w-5 h-5" />
                    <span v-if="!collapsed" class="flex-1 font-medium">{{ item.label }}</span>
                    <i v-if="!collapsed && item?.children?.length !== 0"
                        class="pi pi-chevron-down text-sm leading-none" />
                </div>

                <div v-if="isParentNavSelected(item) && item.children.length && !collapsed"
                    class="relative pl-1.5 flex flex-col transition-all duration-500 mt-2"
                    :class="isParentNavSelected(item) ? 'opacity-100' : 'opacity-0'">

                    <template v-for="(subItem, index) of item.children" :key="index">
                        <div class="cursor-pointer relative px-3.5 py-2 flex items-center transition-all"
                            @click="navClicked(subItem)">
                            <svg width="18" height="44" viewBox="0 0 18 22" fill="none"
                                xmlns="http://www.w3.org/2000/svg" class="absolute left-4 -top-2">
                                <path :d="getLinePath(index, item.children.length)" class="stroke-[#2D2F39]"
                                    stroke-width="2" />
                                <path
                                    d="M11.136 26.2862L11.1313 26.2863C11.1243 26.2863 11.1174 26.2849 11.1109 26.2823C11.1045 26.2796 11.0986 26.2756 11.0937 26.2707L11.0937 26.2707L11.0917 26.2687C11.0805 26.2575 11.0742 26.2422 11.0742 26.2263C11.0742 26.2105 11.0805 26.1953 11.0917 26.1841C11.0917 26.184 11.0917 26.184 11.0917 26.184L14.4286 22.8471L14.7822 22.4936L14.4286 22.14L11.1009 18.8123C11.0922 18.8014 11.0875 18.7878 11.0877 18.7737C11.088 18.7582 11.0943 18.7434 11.1052 18.7324C11.1162 18.7214 11.131 18.7151 11.1466 18.7149C11.1606 18.7146 11.1743 18.7193 11.1852 18.7281L14.9083 22.4512C14.9195 22.4625 14.9258 22.4777 14.9258 22.4936C14.9258 22.5095 14.9195 22.5247 14.9083 22.5359L11.1758 26.2685L11.1758 26.2685L11.1736 26.2707C11.1687 26.2756 11.1628 26.2796 11.1564 26.2823C11.1499 26.2849 11.143 26.2863 11.136 26.2862Z"
                                    class="stroke-[#2D2F39] fill-surface-600" />
                                <path d="M1 14V17.5C1 20.2614 3.23858 22.5 6 22.5H15" class="stroke-[#2D2F39]"
                                    stroke-width="2" />
                                <template v-if="index === getSelectedIndex()">
                                    <path
                                        d="M11.136 26.2862L11.1313 26.2863C11.1243 26.2863 11.1174 26.2849 11.1109 26.2823C11.1045 26.2796 11.0986 26.2756 11.0937 26.2707L11.0937 26.2707L11.0917 26.2687C11.0805 26.2575 11.0742 26.2422 11.0742 26.2263C11.0742 26.2105 11.0805 26.1953 11.0917 26.1841C11.0917 26.184 11.0917 26.184 11.0917 26.184L14.4286 22.8471L14.7822 22.4936L14.4286 22.14L11.1009 18.8123C11.0922 18.8014 11.0875 18.7878 11.0877 18.7737C11.088 18.7582 11.0943 18.7434 11.1052 18.7324C11.1162 18.7214 11.131 18.7151 11.1466 18.7149C11.1606 18.7146 11.1743 18.7193 11.1852 18.7281L14.9083 22.4512C14.9195 22.4625 14.9258 22.4777 14.9258 22.4936C14.9258 22.5095 14.9195 22.5247 14.9083 22.5359L11.1758 26.2685L11.1758 26.2685L11.1736 26.2707C11.1687 26.2756 11.1628 26.2796 11.1564 26.2823C11.1499 26.2849 11.143 26.2863 11.136 26.2862Z"
                                        class="stroke-[#2D2F39] fill-surface-0" />
                                    <path d="M1 14V17.5C1 20.2614 3.23858 22.5 6 22.5H15" class="stroke-[#2D2F39]"
                                        stroke-width="2" />
                                </template>
                                <path v-if="index <= getSelectedIndex()"
                                    :d="getActiveLinePath(index, getSelectedIndex())" class="stroke-[#2D2F39]"
                                    stroke-width="2" />
                            </svg>
                            <p class="leading-relaxed font-medium text-sm transition-all rounded-lg ml-3 w-full px-3 py-2"
                                :class="[
                                    selectedNav?.label === subItem.label
                                        ? 'bg-[#2D2F39] text-white/80'
                                        : 'text-white/50 hover:text-white/80 hover:bg-[#2D2F39]'
                                ]">
                                {{ subItem.label }}
                            </p>
                        </div>
                    </template>
                </div>
            </li>
        </template>
    </ul>
</template>
<script lang="ts">
import { Vue, Component, Prop } from 'vue-facing-decorator'
import Tooltip from 'primevue/tooltip'
import type { NavItem } from '@/components/NavItem.js'

@Component({
    directives: {
        tooltip: Tooltip
    }
})
export default class SideNav extends Vue {
    @Prop({ required: true }) navItems!: NavItem[]
    @Prop({ default: false }) collapsed!: boolean

    selectedNav: NavItem | null = null

    async navClicked(item: NavItem) {
        this.selectedNav = item
        await item.navigate?.()
    }

    getIconUrl(icon: string): string {
        return new URL(`../assets/${icon}`, import.meta.url).href
    }

    mounted() {
        if (this.navItems && this.navItems.length > 0) {
            this.selectedNav = this.$route.meta as unknown as NavItem
        }
    }
    isParentNavSelected(item: NavItem) {
        return this.selectedNav?.label === item.label || this.selectedNav?.parent?.label === item.label
    }


    getLinePath(index: number, totalItems: number) {
        if (index === 0) {
            return `M1 0 V40`;
        } else if (index === totalItems - 1) {
            return `M1 -4 V14`;
        } else {
            return `M1 -4 V40`;
        }
    }

    getActiveLinePath(index: number, selectedIndex: number) {
        if (index === 0) {
            return `M1 0 V${index === selectedIndex ? '14' : '40'}`;
        } else if (index === selectedIndex) {
            return `M1 -4 V14`;
        } else {
            return `M1 -4 V40`;
        }
    }

    getSelectedIndex() {
        const children = this.selectedNav?.parent?.children
        if (children) {
            return children.findIndex((subItem) => subItem.label === this.selectedNav?.label)
        } else {
            return -1
        }
    }
}
</script>