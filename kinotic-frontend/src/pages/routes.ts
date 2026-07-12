
import { type RouteMeta, type RouteRecordRaw } from 'vue-router'
import type { SidebarItemMeta } from '@/types/SidebarItemMeta'

function organizationSidebarItem(label: string, icon: string, order: number): SidebarItemMeta {
  return { group: 'organization', section: 'Organization', label, icon, order }
}

function organizationPlaceholderRoute(path: string, name: string, title: string, description: string,
                                      icon: string, order: number): RouteRecordRaw {
  return {
    path,
    component: () => import('@/layouts/LayoutForPage.vue'),
    meta: {
      showInMainNav: false,
      label: title,
      sidebar: organizationSidebarItem(title, icon, order)
    } as RouteMeta,
    children: [
      {
        name,
        path: '',
        component: () => import('@/pages/OrganizationWorkspacePlaceholder.vue'),
        props: {
          title,
          description
        }
      }
    ]
  }
}

const pageRoutes: RouteRecordRaw[] = [
  {
    path: '/applications',
    component: () => import('@/layouts/LayoutForPage.vue'),
    meta: {
      showInMainNav: true,
      icon: 'microchip.svg',
      label: 'Applications',
      sidebar: { group: 'organization', section: 'Workspace', label: 'Applications', icon: 'pi-th-large', order: 10 } as SidebarItemMeta
    } as RouteMeta,
    children: [
      {
        name: "applications",
        path: '',
        component: () => import('@/pages/ApplicationList.vue'),
      },
    ]
  },

  {
    path: '/members',
    component: () => import('@/layouts/LayoutForPage.vue'),
    meta: {
      showInMainNav: false,
      label: 'Members',
      sidebar: organizationSidebarItem('Members', 'pi-users', 20)
    } as RouteMeta,
    children: [
      {
        name: 'organization-members',
        path: '',
        component: () => import('@/pages/MembersPage.vue'),
        props: { applicationId: null }
      }
    ]
  },
  organizationPlaceholderRoute('/roles-permissions', 'organization-roles', 'Roles & permissions', 'Define roles and control access across your organization.', 'pi-shield', 30),
  organizationPlaceholderRoute('/authentication-providers', 'organization-auth-providers', 'Authentication providers', 'Configure the identity providers available to this organization.', 'pi-key', 40),
  organizationPlaceholderRoute('/identity-mapping', 'organization-identity-mapping', 'Identity mapping', 'Map external identities to your organization users and roles.', 'pi-sort-alt', 50),
  {
    path: '/organization-settings',
    component: () => import('@/layouts/LayoutForPage.vue'),
    meta: {
      showInMainNav: false,
      label: 'Organization settings',
      sidebar: organizationSidebarItem('Organization settings', 'pi-cog', 60)
    } as RouteMeta,
    children: [
      {
        name: 'organization-settings',
        path: '',
        component: () => import('@/pages/OrganizationSettings.vue')
      }
    ]
  },
  organizationPlaceholderRoute('/billing-plan', 'organization-billing-plan', 'Billing & plan', 'Review subscription, billing, and usage details for this organization.', 'pi-credit-card', 70),

  {
    path: '/application/:applicationId',
    component: () => import('@/layouts/LayoutForPage.vue'),
    meta: {
      showInMainNav: false,
      label: 'Application Details',
      icon: 'microchip.svg',
      // Children declare their own sidebar items; any without one still render
      // this group's sidebar.
      sidebarGroup: 'application'
    }  as RouteMeta,
    children: [
      {
        name: 'application-details',
        path: '',
        meta: {
          sidebar: { group: 'application', label: 'Overview', icon: 'pi-objects-column', order: 10 } as SidebarItemMeta
        } as RouteMeta,
        component: () => import('@/pages/ApplicationDetails.vue'),
        props: (route) => ({ applicationId: route.params.applicationId })
      },
      {
        name: 'application-members',
        path: 'members',
        meta: {
          sidebar: { group: 'application', label: 'Members', icon: 'pi pi-users', order: 50 } as SidebarItemMeta
        } as RouteMeta,
        component: () => import('@/pages/MembersPage.vue'),
        props: (route) => ({ applicationId: route.params.applicationId })
      },
      {
        name: 'application-invite-email',
        path: 'invite-email',
        meta: {
          sidebar: { group: 'application', label: 'Invitation email', icon: 'pi pi-envelope', order: 60 } as SidebarItemMeta
        } as RouteMeta,
        component: () => import('@/pages/InviteEmailTemplatePage.vue'),
        props: (route) => ({ applicationId: route.params.applicationId })
      },
      {
        name: 'application-settings',
        path: 'settings',
        meta: {
          sidebar: { group: 'application', label: 'Application settings', icon: 'pi pi-cog', order: 70 } as SidebarItemMeta
        } as RouteMeta,
        component: () => import('@/pages/ApplicationSettings.vue'),
        props: (route) => ({ applicationId: route.params.applicationId })
      }
    ]
  },
  {
    path: '/projects',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: {
      showInMainNav: true,
      icon: 'folder.svg',
      label: 'Projects',
    } as RouteMeta,
  },
  {
    path: '/application/:applicationId/project/:projectId/structures',
    name: 'project-structures-wrapper',
    component: () => import('@/layouts/LayoutForPage.vue'),
    meta: {
      showInMainNav: false,
      icon: 'objects-column.svg',
      label: 'Project Entities',
      sidebarGroup: 'project'
    } as RouteMeta,
    children: [
      {
        name: 'project-structures',
        path: '',
        meta: {
          sidebar: { group: 'project', label: 'Entities', icon: 'pi pi-table', order: 10 } as SidebarItemMeta
        } as RouteMeta,
        component: () => import('@/pages/ProjectStructuresPage.vue'),
        props: (route) => ({
          applicationId: route.params.applicationId,
          projectId: route.params.projectId,
        }),
      },
    ],
  },
  {
    path: '/new-structure',
    component: () => import('@/pages/NewStructure.vue'),
    meta: {
      showInMainNav: false,
      label: 'New Entity',
    } as RouteMeta,
  },
  {
    path: '/settings',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: {
      showInMainNav: true,
      icon: 'settings.svg',
      label: 'Settings',
    } as RouteMeta,
  },
  {
    path: '/login',
    component: () => import('@/pages/login/Login.vue'),
    meta: {
      showInMainNav: false,
      authenticationRequired: false
    } as RouteMeta,
  },
  {
    path: '/signup',
    component: () => import('@/pages/signup/Signup.vue'),
    meta: {
      showInMainNav: false,
      authenticationRequired: false
    } as RouteMeta,
  },
  {
    path: '/signup/verify',
    component: () => import('@/pages/signup/VerifyEmail.vue'),
    meta: {
      showInMainNav: false,
      authenticationRequired: false
    } as RouteMeta,
  },
  {
    path: '/register',
    component: () => import('@/pages/signup/CompleteOrg.vue'),
    meta: {
      showInMainNav: false,
      authenticationRequired: false
    } as RouteMeta,
  },
  {
    path: '/invite/accept',
    component: () => import('@/pages/signup/AcceptInvitation.vue'),
    meta: {
      showInMainNav: false,
      authenticationRequired: false
    } as RouteMeta,
  },
  {
    path: '/device',
    meta: {
      authenticationRequired: true
    },
    component: () => import('@/pages/login/DeviceVerification.vue'),
  },
  {
    path: '/graphql',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: {
      showInMainNav: false,
      icon: 'objects-column.svg',
      label: 'GraphQLPlayground',
    } as RouteMeta,
    children: [
      {
        name: "GraphQLPlayground",
        path: '',
        component: () => import('@/pages/GraphQLPlayground.vue'),
      }
    ]
  },
  {
    name: 'github-install-callback',
    path: '/github/install/callback',
    component: () => import('@/pages/GitHubInstallCallback.vue'),
    meta: {
      showInMainNav: false,
      // Popup-mode callback runs in a fresh window with no STOMP connection;
      // it just postMessages installation_id + state back to the opener and
      // closes itself. No platform call, no auth needed.
      authenticationRequired: false,
    } as RouteMeta,
  },
];

export default pageRoutes
