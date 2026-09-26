<template>
  <div class="flex flex-col">
    <PageHeader title="SBOM"
                description="The project's software bill of materials: every package its bun.lock resolves, in CycloneDX. The last step of a deployment generates it whenever the dependencies changed.">
      <template #actions>
        <Button v-if="documentText" label="Download" icon="pi pi-download" size="small" severity="secondary" @click="download" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div v-if="loading" class="p-6 text-sm text-muted-color">Loading SBOM…</div>
    <div v-else-if="!sbom" class="p-6 text-sm text-muted-color">
      This project has no SBOM yet. The last step of a deployment generates one from the project's bun.lock.
    </div>

    <template v-else>
      <div class="mb-4 flex flex-wrap items-center gap-4">
        <span :title="current ? 'The deployed commit has these dependencies' : 'The last synced commit has other dependencies; its deployment did not generate their SBOM'">
          <Tag :value="current ? 'Current' : 'Outdated'" :severity="current ? 'success' : 'warn'" />
        </span>
        <span class="text-sm">
          Generated from <span class="font-mono" :title="sbom.commitSha">{{ shortSha(sbom.commitSha) }}</span>
        </span>
        <span class="text-xs text-muted-color">{{ DatetimeUtil.formatRelativeDate(sbom.generated) }}</span>
        <span v-if="documentText" class="text-sm text-muted-color">{{ components.length }} components</span>
      </div>

      <div class="mb-3 flex flex-wrap items-center gap-3">
        <IconField icon-position="left" class="w-full sm:w-80">
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" placeholder="Search packages or licenses" class="w-full" />
        </IconField>
        <SelectButton v-model="scope" :options="SCOPE_OPTIONS" :allow-empty="false" size="small" />
      </div>

      <DataTable :value="visibleComponents" size="small" paginator :rows="PAGE_SIZE" :loading="documentLoading" data-key="ref">
        <Column header="Package" style="width: 40%">
          <template #body="{ data }"><span class="font-mono text-sm break-all">{{ data.name }}</span></template>
        </Column>
        <Column header="Version" style="width: 15%">
          <template #body="{ data }"><span class="font-mono text-sm">{{ data.version }}</span></template>
        </Column>
        <Column header="License" style="width: 25%">
          <template #body="{ data }"><span class="text-sm">{{ data.license || '—' }}</span></template>
        </Column>
        <Column header="Scope" style="width: 20%">
          <template #body="{ data }">
            <Tag :value="data.scope" :severity="data.scope === ComponentScope.RUNTIME ? 'info' : 'secondary'" />
          </template>
        </Column>
        <template #empty>
          <span class="text-sm text-muted-color">{{ documentLoading ? 'Reading the SBOM…' : 'No component matches.' }}</span>
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
import { DatetimeUtil, PageHeader, errorMessage, shortSha } from '@kinotic-ai/frontend-common'
import { Kinotic } from '@kinotic-ai/core'
import type { ProjectDeployment, ProjectSbom } from '@kinotic-ai/management-api'

/**
 * The project's SBOM: when and from which commit it was generated, whether the dependencies of
 * the last synced commit are still the ones it lists, and its components, searchable by name
 * and license and filtered by how the project uses them. The CycloneDX document is read straight
 * from the organization's storage through a short-lived URL, and can be downloaded as it is.
 */
const props = defineProps<{
  applicationId: string
  projectId: string
}>()

/** How the project uses a component, from the npm properties of the CycloneDX taxonomy. */
enum ComponentScope {
  RUNTIME = 'Runtime',
  DEVELOPMENT = 'Development',
  OPTIONAL = 'Optional'
}

/** The parts of a CycloneDX component the page reads. */
interface CycloneDxComponent {
  'bom-ref'?: string
  group?: string
  name: string
  version?: string
  scope?: string
  licenses?: Array<{ expression?: string, license?: { id?: string, name?: string } }>
  properties?: Array<{ name: string, value: string }>
}

interface ComponentRow {
  ref: string
  name: string
  version: string
  license: string
  scope: ComponentScope
}

const PAGE_SIZE = 50
const ALL_SCOPES = 'All'
const SCOPE_OPTIONS = [ALL_SCOPES, ComponentScope.RUNTIME, ComponentScope.DEVELOPMENT, ComponentScope.OPTIONAL]

const sbom = ref<ProjectSbom | null>(null)
const deployment = ref<ProjectDeployment | null>(null)
const documentText = ref<string | null>(null)
const components = ref<ComponentRow[]>([])
const search = ref('')
const scope = ref<string>(ALL_SCOPES)
const loading = ref(true)
const documentLoading = ref(false)
const error = ref<string | null>(null)

/** Whether the last synced commit's dependencies are the ones the SBOM lists. */
const current = computed(() => sbom.value !== null && deployment.value?.artifacts?.dependencyHash === sbom.value.dependencyHash)

const visibleComponents = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return components.value.filter(component => (scope.value === ALL_SCOPES || component.scope === scope.value)
    && (!needle || component.name.toLowerCase().includes(needle) || component.license.toLowerCase().includes(needle)))
})

onMounted(async () => {
  try {
    const [found, deployed] = await Promise.all([
      Kinotic.projects.findSbom(props.projectId),
      Kinotic.projects.findDeployment(props.projectId),
    ])
    sbom.value = found
    deployment.value = deployed
  } catch (err) {
    error.value = errorMessage(err, 'The SBOM could not be loaded')
  } finally {
    loading.value = false
  }
  if (sbom.value !== null) {
    await loadDocument()
  }
})

async function loadDocument(): Promise<void> {
  documentLoading.value = true
  try {
    const url = await Kinotic.projects.findSbomDocumentUrl(props.projectId)
    if (url !== null) {
      const response = await fetch(url)
      if (!response.ok) {
        throw new Error(`The SBOM document could not be read: ${response.status} ${response.statusText}`)
      }
      documentText.value = await response.text()
      const bom = JSON.parse(documentText.value) as { components?: CycloneDxComponent[] }
      components.value = (bom.components ?? []).map(toRow).sort((a, b) => a.name.localeCompare(b.name))
    }
  } catch (err) {
    error.value = errorMessage(err, 'The SBOM document could not be read')
  } finally {
    documentLoading.value = false
  }
}

function toRow(component: CycloneDxComponent): ComponentRow {
  const properties = new Map((component.properties ?? []).map(property => [property.name, property.value]))
  let scope: ComponentScope
  // a development-only package is scoped optional too, so its property decides first
  if (properties.get('cdx:npm:package:development') === 'true') {
    scope = ComponentScope.DEVELOPMENT
  } else if (component.scope === 'optional' || properties.get('cdx:npm:package:optional') === 'true') {
    scope = ComponentScope.OPTIONAL
  } else {
    scope = ComponentScope.RUNTIME
  }
  return {
    ref: component['bom-ref'] ?? `${component.group}/${component.name}@${component.version}`,
    name: component.group ? `${component.group}/${component.name}` : component.name,
    version: component.version ?? '',
    license: (component.licenses ?? [])
      .map(entry => entry.expression ?? entry.license?.id ?? entry.license?.name ?? '')
      .filter(license => license !== '')
      .join(', '),
    scope,
  }
}

function download(): void {
  if (documentText.value !== null && sbom.value !== null) {
    const url = URL.createObjectURL(new Blob([documentText.value], { type: 'application/vnd.cyclonedx+json' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${props.projectId}-${shortSha(sbom.value.commitSha)}.cdx.json`
    link.click()
    URL.revokeObjectURL(url)
  }
}
</script>
