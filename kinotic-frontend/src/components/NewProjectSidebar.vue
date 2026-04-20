<script lang="ts">
import { Component, Vue, Prop } from 'vue-facing-decorator';
import { Kinotic } from '@kinotic-ai/core';
import { Project, ProjectType } from '@kinotic-ai/os-api';
import { APPLICATION_STATE } from '@/states/IApplicationState';

import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Button from 'primevue/button';
import Select from 'primevue/select';
import { createDebug } from '@/util/debug';
import { isDark as darkMode } from '@/composables/useTheme'

const debug = createDebug('new-project-sidebar');

interface ProjectForm {
    name: string;
    description: string;
    source: 'GUI' | 'Code';
    language: ProjectType | null;
}

@Component({
    components: {
        InputText,
        Textarea,
        Button,
        Select
    }
})
export default class NewProjectSidebar extends Vue {
    @Prop({ required: true }) readonly visible!: boolean;

    form: ProjectForm = {
        name: '',
        description: '',
        source: 'GUI',
        language: null
    };

    loading = false;

    get isDark() {
        return darkMode.value;
    }

    get languageOptions() {
        return [
            { label: 'TypeScript', value: ProjectType.TYPESCRIPT },
            { label: 'GraphQL', value: ProjectType.GRAPHQL }
        ];
    }

    get inputClass() {
        return [
            'w-full !shadow-none',
            this.isDark
                ? 'border-surface-700 bg-surface-800 text-surface-0 placeholder:text-surface-400 focus:border-surface-600'
                : 'border-surface-300 bg-surface-0 text-surface-950 placeholder:text-surface-400'
        ];
    }

    async handleSubmit(): Promise<void> {
        this.loading = true;
        try {
            const app = APPLICATION_STATE.currentApplication;
            if (!app) throw new Error('No current application selected');

            const project = new Project(null, app.id, this.form.name, this.form.description);

            if (this.form.source === 'GUI') {
                project.sourceOfTruth = ProjectType.GRAPHICAL;
            } else if (this.form.source === 'Code') {
                if (this.form.language === null) {
                    throw new Error('Please select a language for Code projects.');
                }
                project.sourceOfTruth = this.form.language;
            }

            const createdProject = await Kinotic.projects.create(project);

            this.$toast.add({
                severity: 'success',
                summary: 'Success',
                detail: 'Project successfully added',
                life: 3000
            });

            this.resetForm();
            this.$emit('submit', createdProject);
        } catch (error) {
            debug('Failed to create project: %O', error);
            this.$toast.add({
                severity: 'error',
                summary: 'Error',
                detail: 'Failed to create project.',
                life: 3000
            });
        } finally {
            this.loading = false;
        }
    }

    handleClose(): void {
        this.resetForm();
        this.$emit('close');
    }

    private resetForm(): void {
        this.form = {
            name: '',
            description: '',
            source: 'GUI',
            language: null
        };
    }
}
</script>

<template>
    <transition
        enter-active-class="transition-transform duration-300 ease-out"
        enter-from-class="translate-x-full"
        enter-to-class="translate-x-0"
        leave-active-class="transition-transform duration-300 ease-in"
        leave-from-class="translate-x-0"
        leave-to-class="translate-x-full"
    >
        <div
            v-if="visible"
            class="fixed inset-0 z-50 flex justify-end"
            @click.self="handleClose"
        >
            <div :class="['h-full w-[400px] overflow-y-auto shadow-xl', isDark ? 'bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-950']">
                <div :class="['flex items-center justify-between border-b p-4', isDark ? 'border-surface-800' : 'border-surface-200']">
                    <div class="flex items-center gap-3">
                        <div :class="['flex h-[35px] w-[35px] shrink-0 items-center justify-center rounded-[8px]', isDark ? 'bg-surface-800' : 'bg-surface-100']">
                            <img src="@/assets/plus.svg" alt="Create project" class="h-6 w-6" />
                        </div>
                        <h2 :class="['text-lg font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">New Project</h2>
                    </div>
                    <Button
                        @click="handleClose"
                        text
                        rounded
                        :class="['p-2 transition', isDark ? 'hover:bg-surface-800' : 'hover:bg-surface-100']"
                    >
                        <img src="@/assets/close-icon.svg" class="w-4 h-4" />
                    </Button>
                </div>
                <form @submit.prevent="handleSubmit" class="flex flex-col justify-between h-[calc(100vh-100px)] p-4">
                    <div class="flex flex-col gap-5">
                        <div>
                            <label :class="['mb-2 block text-sm font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">Name</label>
                            <InputText
                                v-model="form.name"
                                placeholder="Project name"
                                required
                                :class="inputClass"
                            />
                        </div>

                        <div>
                            <label :class="['mb-2 block text-sm font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">Description</label>
                            <Textarea
                                v-model="form.description"
                                rows="3"
                                :class="inputClass"
                                placeholder="Optional description"
                            />
                        </div>

                        <div>
                            <label :class="['mb-2 block text-sm font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">Source of truth</label>
                            <div :class="['flex w-full gap-2 rounded-xl p-1', isDark ? 'bg-surface-800' : 'bg-surface-100']">
                                <Button
                                    type="button"
                                    @click="form.source = 'GUI'"
                                    class="w-1/2 text-sm font-bold py-[10px] rounded-lg transition"
                                    severity="secondary"
                                    :class="form.source === 'GUI' ? (isDark ? '!bg-surface-950 !text-surface-0' : '!bg-surface-0 !text-surface-950') : (isDark ? '!bg-transparent !text-surface-400' : '!bg-transparent !text-surface-600')"
                                >
                                    GUI
                                </Button>
                                <Button
                                    type="button"
                                    @click="form.source = 'Code'"
                                    class="w-1/2 text-sm font-bold py-[10px] rounded-lg transition"
                                    severity="secondary"
                                    :class="form.source === 'Code' ? (isDark ? '!bg-surface-950 !text-surface-0' : '!bg-surface-0 !text-surface-950') : (isDark ? '!bg-transparent !text-surface-400' : '!bg-transparent !text-surface-600')"
                                >
                                    Code
                                </Button>
                            </div>
                        </div>

                        <div v-if="form.source === 'Code'">
                            <label :class="['mb-2 block text-sm font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">Language</label>
                            <Select
                                v-model="form.language"
                                :options="languageOptions"
                                optionLabel="label"
                                optionValue="value"
                                placeholder="Select language"
                                :class="['w-full rounded-lg p-2 text-sm', isDark ? 'border border-surface-700 bg-surface-800 text-surface-0' : 'border border-surface-300 text-surface-950']"
                            />
                        </div>
                    </div>
                    <div class="flex justify-end gap-2 mt-6">
                        <Button type="button" @click="handleClose" severity="secondary">
                            Cancel
                        </Button>
                        <Button
                            type="submit"
                            :disabled="loading"
                            severity="primary"
                            class="px-[10px] py-[7px] flex items-center gap-2"
                        >
                            <i v-if="loading" class="pi pi-spin pi-spinner text-white text-sm"></i>
                            Create Project
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    </transition>
</template>
