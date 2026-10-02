<script setup lang="ts">
import { computed, ref, watch } from 'vue';
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
import { FormDrawer } from '@kinotic-ai/frontend-common'
import CreationStatus from '@/components/CreationStatus.vue';
import { useCreationPhase } from '@/composables/useCreationPhase';

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

const { phase, create, holdReady } = useCreationPhase();
const created = ref<Project | null>(null);

// Each step reflects what the one create request has actually done so far
const setupSteps = computed(() => {
    const ready = phase.value === 'ready';
    return [
        { label: `Saving ${form.value.name.trim()}`, done: ready, active: !ready },
        { label: ready ? `Created ${created.value?.repoFullName}` : 'Creating its GitHub repository', done: ready, active: false },
        { label: 'Ready', done: ready, active: false }
    ];
});

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


async function handleSubmit(): Promise<void> {
    try {
        const app = APPLICATION_STATE.currentApplication;
        if (!app) throw new Error('No current application selected');

        const project = new Project(null, app.id, form.value.name.trim(), form.value.description);
        project.organizationId = USER_STATE.getOrganizationId();
        project.sourceOfTruth = ProjectType.TYPESCRIPT;
        project.repoPrivate = form.value.repoPrivate;

        // Goes through the server-side ProjectRepoProvisioner, which creates the
        // backing GitHub repo from the configured template and stamps the repo
        // metadata on the project before persisting. Fails if a project with the
        // derived id already exists. createSync so the list re-query the submit
        // handler fires sees the new project rather than a pre-refresh index.
        created.value = await create(() => Kinotic.projects.createSync(project));
        await holdReady();
        finish();
    } catch (error) {
        debug('Failed to create project: %O', error);
        const message = (error as Error)?.message ?? '';
        if (message.includes('GitHub is not linked')) {
            githubLinked.value = false;
        } else {
            showErrorToast(toast, 'Failed to create project', error);
        }
    }
}

// Hands the created project to the page once, whether the hold ran out or the user closed early
function finish(): void {
    const createdProject = created.value;
    if (createdProject) {
        resetForm();
        emit('submit', createdProject);
    }
}

function handleClose(): void {
    if (phase.value === 'ready') {
        finish();
    } else if (phase.value === 'form') {
        resetForm();
        emit('close');
    }
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
    phase.value = 'form';
    created.value = null;
}
</script>

<template>
    <FormDrawer
        :visible="visible"
        title="New project"
        description="Each project is backed by a GitHub repository created for it."
        @close="handleClose"
    >
        <!-- Linking flow: redirecting to GitHub -->
        <div v-if="linkingState === 'redirecting'" class="flex items-center gap-3 text-sm text-surface-700 dark:text-surface-200">
            <i class="pi pi-spin pi-spinner"></i>
            Redirecting to GitHub…
        </div>

        <!-- Linking flow: error -->
        <p v-else-if="linkingState === 'error'" class="text-sm text-red-600">{{ linkingError }}</p>

        <!-- GitHub-not-linked prompt -->
        <p v-else-if="githubLinked === false" class="text-sm text-surface-700 dark:text-surface-200">
            Projects are backed by a GitHub repository. Link your GitHub account to this organization
            before creating a project.
        </p>

        <!-- Loading the link-state probe -->
        <div v-else-if="githubLinked === null" class="flex items-center gap-3 text-sm text-surface-700 dark:text-surface-200">
            <i class="pi pi-spin pi-spinner"></i>
            Checking GitHub link…
        </div>

        <!-- Project form, then its creation progress -->
        <div v-else class="flex h-full flex-col">
            <form v-if="phase === 'form'" id="new-project-form" class="flex flex-col gap-5" @submit.prevent="handleSubmit">
                <div>
                    <label for="new-project-name" class="mb-2 block text-sm font-medium">Name</label>
                    <InputText id="new-project-name" v-model="form.name" placeholder="Project name" required class="w-full" autofocus />
                </div>

                <div>
                    <label for="new-project-description" class="mb-2 block text-sm font-medium">Description</label>
                    <Textarea id="new-project-description" v-model="form.description" rows="3" class="w-full" />
                    <p class="mt-1.5 text-[0.8125rem] text-surface-500 dark:text-surface-400">Optional.</p>
                </div>

                <div class="flex items-center justify-between gap-4 border-t border-surface-200 pt-5 dark:border-surface-800">
                    <div>
                        <label for="new-project-private" class="block text-sm font-medium">Private repository</label>
                        <p class="mt-1.5 text-[0.8125rem] text-surface-500 dark:text-surface-400">
                            Visibility of the GitHub repo created for this project.
                        </p>
                    </div>
                    <ToggleSwitch inputId="new-project-private" v-model="form.repoPrivate" />
                </div>
            </form>

            <CreationStatus
                :phase="phase"
                :name="form.name.trim()"
                hint="Name your project and Kinotic creates its GitHub repository."
                next-step="Next, connect it from Claude Code and build."
                :steps="setupSteps"
            />
        </div>

        <template #footer>
            <Button v-if="phase !== 'ready'" type="button" severity="secondary" variant="outlined" label="Cancel"
                    :disabled="phase === 'creating'" @click="handleClose" />
            <Button v-if="linkingState === 'error'" type="button" label="Try again" @click="linkGitHub" />
            <Button v-else-if="linkingState === 'idle' && githubLinked === false" type="button" label="Link GitHub" @click="linkGitHub" />
            <template v-else-if="linkingState === 'idle' && githubLinked === true">
                <Button v-if="phase === 'form'" type="submit" form="new-project-form"
                        :disabled="form.name.trim() === ''" label="Create project" />
                <Button v-else-if="phase === 'creating'" type="button" :loading="true" label="Creating…" />
                <Button v-else type="button" label="Done" @click="finish" />
            </template>
        </template>
    </FormDrawer>
</template>
