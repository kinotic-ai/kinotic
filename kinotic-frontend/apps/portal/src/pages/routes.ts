import type { Component } from 'vue'
import { type RouteMeta, type RouteRecordRaw } from 'vue-router'
import { ChartLine, CloudUpload, KeyRound, LaptopMinimalCheck, LayoutDashboard, LayoutGrid, Link, ListTree, Server, Settings, Table, User, Users, Zap } from '@lucide/vue'
import { ConnectedAppsPage, OAuthConsentPage, ProjectsIcon, type SidebarItemMeta } from '@kinotic-ai/frontend-common'

import loginPageLeft from '@/assets/login-page-left.svg'
import { USER_STATE } from '@/states/IUserState'

/**
 * The portal's navigation has four scopes, each with its own sidebar group: the
 * organization, one application, one project, and the signed-in account. A route's
 * scope is the {@code sidebarGroup} of its layout record; the sidebar items are the
 * routes that declare a {@link SidebarItemMeta} for that group. Pages opened from a
 * list (a job run, an entity, a trace) nest under the list's path so the sidebar keeps
 * the list highlighted and the page header can point back to it.
 */

const layout = () => import('@/layouts/LayoutForPage.vue')

function organizationItem(label: string, icon: Component, order: number, section: string): SidebarItemMeta {
  return { group: 'organization', section, label, icon, order }
}

function applicationItem(label: string, icon: Component, order: number, section?: string): SidebarItemMeta {
  return { group: 'application', section, label, icon, order }
}

function projectItem(label: string, icon: Component, order: number): SidebarItemMeta {
  return { group: 'project', label, icon, order }
}

function accountItem(label: string, icon: Component, order: number): SidebarItemMeta {
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

const pageRoutes: RouteRecordRaw[] = [
  organizationPage('/quickstart', organizationItem('Quickstart', Zap, 5, 'Getting started'), [
    {
      name: 'quickstart',
      path: '',
      component: () => import('@/pages/QuickstartPage.vue')
    }
  ]),

  organizationPage('/applications', organizationItem('Applications', LayoutGrid, 10, 'Organization'), [
    {
      name: 'applications',
      path: '',
      component: () => import('@/pages/ApplicationList.vue')
    }
  ]),

  organizationPage('/jobs', organizationItem('Jobs', LaptopMinimalCheck, 20, 'Organization'), [
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

  organizationPage('/observability', organizationItem('Observability', ChartLine, 30, 'Organization'), [
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

  organizationPage('/members', organizationItem('Members', Users, 40, 'People & access'), [
    {
      name: 'organization-members',
      path: '',
      component: () => import('@/pages/MembersPage.vue'),
      props: { applicationId: null }
    }
  ]),

  organizationPage('/organization-settings', organizationItem('Organization settings', Settings, 50, 'Settings'), [
    {
      name: 'organization-settings',
      path: '',
      component: () => import('@/pages/OrganizationSettings.vue')
    }
  ]),

  {
    path: '/account',
    redirect: '/account/profile',
    component: layout,
    meta: { sidebarGroup: 'account' } as RouteMeta,
    children: [
      {
        name: 'account-profile',
        path: 'profile',
        meta: { sidebar: accountItem('Profile', User, 10) } as RouteMeta,
        component: () => import('@/pages/ProfilePage.vue')
      },
      {
        name: 'account-connected-apps',
        path: 'connected-apps',
        meta: { sidebar: accountItem('Connected apps', Link, 20) } as RouteMeta,
        component: ConnectedAppsPage
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
        meta: { sidebar: applicationItem('Overview', LayoutDashboard, 10) } as RouteMeta,
        component: () => import('@/pages/ApplicationOverview.vue'),
        props: true
      },
      {
        name: 'application-projects',
        path: 'projects',
        meta: { sidebar: applicationItem('Projects', ProjectsIcon, 20) } as RouteMeta,
        component: () => import('@/pages/ProjectsPage.vue'),
        props: true
      },
      {
        name: 'application-entities',
        path: 'entities',
        meta: { sidebar: applicationItem('Entities', Table, 30) } as RouteMeta,
        component: () => import('@/pages/ApplicationEntitiesPage.vue'),
        props: true
      },
      {
        name: 'application-entity',
        path: 'entities/:entityDefinitionId',
        component: () => import('@/pages/EntityDetailPage.vue'),
        props: true
      },
      {
        name: 'application-observability',
        path: 'observability',
        meta: { sidebar: applicationItem('Observability', ChartLine, 40) } as RouteMeta,
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
        meta: { sidebar: applicationItem('Users', Users, 50, 'Access') } as RouteMeta,
        component: () => import('@/pages/MembersPage.vue'),
        props: true
      },
      {
        name: 'application-users-access',
        path: 'users/access',
        component: () => import('@/pages/ApplicationUsersAccessPage.vue'),
        props: true
      },
      {
        name: 'application-machines',
        path: 'machines',
        meta: { sidebar: applicationItem('Machines', Server, 60, 'Access') } as RouteMeta,
        component: () => import('@/pages/MachinesPage.vue'),
        props: true
      },
      {
        name: 'application-access',
        path: 'access',
        meta: { sidebar: applicationItem('Access', KeyRound, 65, 'Access') } as RouteMeta,
        component: () => import('@/pages/ApplicationAccessPage.vue'),
        props: true
      },
      {
        name: 'application-settings',
        path: 'settings',
        meta: { sidebar: applicationItem('Settings', Settings, 70, 'Settings') } as RouteMeta,
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
        meta: { sidebar: projectItem('Overview', LayoutDashboard, 10) } as RouteMeta,
        component: () => import('@/pages/ProjectOverview.vue'),
        props: true
      },
      {
        name: 'project-entities',
        path: 'entities',
        meta: { sidebar: projectItem('Entities', Table, 20) } as RouteMeta,
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
        meta: { sidebar: projectItem('Deployment', CloudUpload, 30) } as RouteMeta,
        component: () => import('@/pages/ProjectDeploymentPage.vue'),
        props: true
      },
      {
        name: 'project-sbom',
        path: 'sbom',
        meta: { sidebar: projectItem('SBOM', ListTree, 40) } as RouteMeta,
        component: () => import('@/pages/ProjectSbomPage.vue'),
        props: true
      },
      {
        name: 'project-access',
        path: 'access',
        meta: { sidebar: projectItem('Access', KeyRound, 50) } as RouteMeta,
        component: () => import('@/pages/ProjectAccessPage.vue'),
        props: true
      }
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
    component: OAuthConsentPage,
    props: { session: USER_STATE, art: loginPageLeft }
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
