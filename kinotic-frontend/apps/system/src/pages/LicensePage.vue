<template>
  <div>
    <PageHeader title="License"
                description="The Kinotic Enterprise License the cluster runs on. Installing a key applies it to every node without a restart.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div class="flex flex-col gap-4">
      <Message v-if="!loading && !error && !license" severity="info" :closable="false">
        No license key is installed, so the Enterprise Features are off. Paste a key below to turn them on.
      </Message>

      <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <StatCard v-for="stat in stats" :key="stat.label" :icon="stat.icon" :tint="stat.tint" :label="stat.label"
                  :value="stat.tag ? undefined : stat.value" :detail="stat.detail" :loading="loading && !license">
          <template v-if="stat.tag" #default>
            <Tag :value="stat.value" :severity="stat.tag" />
          </template>
        </StatCard>
      </div>

      <DashboardSection :icon="Sparkles" :tint="TINTS.ink" title="Enterprise Features" :count="license?.features.length"
                        description="The features the license turns on while it is active or in its grace period.">
        <div class="flex flex-wrap gap-2 p-5">
          <span v-if="!license || license.features.length === 0" class="text-sm text-muted-color">None</span>
          <span v-for="feature in license?.features ?? []" :key="feature"
                class="inline-flex items-center rounded-md bg-surface-100 px-2 py-1 text-xs font-medium text-surface-700 dark:bg-surface-800 dark:text-surface-200">
            {{ FEATURE_NAMES[feature] ?? feature }}
          </span>
        </div>
      </DashboardSection>

      <DashboardSection :icon="Server" :tint="TINTS.ink" title="Cluster id"
                        description="License this cluster at licenses.kinotic.ai, which takes one of your license's seats and installs the key it issues here; from a computer that cannot reach it, copy the id there. The key works on this cluster alone, and the cluster renews it from there before it expires.">
        <div class="flex flex-wrap items-center gap-3 p-5">
          <template v-if="clusterId">
            <code class="break-all font-mono text-sm">{{ clusterId }}</code>
            <Button label="Copy" icon="pi pi-copy" severity="secondary" outlined size="small" @click="copyClusterId" />
            <Button as="a" :href="licenseClusterUrl(clusterId)" label="License this cluster" icon="pi pi-external-link" size="small"
                    @click="startHandoff" />
          </template>
          <span v-else-if="clusterIdError" class="text-sm text-muted-color">{{ clusterIdError }}</span>
          <span v-else-if="!loading" class="text-sm text-muted-color">
            This cluster has no id: it does not run in Kubernetes, or could not read its kube-system namespace. Only a key
            bound to no cluster works here.
          </span>
        </div>
      </DashboardSection>

      <DashboardSection :icon="KeyRound" :tint="TINTS.ink" title="Install a license key"
                        description="Paste a key issued by Kinotic. A key replaces the installed one, and the cluster runs on it straight away.">
        <div class="flex flex-col gap-3 p-5">
          <Textarea v-model="licenseKey" rows="5" class="w-full font-mono text-xs" placeholder="eyJhbGciOiJFUzI1NiJ9…"
                    aria-label="License key" />
          <div>
            <Button label="Install key" icon="pi pi-upload" :loading="installing" :disabled="licenseKey.trim() === ''"
                    @click="install" />
          </div>
        </div>
      </DashboardSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'
import { BadgeCheck, CalendarClock, CalendarX, KeyRound, Server, Sparkles, User } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import { EnterpriseFeature, LicenseStatus, type License } from '@kinotic-ai/system-api'
import { DashboardSection, PageHeader, StatCard, TINTS, errorMessage, showErrorToast } from '@kinotic-ai/frontend-common'

// the account page of Minds Ignited's license server, which adds the cluster its clusterId query parameter names and
// sends the browser back to its returnUrl with the cluster's key in the fragment, #key=<key>
const LICENSE_ACCOUNT_URL = 'https://licenses.kinotic.ai/account'
const KEY_FRAGMENT = '#key='
// the session storage entry the License this cluster button sets, which lets the key that comes back install itself
const HANDOFF_STORAGE_KEY = 'kinotic.license.handoff'

const FEATURE_NAMES: Record<EnterpriseFeature, string> = {
  [EnterpriseFeature.AZURE_KEY_VAULT]: 'Azure Key Vault',
  [EnterpriseFeature.ENTERPRISE_IDENTITY_PROVIDERS]: 'Enterprise identity providers'
}

const STATUS_TAGS: Record<LicenseStatus, { label: string, severity: 'success' | 'warn' | 'danger' }> = {
  [LicenseStatus.ACTIVE]: { label: 'Active', severity: 'success' },
  [LicenseStatus.GRACE_PERIOD]: { label: 'Grace period', severity: 'warn' },
  [LicenseStatus.EXPIRED]: { label: 'Expired', severity: 'danger' }
}

/** One tile of the license's summary. */
interface LicenseStat {
  label: string
  icon: Component
  /** One of TINTS. */
  tint: string
  value: string
  detail?: string
  tag?: 'success' | 'warn' | 'danger'
}

const toast = useToast()

const license = ref<License | null>(null)
const clusterId = ref<string | null>(null)
const clusterIdError = ref<string | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const licenseKey = ref('')
const installing = ref(false)

const stats = computed<LicenseStat[]>(() => {
  const current = license.value
  const status = current ? STATUS_TAGS[current.status] : null
  return [
    {
      label: 'Licensee', icon: User, tint: TINTS.ink, value: current?.licensee ?? '—',
      detail: current ? (current.clusterId ? 'For this cluster' : 'For any cluster') : undefined
    },
    {
      label: 'Status', icon: BadgeCheck, tint: TINTS.ink,
      value: status?.label ?? 'Not installed', tag: status?.severity ?? 'danger'
    },
    {
      label: 'Expires', icon: CalendarClock, tint: TINTS.ink, value: formatDate(current?.expiresAt),
      detail: current?.issuedAt ? `Issued ${formatDate(current.issuedAt)}` : undefined
    },
    {
      label: 'Grace period ends', icon: CalendarX, tint: TINTS.ink, value: formatDate(current?.graceEndsAt),
      detail: current ? 'Features turn off after this date' : undefined
    }
  ]
})

function formatDate(date: string | null | undefined): string {
  return date ? new Date(date).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : '—'
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  clusterIdError.value = null
  // the license and the cluster id load independently, so one failing still shows the other
  const [current, id] = await Promise.allSettled([Kinotic.licenses.findCurrent(), Kinotic.licenses.findClusterId()])
  if (current.status === 'fulfilled') {
    license.value = current.value
  } else {
    error.value = errorMessage(current.reason, 'Failed to load the license')
  }
  if (id.status === 'fulfilled') {
    clusterId.value = id.value
  } else {
    clusterId.value = null
    clusterIdError.value = errorMessage(id.reason, 'Failed to load the cluster id')
  }
  loading.value = false
}

function licenseClusterUrl(id: string): string {
  const returnUrl = window.location.origin + window.location.pathname
  return `${LICENSE_ACCOUNT_URL}?clusterId=${encodeURIComponent(id)}&returnUrl=${encodeURIComponent(returnUrl)}`
}

function startHandoff(): void {
  try {
    window.sessionStorage.setItem(HANDOFF_STORAGE_KEY, 'started')
  } catch {
    // without session storage the handed-back key waits in the form for Install key
  }
}

// Takes the key the license server handed back in the fragment and drops the fragment so a reload takes nothing. The
// key installs itself only when this tab started the handoff; a key from any other link waits in the form for Install
// key, so a crafted link cannot replace the installed key unseen.
async function takeHandedBackKey(): Promise<void> {
  if (!window.location.hash.startsWith(KEY_FRAGMENT)) {
    return
  }
  const encoded = window.location.hash.substring(KEY_FRAGMENT.length)
  window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search)
  let started = false
  try {
    started = window.sessionStorage.getItem(HANDOFF_STORAGE_KEY) !== null
    window.sessionStorage.removeItem(HANDOFF_STORAGE_KEY)
  } catch {
    started = false
  }
  try {
    licenseKey.value = decodeURIComponent(encoded)
  } catch {
    toast.add({ severity: 'error', summary: 'The key handed back is malformed', detail: 'Copy it from your account at licenses.kinotic.ai.', life: 8000 })
    return
  }
  if (started) {
    await install()
  } else {
    toast.add({ severity: 'info', summary: 'Check the key below', detail: 'Select Install key to install it.', life: 8000 })
  }
}

async function copyClusterId(): Promise<void> {
  try {
    await navigator.clipboard.writeText(clusterId.value ?? '')
    toast.add({ severity: 'success', summary: 'Cluster id copied', life: 2500 })
  } catch (err) {
    showErrorToast(toast, 'Failed to copy the cluster id', err)
  }
}

async function install(): Promise<void> {
  installing.value = true
  try {
    license.value = await Kinotic.licenses.install(licenseKey.value.trim())
    licenseKey.value = ''
    toast.add({ severity: 'success', summary: 'License key installed', life: 4000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to install the license key', err, { life: 8000 })
  } finally {
    installing.value = false
  }
}

onMounted(async () => {
  await takeHandedBackKey()
  await load()
})
</script>
