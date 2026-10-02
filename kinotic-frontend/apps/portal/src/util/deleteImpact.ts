import { markRaw } from 'vue'
import { AppWindow, Cpu, GitBranch, KeyRound, PackageSearch, Rocket } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import type { Project } from '@kinotic-ai/management-api'
import { createDebug, type DeleteImpactRow } from '@kinotic-ai/frontend-common'
import type { ProjectTeardown } from '@/util/ProjectTeardown'

const debug = createDebug('delete-impact')

/** Looks up what deleting the project tears down. A lookup that fails counts as nothing found. */
export async function findProjectTeardown(project: Project): Promise<ProjectTeardown> {
    const projectId = project.id ?? ''
    // Each lookup stands alone, so one failing leaves the others' answers in place
    const [deployment, microservices, uis, machines, dependencies] = await Promise.all([
        lookUp('deployment', () => Kinotic.projects.findDeployment(projectId)),
        lookUp('microservice deployments', () => Kinotic.microserviceDeployments.findAllForProject(projectId)),
        lookUp('UI deployments', () => Kinotic.uiDeployments.findAllForProject(projectId)),
        lookUp('machines', () => Kinotic.machines.findProjectMachines(projectId)),
        lookUp('dependencies', () => Kinotic.projects.findDependencies(projectId))
    ])
    return {
        project,
        deployed: deployment != null,
        microservices: (microservices ?? []).map(microservice => microservice.name),
        uis: (uis ?? []).map(ui => ui.name),
        machines: (machines ?? []).map(machine => machine.displayName ?? machine.id ?? ''),
        hasDependencies: dependencies != null
    }
}

/**
 * The rows listing what deleting the projects removes beyond the project records, and what it
 * keeps. Item names carry their project's name when more than one project is deleted.
 */
export function teardownRows(teardowns: ProjectTeardown[]): { removes: DeleteImpactRow[], kept: DeleteImpactRow[] } {
    const several = teardowns.length > 1
    const named = (teardown: ProjectTeardown, names: string[]) =>
        several ? names.map(name => `${teardown.project.name}/${name}`) : names

    const deployed = teardowns.filter(teardown => teardown.deployed)
    const microservices = teardowns.flatMap(teardown => named(teardown, teardown.microservices))
    const uis = teardowns.flatMap(teardown => named(teardown, teardown.uis))
    // machine names already lead with their project's name
    const machines = teardowns.flatMap(teardown => teardown.machines)
    const inventories = teardowns.filter(teardown => teardown.hasDependencies)
    const repositories = teardowns.map(teardown => teardown.project.repoFullName).filter(Boolean)

    const removes: DeleteImpactRow[] = []
    if (deployed.length > 0) {
        removes.push({
            icon: markRaw(Rocket),
            label: several
                ? `${count(deployed.length, 'deployment')} and the workloads syncing them from GitHub`
                : 'Its deployment and the workload syncing it from GitHub'
        })
    }
    if (microservices.length > 0) {
        removes.push({ icon: markRaw(Cpu), label: `${count(microservices.length, 'microservice deployment')} and their runtime VMs`, items: microservices })
    }
    if (uis.length > 0) {
        removes.push({ icon: markRaw(AppWindow), label: `${count(uis.length, 'UI deployment')} and their published site files`, items: uis })
    }
    if (machines.length > 0) {
        removes.push({ icon: markRaw(KeyRound), label: `${count(machines.length, 'machine identity', 'machine identities')} and their credentials`, items: machines })
    }
    if (inventories.length > 0) {
        removes.push({
            icon: markRaw(PackageSearch),
            label: several ? `${count(inventories.length, 'dependency inventory', 'dependency inventories')} (SBOM)` : 'Its dependency inventory (SBOM)'
        })
    }

    const kept: DeleteImpactRow[] = []
    if (repositories.length > 0) {
        kept.push({ icon: markRaw(GitBranch), label: several ? 'Their GitHub repositories' : 'Its GitHub repository', items: repositories })
    }
    return { removes, kept }
}

export function count(n: number, singular: string, plural = `${singular}s`): string {
    return `${n} ${n === 1 ? singular : plural}`
}

async function lookUp<T>(what: string, request: () => Promise<T>): Promise<T | null> {
    try {
        return await request()
    } catch (error) {
        debug('Failed to look up the %s of a project: %O', what, error)
        return null
    }
}
