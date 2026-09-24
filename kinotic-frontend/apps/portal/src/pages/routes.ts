import { type RouteMeta, type RouteRecordRaw } from 'vue-router'
import type { SidebarItemMeta } from '@kinotic-ai/frontend-common'

/**
 * The portal's navigation has four scopes, each with its own sidebar group: the
 * organization, one application, one project, and the signed-in account. A route's
 * scope is the {@code sidebarGroup} of its layout record; the sidebar items are the
 * routes that declare a {@link SidebarItemMeta} for that group. Pages opened from a
 * list (a workload, a job run, an entity, a trace) nest under the list's path so the
 * sidebar keeps the list highlighted and the page header can point back to it.
 */

const layout = () => import('@/layouts/LayoutForPage.vue')

function organizationItem(label: string, icon: string, order: number, section: string): SidebarItemMeta {
  return { group: 'organization', section, label, icon, order }
}

function applicationItem(label: string, icon: string, order: number, section?: string): SidebarItemMeta {
  return { group: 'application', section, label, icon, order }
}

function projectItem(label: string, icon: string, order: number, section?: string): SidebarItemMeta {
  return { group: 'project', section, label, icon, order }
}

function accountItem(label: string, icon: string, order: number): SidebarItemMeta {
  return { group: 'account', section: 'Account', label, icon, order }
}

/** A top-level organization page: its own layout record carrying the sidebar item. */
function organizationPage(path: string, sidebar: SidebarItemMeta, children: RouteRecordRaw[]): RouteRecordRaw {
  return {
    path,
    component: layout,
    meta: { sidebarGroup: 'organization', sidebar } as RouteMeta,
    children
  }
}

/**
 * The workloads and job runs of an application or a project, with their detail pages, as
 * children of the scope's layout record.
 */
function runtimeRoutes(name: string, item: (label: string, icon: string, order: number, section?: string) => SidebarItemMeta,
                       order: number): RouteRecordRaw[] {
  return [
    {
      name: `${name}-workloads`,
      path: 'workloads',
      meta: { sidebar: item('Workloads', 'pi-box', order, 'Runtime') } as RouteMeta,
      component: () => import('@/pages/WorkloadsPage.vue'),
      props: true
    },
    {
      name: `${name}-workload`,
      path: 'workloads/:workloadId',
      component: () => import('@/pages/WorkloadPage.vue'),
      props: true
    },
    {
      name: `${name}-jobs`,
      path: 'jobs',
      meta: { sidebar: item('Jobs', 'pi-list-check', order + 1, 'Runtime') } as RouteMeta,
      component: () => import('@/pages/JobsPage.vue'),
      props: true
    },
    {
      name: `${name}-job-run`,
      path: 'jobs/:jobRunId',
      component: () => import('@/pages/JobRunPage.vue'),
      props: true
    }
  ]
}

const pageRoutes: RouteRecordRaw[] = [
  organizationPage('/overview', organizationItem('Overview', 'pi-objects-column', 5, 'Organization'), [
    {
      name: 'organization-overview',
      path: '',
      component: () => import('@/pages/OrganizationOverview.vue')
    }
  ]),

  organizationPage('/applications', organizationItem('Applications', 'pi-th-large', 10, 'Organization'), [
    {
      name: 'applications',
      path: '',
      component: () => import('@/pages/ApplicationList.vue')
    }
  ]),

  organizationPage('/projects', organizationItem('Projects', 'pi-folder', 15, 'Organization'), [
    {
      name: 'organization-projects',
      path: '',
      component: () => import('@/pages/OrganizationProjectsPage.vue')
    }
  ]),

  organizationPage('/workloads', organizationItem('Workloads', 'pi-box', 20, 'Runtime'), [
    {
      name: 'workloads',
      path: '',
      component: () => import('@/pages/WorkloadsPage.vue')
    },
    {
      name: 'workload',
      path: ':workloadId',
      component: () => import('@/pages/WorkloadPage.vue'),
      props: true
    }
  ]),

  organizationPage('/jobs', organizationItem('Jobs', 'pi-list-check', 25, 'Runtime'), [
    {
      name: 'jobs',
      path: '',
      component: () => import('@/pages/JobsPage.vue')
    },
    {
      name: 'job-run',
      path: ':jobRunId',
      component: () => import('@/pages/JobRunPage.vue'),
      props: true
    }
  ]),

  organizationPage('/observability', organizationItem('Observability', 'pi-chart-line', 30, 'Runtime'), [
    {
      name: 'organization-observability',
      path: '',
      component: () => import('@/pages/OrganizationObservabilityPage.vue')
    },
    {
      name: 'organization-trace',
      path: 'traces/:traceId',
      component: () => import('@/pages/TracePage.vue'),
      props: true
    }
  ]),

  organizationPage('/members', organizationItem('Members', 'pi-users', 40, 'People & access'), [
    {
      name: 'organization-members',
      path: '',
      component: () => import('@/pages/MembersPage.vue'),
      props: { applicationId: null }
    }
  ]),

  organizationPage('/organization-settings', organizationItem('Organization settings', 'pi-cog', 50, 'Settings'), [
    {
      name: 'organization-settings',
      path: '',
      component: () => import('@/pages/OrganizationSettings.vue')
    }
  ]),

  {
    path: '/graphql',
    component: layout,
    meta: { sidebarGroup: 'organization', fullWidth: true } as RouteMeta,
    children: [
      {
        name: 'graphql-playground',
        path: '',
        component: () => import('@/pages/GraphQLPlayground.vue')
      }
    ]
  },

  {
    path: '/account',
    redirect: '/account/profile',
    component: layout,
    meta: { sidebarGroup: 'account' } as RouteMeta,
    children: [
      {
        name: 'account-profile',
        path: 'profile',
        meta: { sidebar: accountItem('Profile', 'pi-user', 10) } as RouteMeta,
        component: () => import('@/pages/ProfilePage.vue')
      },
      {
        name: 'account-connected-apps',
        path: 'connected-apps',
        meta: { sidebar: accountItem('Connected apps', 'pi-link', 20) } as RouteMeta,
        component: () => import('@kinotic-ai/frontend-common').then(m => m.ConnectedAppsPage)
      }
    ]
  },

  {
    path: '/application/:applicationId',
    component: layout,
    meta: { sidebarGroup: 'application' } as RouteMeta,
    children: [
      {
        name: 'application-overview',
        path: '',
        meta: { sidebar: applicationItem('Overview', 'pi-objects-column', 10) } as RouteMeta,
        component: () => import('@/pages/ApplicationOverview.vue'),
        props: true
      },
      {
        name: 'application-projects',
        path: 'projects',
        meta: { sidebar: applicationItem('Projects', 'pi-folder', 20) } as RouteMeta,
        component: () => import('@/pages/ProjectsPage.vue'),
        props: true
      },
      {
        name: 'application-entities',
        path: 'entities',
        meta: { sidebar: applicationItem('Entities', 'pi-table', 30) } as RouteMeta,
        component: () => import('@/pages/ApplicationEntitiesPage.vue'),
        props: true
      },
      {
        name: 'application-entity',
        path: 'entities/:entityDefinitionId',
        component: () => import('@/pages/EntityDetailPage.vue'),
        props: true
      },
      ...runtimeRoutes('application', applicationItem, 38),
      {
        name: 'application-observability',
        path: 'observability',
        meta: { sidebar: applicationItem('Observability', 'pi-chart-line', 40, 'Runtime') } as RouteMeta,
        component: () => import('@/pages/ObservabilityPage.vue'),
        props: true
      },
      {
        name: 'application-trace',
        path: 'observability/traces/:traceId',
        component: () => import('@/pages/TracePage.vue'),
        props: true
      },
      {
        name: 'application-users',
        path: 'users',
        meta: { sidebar: applicationItem('Users', 'pi-users', 50, 'Access') } as RouteMeta,
        component: () => import('@/pages/MembersPage.vue'),
        props: true
      },
      {
        name: 'application-machines',
        path: 'machines',
        meta: { sidebar: applicationItem('Machines', 'pi-server', 60, 'Access') } as RouteMeta,
        component: () => import('@/pages/MachinesPage.vue'),
        props: true
      },
      {
        name: 'application-settings',
        path: 'settings',
        meta: { sidebar: applicationItem('Settings', 'pi-cog', 70, 'Settings') } as RouteMeta,
        component: () => import('@/pages/ApplicationSettings.vue'),
        props: true
      }
    ]
  },

  {
    path: '/application/:applicationId/project/:projectId',
    component: layout,
    meta: { sidebarGroup: 'project' } as RouteMeta,
    children: [
      {
        name: 'project-overview',
        path: '',
        meta: { sidebar: projectItem('Overview', 'pi-objects-column', 10) } as RouteMeta,
        component: () => import('@/pages/ProjectOverview.vue'),
        props: true
      },
      {
        name: 'project-entities',
        path: 'entities',
        meta: { sidebar: projectItem('Entities', 'pi-table', 20) } as RouteMeta,
        component: () => import('@/pages/ProjectEntitiesPage.vue'),
        props: true
      },
      {
        name: 'project-entity',
        path: 'entities/:entityDefinitionId',
        component: () => import('@/pages/EntityDetailPage.vue'),
        props: true
      },
      {
        name: 'project-deployment',
        path: 'deployment',
        meta: { sidebar: projectItem('Deployment', 'pi-cloud-upload', 30) } as RouteMeta,
        component: () => import('@/pages/ProjectDeploymentPage.vue'),
        props: true
      },
      ...runtimeRoutes('project', projectItem, 40)
    ]
  },

  {
    path: '/new-entity-definition',
    component: () => import('@/pages/NewEntityDefinition.vue')
  },
  {
    path: '/login',
    component: () => import('@/pages/login/LoginPage.vue'),
    meta: { authenticationRequired: false } as RouteMeta
  },
  {
    path: '/signup',
    component: () => import('@/pages/signup/GithubSignup.vue'),
    meta: { authenticationRequired: false } as RouteMeta
  },
  {
    path: '/signup/verify',
    component: () => import('@/pages/signup/VerifyEmail.vue'),
    meta: { authenticationRequired: false } as RouteMeta
  },
  {
    path: '/register',
    component: () => import('@/pages/signup/CompleteOrg.vue'),
    meta: { authenticationRequired: false } as RouteMeta
  },
  {
    path: '/invite/accept',
    component: () => import('@/pages/signup/AcceptInvitation.vue'),
    meta: { authenticationRequired: false } as RouteMeta
  },
  {
    path: '/device',
    meta: { authenticationRequired: true },
    component: () => import('@/pages/login/DeviceVerification.vue')
  },
  {
    path: '/oauth/consent',
    meta: { authenticationRequired: true },
    component: () => import('@/pages/login/OAuthConsent.vue')
  },
  {
    name: 'github-install-callback',
    path: '/github/install/callback',
    component: () => import('@/pages/GitHubInstallCallback.vue'),
    meta: {
      // Runs completeInstall against the platform, so the session must be present;
      // an unauthenticated hit bounces through /login and returns here via referer.
      authenticationRequired: true
    } as RouteMeta
  }
]

export default pageRoutes
