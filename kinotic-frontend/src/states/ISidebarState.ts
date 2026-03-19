import { ref, computed } from 'vue'

class SidebarState {
    private _collapsed = ref(false)

    readonly isCollapsed = computed(() => this._collapsed.value)

    toggle(): void {
        this._collapsed.value = !this._collapsed.value
    }

    collapse(): void {
        this._collapsed.value = true
    }

    expand(): void {
        this._collapsed.value = false
    }
}

export const SIDEBAR_STATE = new SidebarState()
