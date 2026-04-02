<script lang="ts">
import EntityList from "@/pages/EntityList.vue";
import EntityListOld from "@/components/EntityListOld.vue";
import { Component, Vue, Prop, Emit } from "vue-facing-decorator";
import { isDark as darkMode } from '@/composables/useTheme'

@Component({
    components: { EntityList, EntityListOld }
})
export default class StructureDataViewModal extends Vue {
    @Prop({ default: false }) readonly modelValue!: boolean;
    @Prop({ default: "Data View" }) readonly title!: string;
    @Prop({ default: () => ({}) }) readonly entityProps!: Record<string, unknown>;

    showNewVersion = true;

    get visible(): boolean {
        return this.modelValue;
    }

    set visible(val: boolean) {
        this.updateModelValue(val);
    }

    @Emit("update:modelValue")
    updateModelValue(val: boolean) {
        return val;
    }

    @Emit("open")
    emitOpen() {
        return true;
    }

    @Emit("close")
    emitClose() {
        return true;
    }

    mounted() {
        if (this.visible) this.emitOpen();
        window.addEventListener("keydown", this.onKeydown);
    }

    beforeUnmount() {
        window.removeEventListener("keydown", this.onKeydown);
    }

    onKeydown = (e: KeyboardEvent) => {
        if (e.key === "Escape" && this.visible) {
            this.onHide();
        }
    };

    onHide() {
        this.visible = false;
        this.emitClose();
    }

    toggleVersion() {
        this.showNewVersion = !this.showNewVersion;
    }

    get isDark() {
        return darkMode.value;
    }
}
</script>
<template>
    <div v-show="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
        <div :class="['relative h-screen w-full overflow-hidden overflow-y-scroll shadow-lg', isDark ? 'bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
            <div :class="['flex items-center justify-between border-b p-4', isDark ? 'border-surface-700' : 'border-surface-200']">
                <h3 :class="['text-xl font-semibold', isDark ? 'text-surface-0' : 'text-surface-900']">{{ title }}</h3>
                <div class="flex items-center gap-2">
                    <button 
                        @click="toggleVersion" 
                        :class="['rounded-lg px-3 py-1.5 text-sm font-medium transition-colors', isDark ? 'bg-surface-800 text-surface-200 hover:bg-surface-700 hover:text-surface-0' : 'bg-surface-100 text-surface-700 hover:bg-surface-200']"
                    >
                        {{ showNewVersion ? 'Old Version' : 'New Version' }}
                    </button>
                    <button @click="onHide" :class="['flex h-8 w-8 items-center justify-center rounded-lg text-sm', isDark ? 'text-surface-400 hover:bg-surface-800 hover:text-surface-0' : 'text-surface-400 hover:bg-surface-200 hover:text-surface-900']">
                        <svg class="w-3 h-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 14 14">
                            <path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m1 1 6 6m0 0 6 6M7 7l6-6M7 7l-6 6" />
                        </svg>
                    </button>
                </div>
            </div>
            <div :class="['h-full', isDark ? 'bg-surface-900' : 'bg-surface-0']">
                <EntityListOld v-if="!showNewVersion" v-bind="entityProps" />
                <EntityList v-else v-bind="entityProps" />
            </div>
        </div>
    </div>
</template>