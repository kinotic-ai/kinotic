<template>
  <div ref="headerRef" class="sticky top-0 left-0 z-50 flex h-16 items-center justify-between border-b border-surface-800 bg-surface-950 px-6">
    <div class="relative flex items-center gap-3 text-white">
      <RouterLink to="/applications" class="flex items-center gap-2">
        <img src="@/assets/header-logo.svg" class="h-6 w-[27px]" alt="Kinotic" />
      </RouterLink>

      <template v-if="isApplicationDetailsPage || isProjectEntityDefinitionsPage || isApplicationSettingsPage">
        <span class="text-lg text-surface-600">/</span>

        <div ref="appDropdownRef" class="relative inline-block mr-8">
          <button @click="toggleAppDropdown"
            class="flex w-full items-center justify-between gap-2 text-sm font-medium text-surface-300 transition-opacity hover:opacity-80">
            {{ currentAppName }}
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
                currentApp?.id === app.id 
                  ? 'bg-primary-50 text-primary-600 font-medium' 
                  : isDark ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-950 hover:bg-surface-100'
              ]">
              <span>{{ app.id }}</span>
              <i v-if="currentApp?.id === app.id" class="pi pi-check text-primary-500"></i>
            </div>
          </div>
        </div>

                 <template v-if="currentApp && !isApplicationSettingsPage">
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
                  currentProject?.id === proj.id 
                    ? 'bg-primary-50 text-primary-600 font-medium' 
                    : isDark ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-950 hover:bg-surface-100'
                ]">
                <span>{{ proj.name }}</span>
                <i v-if="currentProject?.id === proj.id" class="pi pi-check text-primary-500"></i>
              </div>
            </div>
          </div>
        </template>
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
import { Kinotic } from '@kinotic-ai/core';
import type { Application, Project } from '@kinotic-ai/os-api';
import Avatar from 'primevue/avatar';
import InputText from 'primevue/inputtext';
import IconField from 'primevue/iconfield';
import InputIcon from 'primevue/inputicon';
import { createDebug, isDark as darkMode, toggleDark } from '@kinotic-ai/frontend-common'

const debug = createDebug('header');

const emit = defineEmits<{
  (e: 'application-changed', app: Application): void
}>();

const route = useRoute();
const router = useRouter();

const appDropdownOpen = ref(false);
const projectDropdownOpen = ref(false);
const avatarDropdownOpen = ref(false);

const searchTextApp = ref('');
const searchTextProject = ref('');

const isApplicationDetailsPage = ref(false);
const isProjectEntityDefinitionsPage = ref(false);
const isApplicationSettingsPage = ref(false);

const projectsForCurrentApp = ref<Project[]>([]);
const currentApp = ref<Application | null>(null);
const currentProject = ref<Project | null>(null);
const isLoadingProjects = ref<boolean>(false);
const isSwitchingApplication = ref<boolean>(false);

const appDropdownRef = ref<HTMLElement>();
const projectDropdownRef = ref<HTMLElement>();
const avatarDropdownRef = ref<HTMLElement>();

onMounted(() => {
  updateRouteState();
  PROFILE_STATE.load().catch(error => debug('Failed to load profile: %O', error));
  loadApplicationsIfNeeded();
  if (!APPLICATION_STATE.currentApplication) {
    tryAutoSelectAppAndProject();
  }
  document.addEventListener('click', handleClickOutside);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside);
});

const allApplications = computed(() => {
  return APPLICATION_STATE.allApplications;
});

const filteredApplications = computed(() => {
  return allApplications.value.filter(app =>
    app.id.toLowerCase().includes(searchTextApp.value.toLowerCase())
  );
});

const filteredProjects = computed(() => {
  return projectsForCurrentApp.value.filter(proj =>
    proj.name.toLowerCase().includes(searchTextProject.value.toLowerCase())
  );
});

const currentAppName = computed(() => {
  return currentApp.value?.id || 'Select Application';
});

const currentProjectName = computed(() => {
  return currentProject.value?.name || 'Select Project';
});

const isDark = darkMode;

const avatarMenuItemClass = computed(() => [
  'flex w-full items-center px-4 py-2 text-left text-sm',
  isDark.value ? 'text-surface-0 hover:bg-surface-800' : 'text-surface-700 hover:bg-surface-100'
]);

function toggleTheme() {
  toggleDark();
}

watch(() => route.fullPath, onRouteChange, { immediate: true });
function onRouteChange() {
  updateRouteState();
  if (!APPLICATION_STATE.currentApplication) {
    tryAutoSelectAppAndProject();
  }
}

watch(() => APPLICATION_STATE.currentApplication, onGlobalApplicationChange, { immediate: true });
function onGlobalApplicationChange() {
  currentApp.value = APPLICATION_STATE.currentApplication;
  if (currentApp.value && !isSwitchingApplication.value) {
    loadProjectsForCurrentApp();
  }
}

function updateRouteState() {
  const path = route.path;
  isApplicationDetailsPage.value = /^\/application\/[^/]+$/.test(path);
  isProjectEntityDefinitionsPage.value = /^\/application\/[^/]+\/project\/[^/]+\/entity-definitions$/.test(path);
  isApplicationSettingsPage.value = /^\/application\/[^/]+\/settings$/.test(path);

  // Set current application based on route
  if (isApplicationDetailsPage.value || isProjectEntityDefinitionsPage.value || isApplicationSettingsPage.value) {
    const applicationId = route.params.applicationId as string;
    if (applicationId && currentApp.value?.id !== applicationId) {
      setActiveAppById(applicationId);
    }
  }

  if (isApplicationDetailsPage.value && !isProjectEntityDefinitionsPage.value) {
    currentProject.value = null;
  }
  else if (isProjectEntityDefinitionsPage.value) {
    const projectId = route.params.projectId as string;
    if (projectId && currentProject.value?.id !== projectId) {
      setCurrentProjectById(projectId);
    }
  }
  else if (isApplicationSettingsPage.value) {
    currentProject.value = null;
  }
}

function loadApplicationsIfNeeded() {
  if (APPLICATION_STATE.allApplications.length === 0) {
    APPLICATION_STATE.loadAllApplications();
  }
}

function toggleAppDropdown() {
  appDropdownOpen.value = !appDropdownOpen.value;
  if (appDropdownOpen.value) projectDropdownOpen.value = false;
}

function toggleProjectDropdown() {
  if (!currentApp.value) return;
  projectDropdownOpen.value = !projectDropdownOpen.value;
  if (projectDropdownOpen.value) appDropdownOpen.value = false;

  if (projectsForCurrentApp.value.length === 0) {
    loadProjectsForCurrentApp();
  }
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

async function loadProjectsForCurrentApp() {
  if (!currentApp.value || isLoadingProjects.value) return;

  isLoadingProjects.value = true;
  try {
    const pageable = { pageNumber: 0, pageSize: 100 } as any;
    const result = await Kinotic.projects.findAllForApplication(currentApp.value.id, pageable);
    projectsForCurrentApp.value = result.content ?? [];

    if (isProjectEntityDefinitionsPage.value) {
      const projectId = route.params.projectId as string;
      const routeAppId = route.params.applicationId as string;

      if (projectId && routeAppId === currentApp.value.id && currentProject.value?.id !== projectId) {
        setCurrentProjectById(projectId);
      }
    }
  } catch (e) {
  } finally {
    isLoadingProjects.value = false;
  }
}

async function selectApp(app: Application) {
  isSwitchingApplication.value = true;

  try {
    currentApp.value = app;
    APPLICATION_STATE.currentApplication = app;
    appDropdownOpen.value = false;
    currentProject.value = null;
    projectsForCurrentApp.value = [];
    searchTextApp.value = '';

    await router.push(`/application/${encodeURIComponent(app.id)}`);
    await new Promise(resolve => setTimeout(resolve, 50));
    await loadProjectsForCurrentApp();

    emit('application-changed', app);
  } finally {
    isSwitchingApplication.value = false;
  }
}

function selectProject(proj: Project) {
  currentProject.value = proj;
  const projectId = proj.id ?? '';
  const applicationId = currentApp.value?.id ?? '';
  router.push(`/application/${encodeURIComponent(applicationId)}/project/${encodeURIComponent(projectId)}/entity-definitions`);
  projectDropdownOpen.value = false;
  searchTextProject.value = '';
}

async function setActiveAppById(applicationId: string) {
  const app = allApplications.value.find(a => a.id === applicationId);
  if (app) {
    currentApp.value = app;
    APPLICATION_STATE.currentApplication = app;
  } else {
    setTimeout(() => setActiveAppById(applicationId), 500);
  }
}

async function setActiveProjectById(applicationId: string, projectId: string): Promise<void> {
  try {
    const app = allApplications.value.find(a => a.id === applicationId);
    if (app) {
      currentApp.value = app;
      APPLICATION_STATE.currentApplication = app;

      const pageable = { pageNumber: 0, pageSize: 100 } as any;
      const result = await Kinotic.projects.findAllForApplication(applicationId, pageable);
      projectsForCurrentApp.value = result.content ?? [];

      const proj = projectsForCurrentApp.value.find(p => p.id === projectId);
      if (proj) {
        currentProject.value = proj;
      }
    }
  } catch (error) {
  }
}

function setCurrentProjectById(projectId: string): void {
  if (projectsForCurrentApp.value.length > 0) {
    const proj = projectsForCurrentApp.value.find(p => p.id === projectId);
    if (proj) {
      currentProject.value = proj;
    } else {
      currentProject.value = null;
    }
  } else {
    const routeAppId = route.params.applicationId as string;
    if (routeAppId === currentApp.value?.id && !isLoadingProjects.value && !isSwitchingApplication.value) {
      loadProjectsForCurrentApp();
    } else {
      currentProject.value = null;
    }
  }
}

async function tryAutoSelectAppAndProject() {
  if (APPLICATION_STATE.allApplications.length === 0) {
    await APPLICATION_STATE.loadAllApplications();
  }

  if (isProjectEntityDefinitionsPage.value) {
    const applicationId = route.params.applicationId as string;
    const projectId = route.params.projectId as string;
    await setActiveProjectById(applicationId, projectId);
  } else if (isApplicationDetailsPage.value || isApplicationSettingsPage.value) {
    const path = route.path;
    const match = path.match(/^\/application\/([^/]+)/);
    if (match) {
      const applicationId = decodeURIComponent(match[1]);
      await setActiveAppById(applicationId);
    }
  }
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
