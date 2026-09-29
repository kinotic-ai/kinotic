<template>
  <div class="flex flex-col">
    <PageHeader title="SBOM"
                description="The project's software bill of materials: every package its bun.lock installs and how the project depends on it. The last step of a deployment reads it whenever the dependencies changed.">
      <template #actions>
        <Button v-if="dependencies" label="Download" icon="pi pi-download" size="small" severity="secondary" @click="download" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div v-if="loading" class="p-6 text-sm text-muted-color">Loading SBOM…</div>
    <div v-else-if="!dependencies" class="p-6 text-sm text-muted-color">
      This project has no SBOM of its current dependencies yet. The last step of a deployment reads it from the project's bun.lock whenever the dependencies change.
    </div>

    <template v-else>
      <div class="mb-4 flex flex-wrap items-center gap-4">
        <span class="text-sm">
          Dependencies of <span class="font-mono" :title="commitSha">{{ shortSha(commitSha) }}</span>
        </span>
        <span class="text-sm text-muted-color">{{ rows.length }} packages, {{ dependencies.direct.length }} declared by the project</span>
      </div>

      <div class="mb-3 flex flex-wrap items-center gap-3">
        <IconField icon-position="left" class="w-full sm:w-80">
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" placeholder="Search packages" class="w-full" />
        </IconField>
        <SelectButton v-model="scope" :options="SCOPE_OPTIONS" :allow-empty="false" size="small" />
      </div>

      <DataTable :value="visibleRows" size="small" paginator :rows="PAGE_SIZE" data-key="purl">
        <Column header="Package" style="width: 45%">
          <template #body="{ data }"><span class="font-mono text-sm break-all">{{ data.name }}</span></template>
        </Column>
        <Column header="Version" style="width: 20%">
          <template #body="{ data }"><span class="font-mono text-sm break-all">{{ data.version }}</span></template>
        </Column>
        <Column header="Scope" style="width: 15%">
          <template #body="{ data }">
            <Tag :value="data.scope" :severity="data.scope === PackageScope.RUNTIME ? 'info' : 'secondary'" />
          </template>
        </Column>
        <Column header="Dependency" style="width: 20%">
          <template #body="{ data }">
            <span class="text-sm" :class="{ 'text-muted-color': !data.direct }">{{ data.direct ? 'Direct' : 'Transitive' }}</span>
          </template>
        </Column>
        <template #empty>
          <span class="text-sm text-muted-color">No package matches.</span>
        </template>
      </DataTable>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { PageHeader, errorMessage, shortSha } from '@kinotic-ai/frontend-common'
import { Kinotic } from '@kinotic-ai/core'
import type { ProjectDependencies, ProjectDeployment } from '@kinotic-ai/management-api'

/**
 * The project's SBOM, the one of the dependencies of the last synced commit: every package its
 * bun.lock installs, searchable by name and filtered by how the project uses it. Download saves it
 * as a CycloneDX 1.6 document, the dependency graph included.
 */
const props = defineProps<{
  applicationId: string
  projectId: string
}>()

/** How the project uses a package. */
enum PackageScope {
  RUNTIME = 'Runtime',
  DEVELOPMENT = 'Development',
  OPTIONAL = 'Optional'
}

interface PackageRow {
  purl: string
  /** The package's name, with its scope */
  name: string
  version: string
  scope: PackageScope
  /** Whether one of the project's package.json files declares it */
  direct: boolean
}

const PAGE_SIZE = 50
const ALL_SCOPES = 'All'
const SCOPE_OPTIONS = [ALL_SCOPES, PackageScope.RUNTIME, PackageScope.DEVELOPMENT, PackageScope.OPTIONAL]
const PURL_PREFIX = 'pkg:npm/'

const deployment = ref<ProjectDeployment | null>(null)
const dependencies = ref<ProjectDependencies | null>(null)
const search = ref('')
const scope = ref<string>(ALL_SCOPES)
const loading = ref(true)
const error = ref<string | null>(null)

/** The last synced commit, whose dependencies the SBOM lists. */
const commitSha = computed(() => deployment.value?.artifacts?.commitSha ?? '')

const rows = computed<PackageRow[]>(() => {
  const tree = dependencies.value
  let ret: PackageRow[] = []
  if (tree) {
    const development = new Set(tree.development)
    const optional = new Set(tree.optional)
    const direct = new Set(tree.direct)
    ret = tree.packages
      .map((purl, position) => ({
        purl,
        ...nameAndVersionOf(purl),
        scope: development.has(position) ? PackageScope.DEVELOPMENT : optional.has(position) ? PackageScope.OPTIONAL : PackageScope.RUNTIME,
        direct: direct.has(position),
      }))
      .sort((a, b) => a.name.localeCompare(b.name))
  }
  return ret
})

const visibleRows = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return rows.value.filter(row => (scope.value === ALL_SCOPES || row.scope === scope.value)
    && (!needle || row.name.toLowerCase().includes(needle)))
})

onMounted(async () => {
  try {
    const [found, tree] = await Promise.all([Kinotic.projects.findDeployment(props.projectId),
                                             Kinotic.projects.findDependencies(props.projectId)])
    deployment.value = found
    dependencies.value = tree
  } catch (err) {
    error.value = errorMessage(err, 'The SBOM could not be loaded')
  } finally {
    loading.value = false
  }
})

// pkg:npm/<name>@<version> with each part percent-encoded, so the one literal @ ends the name
function nameAndVersionOf(purl: string): { name: string, version: string } {
  const body = purl.slice(PURL_PREFIX.length)
  const at = body.indexOf('@')
  return {
    name: body.slice(0, at).split('/').map(decodeURIComponent).join('/'),
    version: decodeURIComponent(body.slice(at + 1)),
  }
}

// CycloneDX has no development scope: a development package is excluded, the scope of what is not
// reachable at runtime, and carries the npm taxonomy's development property. A package's bom-ref
// is its purl decoded, the form CycloneDX tools expect in the dependency graph's refs
function toCycloneDx(tree: ProjectDependencies, projectRows: PackageRow[]): object {
  const cycloneDxScopes: Record<PackageScope, string> = {
    [PackageScope.RUNTIME]: 'required',
    [PackageScope.OPTIONAL]: 'optional',
    [PackageScope.DEVELOPMENT]: 'excluded',
  }
  const bomRefs = new Map(projectRows.map(row => [row.purl, `${PURL_PREFIX}${row.name}@${row.version}`]))
  const bomRefOf = (position: number) => bomRefs.get(tree.packages[position]!)!
  const dependsOn = tree.packages.map((): string[] => [])
  for (const [dependent, dependency] of tree.edges) {
    dependsOn[dependent]!.push(bomRefOf(dependency))
  }
  return {
    bomFormat: 'CycloneDX',
    specVersion: '1.6',
    serialNumber: `urn:uuid:${crypto.randomUUID()}`,
    version: 1,
    metadata: {
      timestamp: new Date().toISOString(),
      component: { type: 'application', 'bom-ref': props.projectId, name: props.projectId },
    },
    components: projectRows.map(row => {
      const slash = row.name.startsWith('@') ? row.name.indexOf('/') : -1
      return {
        type: 'library',
        'bom-ref': bomRefs.get(row.purl),
        ...(slash === -1 ? { name: row.name } : { group: row.name.slice(0, slash), name: row.name.slice(slash + 1) }),
        version: row.version,
        purl: row.purl,
        scope: cycloneDxScopes[row.scope],
        ...(row.scope === PackageScope.DEVELOPMENT ? { properties: [{ name: 'cdx:npm:package:development', value: 'true' }] } : {}),
      }
    }),
    dependencies: [
      { ref: props.projectId, dependsOn: tree.direct.map(bomRefOf) },
      ...tree.packages.map((_, position) => ({ ref: bomRefOf(position), dependsOn: dependsOn[position]! })),
    ],
  }
}

function download(): void {
  if (dependencies.value !== null) {
    const bom = JSON.stringify(toCycloneDx(dependencies.value, rows.value), null, 2)
    const url = URL.createObjectURL(new Blob([bom], { type: 'application/vnd.cyclonedx+json' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${props.projectId}-${shortSha(commitSha.value)}.cdx.json`
    link.click()
    URL.revokeObjectURL(url)
  }
}
</script>
