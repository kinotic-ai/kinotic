<template>
  <div>
    <PageHeader title="Settings"
                description="Name, description, tenancy, primary UI, and the emails this application sends." />

    <Tabs lazy :value="activeTab" @update:value="selectTab">
      <TabList>
        <Tab value="general">
          <span class="flex items-center gap-2"><SlidersHorizontal :size="18" :stroke-width="1.75" aria-hidden="true" />General</span>
        </Tab>
        <Tab value="invitation-email">
          <span class="flex items-center gap-2"><Mail :size="18" :stroke-width="1.75" aria-hidden="true" />Invitation email</span>
        </Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="general">
          <form class="flex max-w-[880px] flex-col gap-4 pt-4" @submit.prevent="saveSettings">
            <DashboardSection :icon="Info" :tint="TINTS.blue" title="About"
                              description="How the application is identified and described.">
              <div class="divide-y divide-surface-200 dark:divide-surface-700">
                <div :class="ROW_CLASS">
                  <div>
                    <span :class="LABEL_CLASS">Application ID</span>
                    <p :class="HELP_CLASS">Derived from its name when it was created; it can't be changed.</p>
                  </div>
                  <div class="flex items-center gap-2 rounded-lg border border-surface-200 bg-surface-50 py-1.5 pl-3 pr-1.5 dark:border-surface-700 dark:bg-surface-800/60">
                    <Lock :size="14" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
                    <span class="min-w-0 flex-1 truncate font-mono text-[0.8125rem] text-surface-800 dark:text-surface-100">{{ applicationId }}</span>
                    <Button type="button" :icon="copied ? 'pi pi-check' : 'pi pi-copy'" severity="secondary" text size="small"
                            :aria-label="copied ? 'Copied' : 'Copy application ID'" v-tooltip.top="copied ? 'Copied' : 'Copy'" @click="copyId" />
                  </div>
                </div>

                <div :class="ROW_CLASS">
                  <div>
                    <label for="app-description" :class="LABEL_CLASS">Description</label>
                    <p :class="HELP_CLASS">Shown in the application list.</p>
                  </div>
                  <Textarea id="app-description" v-model="appDescription" rows="3" auto-resize class="w-full" placeholder="What this application does" />
                </div>
              </div>
            </DashboardSection>

            <DashboardSection :icon="UsersRound" :tint="TINTS.blue" title="Sign-in and tenancy"
                              description="How the application's users are kept apart and where their browser flows land.">
              <div class="divide-y divide-surface-200 dark:divide-surface-700">
                <div :class="ROW_CLASS">
                  <div>
                    <label for="app-tenancy" :class="LABEL_CLASS">Tenant per user</label>
                    <p :class="HELP_CLASS">Each user of this application gets their own isolated tenant. Applies to users created after enabling.</p>
                  </div>
                  <div class="flex items-center gap-3">
                    <ToggleSwitch input-id="app-tenancy" v-model="tenantPerUser" />
                    <span class="text-sm text-surface-700 dark:text-surface-200">{{ tenantPerUser ? 'On' : 'Off' }}</span>
                  </div>
                </div>

                <div :class="ROW_CLASS">
                  <div>
                    <label for="app-primary-ui" :class="LABEL_CLASS">Primary UI</label>
                    <p :class="HELP_CLASS">The published UI this application's browser flows, such as OAuth consent, return to.</p>
                  </div>
                  <div>
                    <Select input-id="app-primary-ui" v-model="primaryUiId" :options="uiOptions" placeholder="Not set" show-clear class="w-full"
                            :disabled="uiOptions.length === 0" />
                    <p v-if="uiOptions.length === 0" class="mt-1.5 text-[0.8125rem] text-muted-color">
                      No UI has been published yet; one appears here once a deployment publishes it.
                    </p>
                  </div>
                </div>
              </div>
            </DashboardSection>

            <!-- the bar appears only while there is something to save, and stays in reach at the bottom -->
            <div v-if="dirty"
                 class="sticky bottom-4 z-10 flex items-center justify-between gap-3 rounded-xl border border-surface-200 bg-surface-0/95 px-4 py-3 shadow-lg backdrop-blur dark:border-surface-700 dark:bg-surface-900/95">
              <span class="flex items-center gap-2 text-sm text-surface-700 dark:text-surface-200">
                <span class="h-2 w-2 rounded-full bg-amber-500" aria-hidden="true" />
                You have unsaved changes
              </span>
              <div class="flex gap-2">
                <Button type="button" label="Discard" severity="secondary" outlined :disabled="loading" @click="discard" />
                <Button type="submit" label="Save changes" :loading="loading" />
              </div>
            </div>
          </form>
        </TabPanel>
        <TabPanel value="invitation-email">
          <div class="pt-4">
            <InviteEmailTemplateEditor :key="applicationId" :application-id="applicationId" />
          </div>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { Info, Lock, Mail, SlidersHorizontal, UsersRound } from '@lucide/vue'
import { ref, computed, watch } from 'vue'
import { showErrorToast } from '@kinotic-ai/frontend-common'
import { Textarea, Button, ToggleSwitch } from 'primevue'
import Select from 'primevue/select'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import { DashboardSection, PageHeader, TINTS } from '@kinotic-ai/frontend-common'
import InviteEmailTemplateEditor from '@/components/InviteEmailTemplateEditor.vue'
import { useQueryTab } from '@/composables/useQueryTab'
import { APPLICATION_STATE } from '@/states/IApplicationState'
import { USER_STATE } from '@/states/IUserState'
import { Kinotic } from '@kinotic-ai/core'
import { useToast } from 'primevue/usetoast'

const props = defineProps({
  applicationId: {
    type: String,
    required: true
  }
})

const toast = useToast()
const activeTab = useQueryTab(['general', 'invitation-email'] as const)
const appDescription = ref('')
const tenantPerUser = ref(false)
const primaryUiId = ref<string | null>(null)
const publishedUiNames = ref<string[]>([])
const loading = ref(false)

// One setting per row: what it is and what it does on the left, its control on the right
const ROW_CLASS = 'grid items-start gap-3 px-5 py-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)] md:gap-8'
const LABEL_CLASS = 'block text-sm font-medium text-surface-950 dark:text-surface-0'
const HELP_CLASS = 'mt-1 text-xs leading-5 text-muted-color'

/** The values last loaded or saved, which the form compares against to know it has changes. */
const saved = ref({ description: '', tenantPerUser: false, primaryUiId: null as string | null })
const dirty = computed(() => appDescription.value !== saved.value.description
    || tenantPerUser.value !== saved.value.tenantPerUser
    || primaryUiId.value !== saved.value.primaryUiId)

// a primary UI whose deployment was removed stays designated, so it stays selectable
const uiOptions = computed(() => primaryUiId.value && !publishedUiNames.value.includes(primaryUiId.value)
  ? [...publishedUiNames.value, primaryUiId.value]
  : publishedUiNames.value)

watch(() => APPLICATION_STATE.currentApplication, (newApp) => {
  if (newApp) {
    appDescription.value = newApp.description || ''
    tenantPerUser.value = Boolean(newApp.tenantPerUser)
    primaryUiId.value = newApp.primaryUiId ?? null
    saved.value = { description: appDescription.value, tenantPerUser: tenantPerUser.value, primaryUiId: primaryUiId.value }
  }
}, { immediate: true })

watch(() => props.applicationId, loadPublishedUiNames, { immediate: true })

async function loadPublishedUiNames(applicationId: string): Promise<void> {
  try {
    const published = await Kinotic.uiDeployments.findAllForApplication(applicationId)
    publishedUiNames.value = published.map(ui => ui.name)
  } catch (error) {
    showErrorToast(toast, 'Failed to load the application\'s UIs', error)
  }
}

const copied = ref(false)

async function copyId(): Promise<void> {
  await navigator.clipboard.writeText(props.applicationId)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

function discard(): void {
  appDescription.value = saved.value.description
  tenantPerUser.value = saved.value.tenantPerUser
  primaryUiId.value = saved.value.primaryUiId
}

function selectTab(value: string | number): void {
  activeTab.value = value === 'invitation-email' ? 'invitation-email' : 'general'
}

const saveSettings = async () => {
  if (!APPLICATION_STATE.currentApplication) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'No application selected',
      life: 3000
    })
    return
  }

  loading.value = true
  try {
    const updatedApplication = {
      ...APPLICATION_STATE.currentApplication,
      organizationId: USER_STATE.getOrganizationId(),
      description: appDescription.value,
      tenantPerUser: tenantPerUser.value,
      primaryUiId: primaryUiId.value
    }

    await Kinotic.applications.save(updatedApplication)
    
    APPLICATION_STATE.currentApplication = updatedApplication

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Application settings saved successfully',
      life: 3000
    })
  } catch (error) {
    showErrorToast(toast, 'Failed to save application settings', error)
  } finally {
    loading.value = false
  }
}
</script>
