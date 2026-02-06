import { NavItem } from '@/components/NavItem'
import { Pageable } from '@kinotic/continuum-client'
import { Application, Structures } from '@kinotic/structures-api'
import { computed, type ComputedRef, markRaw, reactive, type Reactive } from 'vue'
import type { NavigationGuardNext, RouteLocationNormalized, Router, RouteRecordRaw } from 'vue-router'
import { createDebug } from '@/util/debug'

const debug = createDebug('application-state');

export interface IApplicationState {
    mainNavItems: NavItem[]
    bottomNavItems: NavItem[]
    selectedNavItem: NavItem | null
    allApplications: Application[]
    breadcrumbItems: ComputedRef<NavItem[]>

    countsLoaded: boolean
    projectsCount: number
    structuresCount: number

    currentApplication: Application | null

    loadAllApplications(): Promise<void>

    initialize(router: Router): void
}

class ApplicationState implements IApplicationState {
    public mainNavItems: NavItem[] = markRaw<NavItem[]>([])
    public bottomNavItems: NavItem[] = markRaw<NavItem[]>([])
    public selectedNavItem: NavItem | null = null
    private router!: Router
    private allNavItems: NavItem[] = []
    public allApplications: Application[] = []
    public breadcrumbItems: ComputedRef<NavItem[]>

    public countsLoaded = false
    public projectsCount = 0
    public structuresCount = 0

    public _currentApplication: Application | null = null

    constructor() {
        this.breadcrumbItems = computed<NavItem[]>(() => this.getBreadcrumbItems())
    }

    public initialize(router: Router): void {
        this.router = router

        if (router.options.routes) {
            router.options.routes.forEach(route => {
                if (this.showInMainNav(route)) {
                    const navItem = this.createNavItem(route, '')
                    this.mainNavItems.push(navItem)
                    this.allNavItems.push(navItem)
                }
                if (this.showInBottomNav(route)) {
                    const navItem = this.createNavItem(route, '')
                    this.bottomNavItems.push(navItem)
                    this.allNavItems.push(navItem)
                }
                if (this.showInBreadcrumbs(route)) {
                    this.allNavItems.push(this.createNavItem(route, ''))
                }
            })
        }

        router.beforeResolve((to: RouteLocationNormalized, _, next: NavigationGuardNext) => {
            this.updateSelectedNavItem(to.path)
            next()
        })

        this.updateSelectedNavItem(router.currentRoute.value.path)
    }

    public set currentApplication(app: Application | null) {
        this._currentApplication = app
        this.countsLoaded = false
        
        if (app) {
            Promise.all([
                Structures.getProjectService().countForApplication(app.id),
                Structures.getStructureService().countForApplication(app.id)
            ]).then(([projectsCount, structuresCount]) => {
                this.projectsCount = projectsCount
                this.structuresCount = structuresCount
                this.countsLoaded = true
            }).catch(error => {
                debug('Failed to load counts: %O', error)
                this.projectsCount = -1
                this.structuresCount = -1
                this.countsLoaded = true
            })
        }
    }

    public get currentApplication(): Application | null {
        return this._currentApplication
    }

    public async loadAllApplications(): Promise<void> {
        try {
            const service = Structures.getApplicationService()
            const pageable = Pageable.create(0, 1000)
            const result = await service.findAll(pageable)
            this.allApplications = result.content ?? []
        } catch (error) {
            debug('Failed to load all applications: %O', error)
            this.allApplications = []
        }
    }

    private createNavItem(route: RouteRecordRaw, parentPath: string): NavItem {
        const fullPath = this.resolveFullPath(route.path, parentPath)
        const navItem = new NavItem(
            route.meta?.icon as string || '',
            route.meta?.label as string || route.name?.toString() || '',
            fullPath,
            async () => { await this.router.push(fullPath) }
        )

        if (route.children?.length) {
            route.children.forEach(child => {
                if (this.showInMainNav(child) || this.showInBottomNav(child) || this.showInBreadcrumbs(child)) {
                    const childItem = this.createNavItem(child, fullPath)
                    navItem.addChild(childItem)
                    this.allNavItems.push(childItem)
                }
            })
        }

        return navItem
    }

    private resolveFullPath(routePath: string, parentPath: string): string {
        if (routePath.startsWith('/') || !parentPath) {
            return routePath
        }
        return `${parentPath}${parentPath.endsWith('/') ? '' : '/'}${routePath}`
    }

    private updateSelectedNavItem(path: string): void {
        let bestMatch: NavItem | null = null
        let longestMatchLength = 0

        const checkNavItem = (navItem: NavItem): void => {
            if (path.startsWith(navItem.path) && navItem.path.length > longestMatchLength) {
                bestMatch = navItem
                longestMatchLength = navItem.path.length
            }
            navItem.children.forEach(checkNavItem)
        }

        [...this.mainNavItems, ...this.bottomNavItems].forEach(checkNavItem)
        this.selectedNavItem = bestMatch
    }

    private getBreadcrumbItems(): NavItem[] {
        if (!this.router) return []
        const currentPath = this.router.currentRoute.value.path
        const items: NavItem[] = []

        this.allNavItems.forEach(navItem => {
            if (currentPath.startsWith(navItem.path) && this.showInBreadcrumbsForPath(navItem.path)) {
                items.push(navItem)
            }
        })

        return items.sort((a, b) => a.path.length - b.path.length)
    }

    private showInMainNav(routeConfig: RouteRecordRaw): boolean {
        return routeConfig.meta?.showInMainNav === true
    }

    private showInBottomNav(routeConfig: RouteRecordRaw): boolean {
        return routeConfig.meta?.showInBottomNav === true
    }

    private showInBreadcrumbs(routeConfig: RouteRecordRaw): boolean {
        return routeConfig.meta?.showInBreadcrumbs === true
    }

    private showInBreadcrumbsForPath(path: string): boolean {
        const route = this.router.getRoutes().find(r => r.path === path)
        return route?.meta?.showInBreadcrumbs === true
    }
}

export const APPLICATION_STATE: Reactive<IApplicationState> = reactive<IApplicationState>(new ApplicationState())
