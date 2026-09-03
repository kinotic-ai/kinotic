<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { showErrorToast } from '@kinotic-ai/frontend-common';
import { Kinotic } from '@kinotic-ai/core';
import { Project, ProjectType } from '@kinotic-ai/management-api';
import { APPLICATION_STATE } from '@/states/IApplicationState';
import { USER_STATE } from '@/states/IUserState';

import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Button from 'primevue/button';
import ToggleSwitch from 'primevue/toggleswitch';
import { createDebug } from '@kinotic-ai/frontend-common';
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

const debug = createDebug('new-project-sidebar');

interface ProjectForm {
    name: string;
    description: string;
    repoPrivate: boolean;
}

type LinkingState = 'idle' | 'redirecting' | 'error';

const props = defineProps<{
    visible: boolean
}>();

const emit = defineEmits<{
    (e: 'submit', project: Project): void
    (e: 'close'): void
}>();

const route = useRoute();
const toast = useToast();

const form = ref<ProjectForm>({
    name: '',
    description: '',
    repoPrivate: true
});

const loading = ref(false);

/** null = checking; false = no install (prompt to link); true = install present (show form). */
const githubLinked = ref<boolean | null>(null);

const linkingState = ref<LinkingState>('idle');
const linkingError = ref<string | null>(null);

watch(() => props.visible, onVisibleChanged);
async function onVisibleChanged(isOpen: boolean): Promise<void> {
    if (!isOpen) return;
    githubLinked.value = null;
    linkingState.value = 'idle';
    linkingError.value = null;
    try {
        const install = await Kinotic.githubAppInstallations.findForCurrentOrg();
        githubLinked.value = install != null;
    } catch (e) {
        debug('Failed to check GitHub link state: %O', e);
        // Treat lookup failure as "linked" — let the create attempt surface the real error
        // rather than blocking the user behind a noisy probe.
        githubLinked.value = true;
    }
}

const isDark = darkMode;

async function handleSubmit(): Promise<void> {
    loading.value = true;
    try {
        const app = APPLICATION_STATE.currentApplication;
        if (!app) throw new Error('No current application selected');

        const project = new Project(null, app.id, form.value.name, form.value.description);
        project.organizationId = USER_STATE.getOrganizationId();
        project.sourceOfTruth = ProjectType.TYPESCRIPT;
        project.repoPrivate = form.value.repoPrivate;

        // Goes through the server-side ProjectRepoProvisioner, which creates the
        // backing GitHub repo from the configured template and stamps the repo
        // metadata on the project before persisting. Fails if a project with the
        // derived id already exists. createSync so the list re-query the submit
        // handler fires sees the new project rather than a pre-refresh index.
        const createdProject = await Kinotic.projects.createSync(project);

        toast.add({
            severity: 'success',
            summary: 'Success',
            detail: 'Project successfully added',
            life: 3000
        });

        resetForm();
        emit('submit', createdProject);
    } catch (error) {
        debug('Failed to create project: %O', error);
        const message = (error as Error)?.message ?? '';
        if (message.includes('GitHub is not linked')) {
            githubLinked.value = false;
        } else {
            showErrorToast(toast, 'Failed to create project', error);
        }
    } finally {
        loading.value = false;
    }
}

function handleClose(): void {
    resetForm();
    emit('close');
}

/**
 * Sends the whole tab to GitHub's install page. GitHub redirects back to
 * {@code /github/install/callback}, which runs completeInstall and lands on the
 * returnTo — {@code ProjectList} re-opens this sidebar via {@code openNewProject=1}.
 *
 * A popup can't be used here: after the popup round-trips through github.com,
 * GitHub's Cross-Origin-Opener-Policy triggers a browsing-context-group swap that
 * severs window.opener, so the popup could never signal back to this window.
 */
async function linkGitHub(): Promise<void> {
    linkingState.value = 'redirecting';
    linkingError.value = null;
    try {
        const url = await Kinotic.githubAppInstallations.startInstall(buildReturnTo());
        window.location.href = url;
    } catch (err) {
        debug('Failed to start GitHub install: %O', err);
        linkingState.value = 'error';
        linkingError.value = (err as Error)?.message ?? 'Failed to start GitHub install.';
    }
}

/**
 * Builds the returnTo for the install round-trip: the current route plus
 * {@code openNewProject=1} so {@code ProjectList} re-opens the sidebar when
 * the same-window flow lands here. Existing query params are preserved.
 */
function buildReturnTo(): string {
    const fullPath = route.fullPath;
    const sep = fullPath.includes('?') ? '&' : '?';
    return `${fullPath}${sep}openNewProject=1`;
}

function resetForm(): void {
    form.value = {
        name: '',
        description: '',
        repoPrivate: true
    };
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

                <!-- Linking flow: redirecting to GitHub -->
                <div v-if="linkingState === 'redirecting'" class="flex items-center gap-3 p-4">
                    <i class="pi pi-spin pi-spinner text-lg"></i>
                    <span :class="['text-sm', isDark ? 'text-surface-200' : 'text-surface-700']">
                        Redirecting to GitHub…
                    </span>
                </div>

                <!-- Linking flow: error -->
                <div v-else-if="linkingState === 'error'" class="flex flex-col gap-4 p-4">
                    <p class="text-sm text-red-600">{{ linkingError }}</p>
                    <div class="flex justify-end gap-2">
                        <Button type="button" @click="handleClose" severity="secondary">Cancel</Button>
                        <Button type="button" @click="linkGitHub" severity="primary">Try again</Button>
                    </div>
                </div>

                <!-- GitHub-not-linked prompt -->
                <div v-else-if="githubLinked === false" class="flex flex-col gap-4 p-4">
                    <p :class="['text-sm', isDark ? 'text-surface-200' : 'text-surface-700']">
                        Projects are backed by a GitHub repository. Link your GitHub account to this organization
                        before creating a project.
                    </p>
                    <div class="flex justify-end gap-2">
                        <Button type="button" @click="handleClose" severity="secondary">Cancel</Button>
                        <Button type="button" severity="primary" @click="linkGitHub">
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
                                class="w-full"
                            />
                        </div>

                        <div>
                            <label :class="['mb-2 block text-sm font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">Description</label>
                            <Textarea
                                v-model="form.description"
                                rows="3"
                                class="w-full"
                                placeholder="Optional description"
                            />
                        </div>

                        <div class="flex items-center justify-between">
                            <div>
                                <label :class="['block text-sm font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">Private repository</label>
                                <p :class="['text-xs mt-1', isDark ? 'text-surface-400' : 'text-surface-500']">
                                    Visibility of the GitHub repo created for this project.
                                </p>
                            </div>
                            <ToggleSwitch v-model="form.repoPrivate" />
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
