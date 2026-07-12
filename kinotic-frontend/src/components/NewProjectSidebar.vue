<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { showErrorToast } from '@/util/helpers';
import { Kinotic } from '@kinotic-ai/core';
import { Project, ProjectType } from '@kinotic-ai/os-api';
import { APPLICATION_STATE } from '@/states/IApplicationState';
import { USER_STATE } from '@/states/IUserState';

import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Button from 'primevue/button';
import ToggleSwitch from 'primevue/toggleswitch';
import { createDebug } from '@/util/debug';
import { isDark as darkMode } from '@/composables/useTheme'

const debug = createDebug('new-project-sidebar');

interface ProjectForm {
    name: string;
    description: string;
    repoPrivate: boolean;
}

type LinkingState = 'idle' | 'awaiting' | 'completing' | 'error';

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

const popup = ref<Window | null>(null);
const installListener = ref<((e: MessageEvent) => void) | null>(null);
const popupWatcher = ref<number | null>(null);

watch(() => props.visible, onVisibleChanged);
async function onVisibleChanged(isOpen: boolean): Promise<void> {
    if (!isOpen) {
        cleanupPopupListeners();
        return;
    }
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

onBeforeUnmount(() => {
    cleanupPopupListeners();
});

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
        // derived id already exists.
        const createdProject = await Kinotic.projects.create(project);

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
    cleanupPopupListeners();
    resetForm();
    emit('close');
}

/**
 * Opens GitHub's install page in a popup. The sidebar stays in the main window
 * showing an "awaiting install" panel. When the popup hits our /github/install/callback
 * route it posts back installation_id + state and closes itself; we then run
 * completeInstall and transition to the project form.
 *
 * The popup must be opened synchronously inside the click handler — any await
 * before window.open hands control back to the event loop and the browser stops
 * treating it as a user-initiated popup.
 */
function linkGitHub(): void {
    linkingError.value = null;

    const popupWindow = window.open('about:blank', 'kinotic-github-install', 'width=900,height=900');
    if (!popupWindow) {
        // Popup blocked — fall back to same-window navigation.
        linkGitHubSameWindow();
        return;
    }

    popup.value = popupWindow;
    linkingState.value = 'awaiting';

    // Resolve the GitHub URL asynchronously and aim the popup at it.
    Kinotic.githubAppInstallations.startInstall(buildReturnTo())
        .then(url => {
            if (popup.value && !popup.value.closed) {
                popup.value.location.href = url;
            }
        })
        .catch(err => {
            debug('Failed to start GitHub install: %O', err);
            if (popup.value && !popup.value.closed) {
                popup.value.close();
            }
            cleanupPopupListeners();
            linkingState.value = 'error';
            linkingError.value = (err as Error)?.message ?? 'Failed to start GitHub install.';
        });

    installListener.value = onInstallMessage;
    window.addEventListener('message', installListener.value);

    // Watch for the user closing the popup without finishing.
    popupWatcher.value = window.setInterval(() => {
        if (popup.value && popup.value.closed) {
            const wasAwaiting = linkingState.value === 'awaiting';
            cleanupPopupListeners();
            if (wasAwaiting) {
                linkingState.value = 'idle';
            }
        }
    }, 500);
}

async function linkGitHubSameWindow(): Promise<void> {
    try {
        const url = await Kinotic.githubAppInstallations.startInstall(buildReturnTo());
        window.location.href = url;
    } catch (err) {
        debug('Failed to start GitHub install (same-window fallback): %O', err);
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

async function onInstallMessage(event: MessageEvent): Promise<void> {
    if (event.origin !== window.location.origin) return;
    const data = event.data as { type?: string; installationId?: number; state?: string; message?: string } | undefined;
    if (!data?.type) return;

    if (data.type === 'kinotic-github-install-complete'
            && typeof data.installationId === 'number'
            && typeof data.state === 'string') {
        cleanupPopupListeners();
        linkingState.value = 'completing';
        try {
            await Kinotic.githubAppInstallations.completeInstall(data.installationId, data.state);
            const install = await Kinotic.githubAppInstallations.findForCurrentOrg();
            githubLinked.value = install != null;
            linkingState.value = 'idle';
        } catch (err) {
            debug('Failed to complete GitHub install: %O', err);
            linkingState.value = 'error';
            linkingError.value = (err as Error)?.message ?? 'Failed to complete GitHub install.';
        }
    } else if (data.type === 'kinotic-github-install-error') {
        cleanupPopupListeners();
        linkingState.value = 'error';
        linkingError.value = data.message ?? 'GitHub install was cancelled or failed.';
    }
}

function cleanupPopupListeners(): void {
    if (installListener.value) {
        window.removeEventListener('message', installListener.value);
        installListener.value = null;
    }
    if (popupWatcher.value !== null) {
        window.clearInterval(popupWatcher.value);
        popupWatcher.value = null;
    }
    popup.value = null;
}

function cancelLinking(): void {
    if (popup.value && !popup.value.closed) {
        popup.value.close();
    }
    cleanupPopupListeners();
    linkingState.value = 'idle';
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

                <!-- Linking flow: awaiting popup -->
                <div v-if="linkingState === 'awaiting'" class="flex flex-col gap-4 p-4">
                    <div class="flex items-center gap-3">
                        <i class="pi pi-spin pi-spinner text-lg"></i>
                        <p :class="['text-sm', isDark ? 'text-surface-200' : 'text-surface-700']">
                            Complete the GitHub install in the popup window.
                        </p>
                    </div>
                    <p :class="['text-xs', isDark ? 'text-surface-400' : 'text-surface-500']">
                        Don't see it? Your browser may have blocked the popup.
                        <a class="underline cursor-pointer" @click="linkGitHubSameWindow">Continue in this window</a>.
                    </p>
                    <div class="flex justify-end">
                        <Button type="button" @click="cancelLinking" severity="secondary">Cancel</Button>
                    </div>
                </div>

                <!-- Linking flow: completing (popup posted back, calling completeInstall) -->
                <div v-else-if="linkingState === 'completing'" class="flex items-center gap-3 p-4">
                    <i class="pi pi-spin pi-spinner text-lg"></i>
                    <span :class="['text-sm', isDark ? 'text-surface-200' : 'text-surface-700']">
                        Finishing GitHub install…
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
