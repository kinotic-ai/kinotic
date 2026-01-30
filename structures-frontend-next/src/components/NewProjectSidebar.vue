<script lang="ts">
import { Component, Vue, Prop } from 'vue-facing-decorator';
import { Structures, Project, ProjectType } from '@kinotic/structures-api';
import { APPLICATION_STATE } from '@/states/IApplicationState';

import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Button from 'primevue/button';
import Select from 'primevue/select';

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

    get languageOptions() {
        return [
            { label: 'TypeScript', value: ProjectType.TYPESCRIPT },
            { label: 'GraphQL', value: ProjectType.GRAPHQL }
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

            const createdProject = await Structures.getProjectService().create(project);

            this.$toast.add({
                severity: 'success',
                summary: 'Success',
                detail: 'Project successfully added',
                life: 3000
            });

            this.resetForm();
            this.$emit('submit', createdProject);
        } catch (error) {
            console.error('[NewProjectSidebar] Failed to create project:', error);
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
    <transition name="slide">
        <div
            v-if="visible"
            class="fixed inset-0 z-50 flex justify-end"
            @click.self="handleClose"
        >
            <div class="w-[400px] h-full bg-white shadow-xl overflow-y-auto">
                <div class="flex justify-between items-center border-b border-[#E6E7EB] p-4">
                    <div class="flex items-center gap-3">
                        <img src="@/assets/action-plus-icon.svg" />
                        <h2 class="text-lg font-semibold text-[#101010]">New Project</h2>
                    </div>
                    <Button
                        @click="handleClose"
                        text
                        rounded
                        class="p-2 hover:bg-gray-100 transition"
                    >
                        <img src="@/assets/close-icon.svg" class="w-4 h-4" />
                    </Button>
                </div>
                <form @submit.prevent="handleSubmit" class="flex flex-col justify-between h-[calc(100vh-100px)] p-4">
                    <div class="flex flex-col gap-5">
                        <div>
                            <label class="block text-sm font-semibold text-[#101010] mb-2">Name</label>
                            <InputText
                                v-model="form.name"
                                placeholder="Project name"
                                required
                                class="w-full"
                            />
                        </div>

                        <div>
                            <label class="block text-sm font-semibold text-[#101010] mb-2">Description</label>
                            <Textarea
                                v-model="form.description"
                                rows="3"
                                class="w-full"
                                placeholder="Optional description"
                            />
                        </div>

                        <div>
                            <label class="block text-sm font-semibold text-[#101010] mb-2">Source of truth</label>
                            <div class="p-1 bg-[#F4F5F9] rounded-xl w-full flex gap-2">
                                <Button
                                    type="button"
                                    @click="form.source = 'GUI'"
                                    class="w-1/2 text-sm font-bold py-[10px] rounded-lg transition"
                                    severity="secondary"
                                    :class="form.source === 'GUI' ? '!bg-white !text-[#101010]' : '!bg-transparent !text-[#5F6165]'"
                                >
                                    GUI
                                </Button>
                                <Button
                                    type="button"
                                    @click="form.source = 'Code'"
                                    class="w-1/2 text-sm font-bold py-[10px] rounded-lg transition"
                                    severity="secondary"
                                    :class="form.source === 'Code' ? '!bg-white !text-[#101010]' : '!bg-transparent !text-[#5F6165]'"
                                >
                                    Code
                                </Button>
                            </div>
                        </div>

                        <div v-if="form.source === 'Code'">
                            <label class="block text-sm font-semibold text-[#101010] mb-2">Language</label>
                            <Select
                                v-model="form.language"
                                :options="languageOptions"
                                optionLabel="label"
                                optionValue="value"
                                placeholder="Select language"
                                class="w-full p-2 border border-[#D2D3D9] rounded-lg text-sm text-[#101010]"
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
<style scoped>
.slide-enter-active,
.slide-leave-active {
    transition: transform 0.3s ease;
}
.slide-enter-from {
    transform: translateX(100%);
}
.slide-enter-to {
    transform: translateX(0%);
}
.slide-leave-from {
    transform: translateX(0%);
}
.slide-leave-to {
    transform: translateX(100%);
}
</style>
