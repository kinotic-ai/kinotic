<template>
  <div ref="headerRef" class="sticky top-0 left-0 z-50 flex h-16 items-center justify-between border-b border-surface-800 bg-surface-950 px-4 md:px-6">
    <div class="relative flex min-w-0 items-center gap-3 text-white">
      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-surface-300 transition-colors hover:text-surface-0 md:hidden"
        aria-label="Open navigation"
        @click="emit('toggle-nav')"
      >
        <span class="pi pi-bars"></span>
      </button>

      <RouterLink to="/applications" class="flex items-center gap-2">
        <img src="@/assets/header-logo.svg" class="h-6 w-[27px]" alt="Kinotic" />
      </RouterLink>

      <!-- On small screens only the deepest segment stays; the sidebar's back row names the rest -->
      <span :class="['text-lg text-surface-600', applicationId ? 'hidden md:inline' : '']">/</span>
      <RouterLink to="/applications"
        :class="['items-center gap-1.5 text-sm font-medium text-surface-300 transition-opacity hover:opacity-80', applicationId ? 'hidden md:flex' : 'flex']">
        {{ organizationId }}
        <span class="text-[11px] font-normal text-surface-500">org</span>
      </RouterLink>

      <template v-if="applicationId">
        <span :class="['text-lg text-surface-600', projectId ? 'hidden md:inline' : '']">/</span>

        <div ref="appDropdownRef" :class="['relative', projectId ? 'hidden md:inline-block' : 'inline-block']">
          <button @click="toggleAppDropdown"
            class="flex w-full items-center justify-between gap-2 text-sm font-medium text-surface-300 transition-opacity hover:opacity-80">
            {{ applicationId }}
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          <div v-if="appDropdownOpen"
            :class="[
              'absolute top-full left-0 z-50 mt-2 max-h-80 w-64 overflow-y-auto rounded-xl border p-2 shadow-lg',
              isDark ? 'border-surface-800 bg-surface-900' : 'border-surface-200 bg-surface-0'
            ]">
            <div class="w-full mb-2">
              <IconField class="w-full">
                <InputIcon class="pi pi-search" />
                <InputText v-model="searchTextApp" placeholder="Search applications" class="w-full" />
              </IconField>
            </div>
            <div v-for="app in filteredApplications" :key="app.id" @click="selectApp(app)"
              :class="[
                'flex cursor-pointer items-center justify-between rounded-lg px-4 py-2 text-sm',
                applicationId === app.id
                  ? 'bg-primary-50 text-primary-600 font-medium'
                  : isDark ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-950 hover:bg-surface-100'
              ]">
              <span>{{ app.id }}</span>
              <i v-if="applicationId === app.id" class="pi pi-check text-primary-500"></i>
            </div>
          </div>
        </div>
      </template>

      <template v-if="applicationId && projectId">
        <span class="text-lg text-surface-600">/</span>
        <div ref="projectDropdownRef" class="relative inline-block">
          <button @click="toggleProjectDropdown"
            class="flex w-full items-center justify-between gap-2 text-sm font-medium text-surface-300 transition-opacity hover:opacity-80">
            {{ currentProjectName }}
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          <div v-if="projectDropdownOpen"
            :class="[
              'absolute top-full left-0 z-50 mt-2 max-h-80 w-64 overflow-y-auto rounded-xl border p-2 shadow-lg',
              isDark ? 'border-surface-800 bg-surface-900' : 'border-surface-200 bg-surface-0'
            ]">
            <div class="w-full mb-2">
              <IconField class="w-full">
                <InputIcon class="pi pi-search" />
                <InputText v-model="searchTextProject" placeholder="Search projects" class="w-full" />
              </IconField>
            </div>
            <div v-for="proj in filteredProjects" :key="proj.id ?? ''" @click="selectProject(proj)"
              :class="[
                'flex cursor-pointer items-center justify-between rounded-lg px-4 py-2 text-sm',
                projectId === proj.id
                  ? 'bg-primary-50 text-primary-600 font-medium'
                  : isDark ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-950 hover:bg-surface-100'
              ]">
              <span>{{ proj.name }}</span>
              <i v-if="projectId === proj.id" class="pi pi-check text-primary-500"></i>
            </div>
          </div>
        </div>
      </template>
    </div>

    <div class="flex items-center gap-3">
      <button
        type="button"
        class="flex h-9 w-9 items-center justify-center rounded-full border border-surface-800 bg-transparent text-surface-400 transition-colors hover:border-surface-700 hover:text-surface-0"
        :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
        @click="toggleTheme"
      >
        <span :class="isDark ? 'pi pi-sun' : 'pi pi-moon'"></span>
      </button>

      <div ref="avatarDropdownRef" class="relative">
        <button @click="toggleAvatarDropdown" class="flex items-center">
          <Avatar :label="PROFILE_STATE.initials" shape="circle" class="cursor-pointer hover:opacity-80" />
        </button>

        <div v-if="avatarDropdownOpen"
          :class="[
            'absolute top-full right-0 z-50 mt-2 w-48 rounded-xl border shadow-lg',
            isDark ? 'border-surface-800 bg-surface-900' : 'border-surface-200 bg-surface-0'
          ]">
          <div class="py-1">
            <RouterLink to="/account/profile" :class="avatarMenuItemClass" @click="avatarDropdownOpen = false">
              <i class="pi pi-user mr-2"></i>
              Profile
            </RouterLink>
            <RouterLink to="/account/connected-apps" :class="avatarMenuItemClass" @click="avatarDropdownOpen = false">
              <i class="pi pi-link mr-2"></i>
              Connected apps
            </RouterLink>
            <div :class="['my-1 border-t', isDark ? 'border-surface-800' : 'border-surface-200']"></div>
            <button @click="handleLogout" :class="avatarMenuItemClass">
              <i class="pi pi-sign-out mr-2"></i>
              Logout
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { APPLICATION_STATE } from '@/states/IApplicationState';
import { PROFILE_STATE } from '@/states/IProfileState';
import { USER_STATE } from '@/states/IUserState';
import { Kinotic, Pageable } from '@kinotic-ai/core';
import type { Application, Project } from '@kinotic-ai/management-api';
import Avatar from 'primevue/avatar';
import InputText from 'primevue/inputtext';
import IconField from 'primevue/iconfield';
import InputIcon from 'primevue/inputicon';
import { createDebug, isDark as darkMode, toggleDark } from '@kinotic-ai/frontend-common'

const debug = createDebug('header');

const emit = defineEmits<{
  (e: 'toggle-nav'): void
}>()

/**
 * The breadcrumb across the top: organization / application / project, as deep as the
 * current route goes. The application and project segments are switchers; switching keeps
 * the page within the new scope where it exists there.
 */
const route = useRoute();
const router = useRouter();

const appDropdownOpen = ref(false);
const projectDropdownOpen = ref(false);
const avatarDropdownOpen = ref(false);

const searchTextApp = ref('');
const searchTextProject = ref('');

const projectsForCurrentApp = ref<Project[]>([]);

const appDropdownRef = ref<HTMLElement>();
const projectDropdownRef = ref<HTMLElement>();
const avatarDropdownRef = ref<HTMLElement>();

const organizationId = computed(() => USER_STATE.getOrganizationId());
const applicationId = computed(() => route.params.applicationId as string | undefined);
const projectId = computed(() => route.params.projectId as string | undefined);

onMounted(() => {
  PROFILE_STATE.load().catch(error => debug('Failed to load profile: %O', error));
  if (APPLICATION_STATE.allApplications.length === 0) {
    APPLICATION_STATE.loadAllApplications();
  }
  document.addEventListener('click', handleClickOutside);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside);
});

const filteredApplications = computed(() => {
  return APPLICATION_STATE.allApplications.filter(app =>
    app.id.toLowerCase().includes(searchTextApp.value.toLowerCase())
  );
});

const filteredProjects = computed(() => {
  return projectsForCurrentApp.value.filter(proj =>
    proj.name.toLowerCase().includes(searchTextProject.value.toLowerCase())
  );
});

const currentProjectName = computed(() => {
  const project = projectsForCurrentApp.value.find(p => p.id === projectId.value);
  return project?.name ?? projectId.value ?? '';
});

const isDark = darkMode;

const avatarMenuItemClass = computed(() => [
  'flex w-full items-center px-4 py-2 text-left text-sm',
  isDark.value ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-700 hover:bg-surface-100'
]);

function toggleTheme() {
  toggleDark();
}

watch(applicationId, onApplicationChanged, { immediate: true });
async function onApplicationChanged(id: string | undefined) {
  projectsForCurrentApp.value = [];
  if (id === undefined) {
    return;
  }
  if (APPLICATION_STATE.currentApplication?.id !== id) {
    await syncCurrentApplication(id);
  }
  await loadProjectsForCurrentApp(id);
}

/** Points the shared application state at the route's application. */
async function syncCurrentApplication(id: string): Promise<void> {
  try {
    if (APPLICATION_STATE.allApplications.length === 0) {
      await APPLICATION_STATE.loadAllApplications();
    }
    const listed = APPLICATION_STATE.allApplications.find(app => app.id === id);
    APPLICATION_STATE.currentApplication = listed ?? await Kinotic.applications.findById(id);
  } catch (error) {
    debug('Failed to load application %s: %O', id, error);
  }
}

async function loadProjectsForCurrentApp(id: string): Promise<void> {
  try {
    const result = await Kinotic.projects.findAllForApplication(id, Pageable.create(0, 100));
    // the route may have moved to another application while this request was in flight
    if (applicationId.value === id) {
      projectsForCurrentApp.value = result.content ?? [];
    }
  } catch (error) {
    debug('Failed to load projects for %s: %O', id, error);
  }
}

function toggleAppDropdown() {
  appDropdownOpen.value = !appDropdownOpen.value;
  if (appDropdownOpen.value) projectDropdownOpen.value = false;
}

function toggleProjectDropdown() {
  projectDropdownOpen.value = !projectDropdownOpen.value;
  if (projectDropdownOpen.value) appDropdownOpen.value = false;
}

function toggleAvatarDropdown() {
  avatarDropdownOpen.value = !avatarDropdownOpen.value;
  if (avatarDropdownOpen.value) {
    appDropdownOpen.value = false;
    projectDropdownOpen.value = false;
  }
}

async function handleLogout() {
  try {
    await USER_STATE.logout();
    router.push('/login');
  } catch (error) {
    router.push('/login');
  }
}

/** The part of the current path below the given scope path, or '' when not inside it. */
function pathBelow(scopePath: string): string {
  return route.path.startsWith(scopePath) ? route.path.slice(scopePath.length) : '';
}

function selectApp(app: Application) {
  appDropdownOpen.value = false;
  searchTextApp.value = '';
  // only the section carries over: a project or an entity belongs to this application alone
  const section = projectId.value
      ? ''
      : pathBelow(`/application/${encodeURIComponent(applicationId.value ?? '')}`).split('/')[1];
  const below = section ? `/${section}` : '';
  router.push(`/application/${encodeURIComponent(app.id)}${below}`);
}

function selectProject(proj: Project) {
  projectDropdownOpen.value = false;
  searchTextProject.value = '';
  const scopePath = `/application/${encodeURIComponent(applicationId.value ?? '')}/project/${encodeURIComponent(projectId.value ?? '')}`;
  // only the first segment carries over: an entity or job run belongs to this project alone
  const section = pathBelow(scopePath).split('/')[1];
  const below = section ? `/${section}` : '';
  router.push(`/application/${encodeURIComponent(applicationId.value ?? '')}/project/${encodeURIComponent(proj.id ?? '')}${below}`);
}

function handleClickOutside(event: MouseEvent) {
  const appDropdownEl = appDropdownRef.value;
  const projectDropdownEl = projectDropdownRef.value;
  const avatarDropdownEl = avatarDropdownRef.value;

  const clickedOutsideApp = appDropdownEl && !appDropdownEl.contains(event.target as Node);
  const clickedOutsideProject = projectDropdownEl && !projectDropdownEl.contains(event.target as Node);
  const clickedOutsideAvatar = avatarDropdownEl && !avatarDropdownEl.contains(event.target as Node);

  if (appDropdownOpen.value && clickedOutsideApp) {
    appDropdownOpen.value = false;
  }
  if (projectDropdownOpen.value && clickedOutsideProject) {
    projectDropdownOpen.value = false;
  }
  if (avatarDropdownOpen.value && clickedOutsideAvatar) {
    avatarDropdownOpen.value = false;
  }
}
</script>
