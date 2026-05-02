<script lang="ts">
import { Component, Vue, Prop, Watch } from 'vue-facing-decorator';
import { Kinotic } from '@kinotic-ai/core';
import { Project, ProjectType } from '@kinotic-ai/os-api';
import { APPLICATION_STATE } from '@/states/IApplicationState';
import { USER_STATE } from '@/states/IUserState';

import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Button from 'primevue/button';
import { createDebug } from '@/util/debug';
import { isDark as darkMode } from '@/composables/useTheme'

const debug = createDebug('new-project-sidebar');

interface ProjectForm {
    name: string;
    description: string;
}

@Component({
    components: {
        InputText,
        Textarea,
        Button
    }
})
export default class NewProjectSidebar extends Vue {
    @Prop({ required: true }) readonly visible!: boolean;

    form: ProjectForm = {
        name: '',
        description: ''
    };

    loading = false;

    /** null = checking; false = no install (prompt to link); true = install present (show form). */
    githubLinked: boolean | null = null;

    @Watch('visible')
    async onVisibleChanged(isOpen: boolean): Promise<void> {
        if (!isOpen) return;
        this.githubLinked = null;
        try {
            const install = await Kinotic.githubAppInstallations.findForCurrentOrg();
            this.githubLinked = install != null;
        } catch (e) {
            debug('Failed to check GitHub link state: %O', e);
            // Treat lookup failure as "linked" — let the create attempt surface the real error
            // rather than blocking the user behind a noisy probe.
            this.githubLinked = true;
        }
    }

    get isDark() {
        return darkMode.value;
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
            project.organizationId = USER_STATE.getOrganizationId();
            project.sourceOfTruth = ProjectType.TYPESCRIPT;

            // Goes through the server-side ProjectRepoProvisioner, which creates the
            // backing GitHub repo from the configured template and stamps the repo
            // metadata on the project before persisting. Fails if a project with the
            // derived id already exists.
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
            const message = (error as Error)?.message ?? '';
            if (message.includes('GitHub is not linked')) {
                this.githubLinked = false;
            } else {
                this.$toast.add({
                    severity: 'error',
                    summary: 'Error',
                    detail: 'Failed to create project.',
                    life: 3000
                });
            }
        } finally {
            this.loading = false;
        }
    }

    handleClose(): void {
        this.resetForm();
        this.$emit('close');
    }

    goToGitHubSettings(): void {
        this.$emit('close');
        this.$router.push('/integrations/github');
    }

    private resetForm(): void {
        this.form = {
            name: '',
            description: ''
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

                <!-- GitHub-not-linked prompt: shown when the org has no GitHubAppInstallation. -->
                <div v-if="githubLinked === false" class="flex flex-col gap-4 p-4">
                    <p :class="['text-sm', isDark ? 'text-surface-200' : 'text-surface-700']">
                        Projects are backed by a GitHub repository. Link your GitHub account to this organization
                        before creating a project.
                    </p>
                    <div class="flex justify-end gap-2">
                        <Button type="button" @click="handleClose" severity="secondary">Cancel</Button>
                        <Button type="button" severity="primary" @click="goToGitHubSettings">
                            Link GitHub
                        </Button>
                    </div>
                </div>

                <!-- Loading the link-state probe -->
                <div v-else-if="githubLinked === null" class="flex items-center gap-2 p-4">
                    <i class="pi pi-spin pi-spinner"></i>
                    <span :class="['text-sm', isDark ? 'text-surface-200' : 'text-surface-700']">Checking GitHub link…</span>
                </div>

                <!-- Project form -->
                <form v-else @submit.prevent="handleSubmit" class="flex flex-col justify-between h-[calc(100vh-100px)] p-4">
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
