<script setup lang="ts">
import EntityList from "@/pages/EntityList.vue";
import EntityListOld from "@/components/EntityListOld.vue";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { isDark as darkMode } from '@/composables/useTheme'

const props = withDefaults(defineProps<{
    modelValue?: boolean;
    title?: string;
    entityProps?: Record<string, unknown>;
}>(), {
    modelValue: false,
    title: "Data View",
    entityProps: () => ({}),
});

const emit = defineEmits<{
    (e: "update:modelValue", value: boolean): void;
    (e: "open", value: boolean): void;
    (e: "close", value: boolean): void;
}>();

const showNewVersion = ref(true);

const visible = computed({
    get: (): boolean => props.modelValue,
    set: (val: boolean) => {
        updateModelValue(val);
    },
});

function updateModelValue(val: boolean) {
    emit("update:modelValue", val);
}

function emitOpen() {
    emit("open", true);
}

function emitClose() {
    emit("close", true);
}

onMounted(() => {
    if (visible.value) emitOpen();
    window.addEventListener("keydown", onKeydown);
});

onBeforeUnmount(() => {
    window.removeEventListener("keydown", onKeydown);
});

const onKeydown = (e: KeyboardEvent) => {
    if (e.key === "Escape" && visible.value) {
        onHide();
    }
};

function onHide() {
    visible.value = false;
    emitClose();
}

function toggleVersion() {
    showNewVersion.value = !showNewVersion.value;
}

const isDark = darkMode;
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