<template>
  <div ref="headerRef" class="px-6 py-4 bg-surface-950 flex justify-between items-center sticky top-0 left-0 z-50">
    <div class="flex items-center gap-2 text-white relative">
      <RouterLink to="/applications" class="flex items-center gap-2">
        <img v-if="!isApplicationDetailsPage && !isProjectStructuresPage && !isApplicationSettingsPage && !isDashboardsPage && !isDataInsightsPage && !isSavedWidgetsPage" src="@/assets/logo.svg" class="h-8" />
        <img v-else src="@/assets/sidebar-logo.svg" class="!h-8 !w-8" />
      </RouterLink>

      <template v-if="isApplicationDetailsPage || isProjectStructuresPage || isApplicationSettingsPage || isDashboardsPage || isDataInsightsPage || isSavedWidgetsPage">
        <span class="text-surface-400 text-lg">/</span>

        <div ref="appDropdownRef" class="relative inline-block mr-8">
          <button @click="toggleAppDropdown"
            class="flex items-center gap-2 text-surface-400 font-medium text-sm hover:opacity-80 w-full justify-between">
            {{ currentAppName }}
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          <div v-if="appDropdownOpen"
            class="absolute top-full left-0 mt-1 bg-white rounded shadow-lg w-64 max-h-80 overflow-y-auto z-50 p-2">
            <div class="w-full mb-2">
              <IconField class="w-full">
                <InputIcon class="pi pi-search" />
                <InputText v-model="searchTextApp" placeholder="Search applications" class="w-full" />
              </IconField>
            </div>
            <div v-for="app in filteredApplications" :key="app.id" @click="selectApp(app)"
              :class="[
                'px-4 py-2 cursor-pointer text-sm rounded flex items-center justify-between',
                currentApp?.id === app.id 
                  ? 'bg-blue-100 text-blue-700 font-medium' 
                  : 'hover:bg-gray-100 text-surface-950'
              ]">
              <span>{{ app.id }}</span>
              <i v-if="currentApp?.id === app.id" class="pi pi-check text-blue-600"></i>
            </div>
          </div>
        </div>

                 <template v-if="currentApp && !isApplicationSettingsPage && !isDashboardsPage && !isDataInsightsPage && !isSavedWidgetsPage">
           <span class="text-surface-400 text-lg">/</span>
           <div ref="projectDropdownRef" class="relative inline-block">
            <button @click="toggleProjectDropdown"
              class="flex items-center gap-2 text-surface-400 font-medium text-sm hover:opacity-80 w-full justify-between">
              {{ currentProjectName }}
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
              </svg>
            </button>

            <div v-if="projectDropdownOpen"
              class="absolute top-full left-0 mt-1 bg-white rounded shadow-lg w-64 max-h-80 overflow-y-auto z-50 p-2">
              <div class="w-full mb-2">
                <IconField class="w-full">
                  <InputIcon class="pi pi-search" />
                  <InputText v-model="searchTextProject" placeholder="Search projects" class="w-full" />
                </IconField>
              </div>
              <div v-for="proj in filteredProjects" :key="proj.id ?? ''" @click="selectProject(proj)"
                :class="[
                  'px-4 py-2 cursor-pointer text-sm rounded flex items-center justify-between',
                  currentProject?.id === proj.id 
                    ? 'bg-blue-100 text-blue-700 font-medium' 
                    : 'hover:bg-gray-100 text-surface-950'
                ]">
                <span>{{ proj.name }}</span>
                <i v-if="currentProject?.id === proj.id" class="pi pi-check text-blue-600"></i>
              </div>
            </div>
          </div>
        </template>
      </template>
    </div>

         <div ref="avatarDropdownRef" class="relative">
       <button @click="toggleAvatarDropdown" class="flex items-center">
         <img src="@/assets/avatar.png" class="h-8 w-8 rounded-full cursor-pointer hover:opacity-80" />
       </button>
       
       <div v-if="avatarDropdownOpen" 
         class="absolute top-full right-0 mt-1 bg-white rounded shadow-lg w-48 z-50">
         <div class="py-1">
           <button @click="handleLogout" 
             class="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 flex items-center">
             <i class="pi pi-sign-out mr-2"></i>
             Logout
           </button>
         </div>
       </div>
     </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Watch } from 'vue-facing-decorator';
import { APPLICATION_STATE } from '@/states/IApplicationState';
import { USER_STATE } from '@/states/IUserState';
import type { Application, Project } from '@kinotic/structures-api';
import { Structures } from '@kinotic/structures-api';
import InputText from 'primevue/inputtext';
import IconField from 'primevue/iconfield';
import InputIcon from 'primevue/inputicon';

@Component({
  components: {
    InputText,
    IconField,
    InputIcon
  }
})
export default class Header extends Vue {
  appDropdownOpen = false;
  projectDropdownOpen = false;
  avatarDropdownOpen = false;

  searchTextApp = '';
  searchTextProject = '';

  isApplicationDetailsPage = false;
  isProjectStructuresPage = false;
  isApplicationSettingsPage = false;
  isDashboardsPage = false;
  isDataInsightsPage = false;
  isSavedWidgetsPage = false;

  projectsForCurrentApp: Project[] = [];
  currentApp: Application | null = null;
  currentProject: Project | null = null;
  isLoadingProjects: boolean = false;
  isSwitchingApplication: boolean = false;

  mounted() {
    this.updateRouteState();
    this.loadApplicationsIfNeeded();
    if (!APPLICATION_STATE.currentApplication) {
      this.tryAutoSelectAppAndProject();
    }
    document.addEventListener('click', this.handleClickOutside);
  }

  beforeUnmount() {
    document.removeEventListener('click', this.handleClickOutside);
  }

  get allApplications() {
    return APPLICATION_STATE.allApplications;
  }

  get filteredApplications() {
    return this.allApplications.filter(app =>
      app.id.toLowerCase().includes(this.searchTextApp.toLowerCase())
    );
  }

  get filteredProjects() {
    return this.projectsForCurrentApp.filter(proj =>
      proj.name.toLowerCase().includes(this.searchTextProject.toLowerCase())
    );
  }

  get currentAppName() {
    return this.currentApp?.id || 'Select Application';
  }

  get currentProjectName() {
    return this.currentProject?.name || 'Select Project';
  }

  @Watch('$route.fullPath', { immediate: true })
  onRouteChange() {
    this.updateRouteState();
    if (!APPLICATION_STATE.currentApplication) {
      this.tryAutoSelectAppAndProject();
    }
  }

  @Watch('APPLICATION_STATE.currentApplication', { immediate: true })
  onGlobalApplicationChange() {
    this.currentApp = APPLICATION_STATE.currentApplication;
    // Only auto-load projects if we're not in the middle of switching applications
    // to prevent race conditions and infinite loops
    if (this.currentApp && !this.isSwitchingApplication) {
      this.loadProjectsForCurrentApp();
    }
  }

  updateRouteState() {
    const path = this.$route.path;
    this.isApplicationDetailsPage = /^\/application\/[^/]+$/.test(path);
    this.isProjectStructuresPage = /^\/application\/[^/]+\/project\/[^/]+\/structures$/.test(path);
    this.isApplicationSettingsPage = /^\/application\/[^/]+\/settings$/.test(path);
    this.isDashboardsPage = /^\/application\/[^/]+\/dashboards(\/.*)?$/.test(path);
    this.isDataInsightsPage = /^\/application\/[^/]+\/data-insights$/.test(path);
    this.isSavedWidgetsPage = /^\/application\/[^/]+\/saved-widgets$/.test(path);
    
    // Set current application based on route
    if (this.isApplicationDetailsPage || this.isProjectStructuresPage || this.isApplicationSettingsPage || this.isDashboardsPage || this.isDataInsightsPage || this.isSavedWidgetsPage) {
      const applicationId = this.$route.params.applicationId as string;
      if (applicationId && this.currentApp?.id !== applicationId) {
        this.setActiveAppById(applicationId);
      }
    }
    
    if (this.isApplicationDetailsPage && !this.isProjectStructuresPage) {
      this.currentProject = null;
    }
    else if (this.isProjectStructuresPage) {
      const projectId = this.$route.params.projectId as string;
      if (projectId && this.currentProject?.id !== projectId) {
        this.setCurrentProjectById(projectId);
      }
    }
    else if (this.isApplicationSettingsPage || this.isDashboardsPage || this.isDataInsightsPage || this.isSavedWidgetsPage) {
      this.currentProject = null;
    }
  }

  loadApplicationsIfNeeded() {
    if (APPLICATION_STATE.allApplications.length === 0) {
      APPLICATION_STATE.loadAllApplications();
    }
  }

  toggleAppDropdown() {
    this.appDropdownOpen = !this.appDropdownOpen;
    if (this.appDropdownOpen) this.projectDropdownOpen = false;
  }

  toggleProjectDropdown() {
    if (!this.currentApp) return;
    this.projectDropdownOpen = !this.projectDropdownOpen;
    if (this.projectDropdownOpen) this.appDropdownOpen = false;

    if (this.projectsForCurrentApp.length === 0) {
      this.loadProjectsForCurrentApp();
    }
  }

  toggleAvatarDropdown() {
    this.avatarDropdownOpen = !this.avatarDropdownOpen;
    if (this.avatarDropdownOpen) {
      this.appDropdownOpen = false;
      this.projectDropdownOpen = false;
    }
  }

  async handleLogout() {
    try {
      await USER_STATE.logout();
      this.$router.push('/login');
    } catch (error) {
      this.$router.push('/login');
    }
  }

  async loadProjectsForCurrentApp() {
    if (!this.currentApp || this.isLoadingProjects) return;
    
    this.isLoadingProjects = true;
    try {
      const pageable = { pageNumber: 0, pageSize: 100 } as any;
      const result = await Structures.getProjectService().findAllForApplication(this.currentApp.id, pageable);
      this.projectsForCurrentApp = result.content ?? [];
      
      if (this.isProjectStructuresPage) {
        const projectId = this.$route.params.projectId as string;
        const routeAppId = this.$route.params.applicationId as string;
        
        // Only try to set the project if we're still on the same application
        // This prevents infinite loops when switching to an app with no projects
        if (projectId && routeAppId === this.currentApp.id && this.currentProject?.id !== projectId) {
          this.setCurrentProjectById(projectId);
        }
      }
    } catch (e) {
      console.error('[Header] Failed to load projects:', e);
    } finally {
      this.isLoadingProjects = false;
    }
  }

  async selectApp(app: Application) {
    // Set flag to prevent watchers from triggering during the switch
    this.isSwitchingApplication = true;
    
    try {
      this.currentApp = app;
      APPLICATION_STATE.currentApplication = app;
      this.appDropdownOpen = false;
      this.currentProject = null;
      this.projectsForCurrentApp = []; // Clear projects from previous app
      this.searchTextApp = '';

      // Navigate first to change the route context, then load projects
      await this.$router.push(`/application/${encodeURIComponent(app.id)}`);
      
      // Small delay to ensure route has fully updated
      await new Promise(resolve => setTimeout(resolve, 50));
      
      await this.loadProjectsForCurrentApp();

      this.$emit('application-changed', app);
    } finally {
      // Always clear the flag even if there's an error
      this.isSwitchingApplication = false;
    }
  }

  selectProject(proj: Project) {
    this.currentProject = proj;
    const projectId = proj.id ?? '';
    const applicationId = this.currentApp?.id ?? '';
    this.$router.push(`/application/${encodeURIComponent(applicationId)}/project/${encodeURIComponent(projectId)}/structures`);
    this.projectDropdownOpen = false;
    this.searchTextProject = '';
  }

  async setActiveAppById(applicationId: string) {
    const app = this.allApplications.find(a => a.id === applicationId);
    if (app) {
      this.currentApp = app;
      APPLICATION_STATE.currentApplication = app;
    } else {
      setTimeout(() => this.setActiveAppById(applicationId), 500);
    }
  }

  async setActiveProjectById(applicationId: string, projectId: string): Promise<void> {
    try {
      const app = this.allApplications.find(a => a.id === applicationId);
      if (app) {
        this.currentApp = app;
        APPLICATION_STATE.currentApplication = app;

        const pageable = { pageNumber: 0, pageSize: 100 } as any;
        const result = await Structures.getProjectService().findAllForApplication(applicationId, pageable);
        this.projectsForCurrentApp = result.content ?? [];

        const proj = this.projectsForCurrentApp.find(p => p.id === projectId);
        if (proj) {
          this.currentProject = proj;
        }
      }
    } catch (error) {
    }
  }

  setCurrentProjectById(projectId: string): void {
    if (this.projectsForCurrentApp.length > 0) {
      const proj = this.projectsForCurrentApp.find(p => p.id === projectId);
      if (proj) {
        this.currentProject = proj;
      } else {
        // Project not found in the list, clear current project
        console.warn('[Header] Project not found in current application:', projectId);
        this.currentProject = null;
      }
    } else {
      // No projects loaded yet - only try to load if we're not already loading
      // and if the route application matches the current application
      // and we're not in the middle of switching applications
      const routeAppId = this.$route.params.applicationId as string;
      if (routeAppId === this.currentApp?.id && !this.isLoadingProjects && !this.isSwitchingApplication) {
        this.loadProjectsForCurrentApp();
      } else {
        // Different application in route vs state, or already loading, just clear
        this.currentProject = null;
      }
    }
  }

  async tryAutoSelectAppAndProject() {
    if (APPLICATION_STATE.allApplications.length === 0) {
      await APPLICATION_STATE.loadAllApplications();
    }

    if (this.isProjectStructuresPage) {
      const applicationId = this.$route.params.applicationId as string;
      const projectId = this.$route.params.projectId as string;
      await this.setActiveProjectById(applicationId, projectId);
    } else if (this.isApplicationDetailsPage || this.isApplicationSettingsPage) {
      const path = this.$route.path;
      const match = path.match(/^\/application\/([^/]+)/);
      if (match) {
        const applicationId = decodeURIComponent(match[1]);
        await this.setActiveAppById(applicationId);
      }
    }
  }

  handleClickOutside(event: MouseEvent) {
    const appDropdownEl = this.$refs.appDropdownRef as HTMLElement;
    const projectDropdownEl = this.$refs.projectDropdownRef as HTMLElement;
    const avatarDropdownEl = this.$refs.avatarDropdownRef as HTMLElement;

    const clickedOutsideApp = appDropdownEl && !appDropdownEl.contains(event.target as Node);
    const clickedOutsideProject = projectDropdownEl && !projectDropdownEl.contains(event.target as Node);
    const clickedOutsideAvatar = avatarDropdownEl && !avatarDropdownEl.contains(event.target as Node);

    if (this.appDropdownOpen && clickedOutsideApp) {
      this.appDropdownOpen = false;
    }
    if (this.projectDropdownOpen && clickedOutsideProject) {
      this.projectDropdownOpen = false;
    }
    if (this.avatarDropdownOpen && clickedOutsideAvatar) {
      this.avatarDropdownOpen = false;
    }
  }

  notifyApplicationChange(app: Application) {
    this.$emit('application-changed', app);
  }
}
</script>
