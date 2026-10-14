<template>
  <div class="flex flex-col gap-5">
    <PageHeader title="Access control" description="Manage roles, groups, and resource assignments." />
    <div v-if="applicationId" class="flex items-center gap-3">
      <label for="access-tenant">Tenant ID (leave empty for application access)</label>
      <InputText id="access-tenant" v-model="tenantInput" />
      <Button label="Open scope" @click="tenantId = tenantInput.trim() || null" />
    </div>
    <AccessControlEditor :key="JSON.stringify(scope)" :scope="scope" :service="Kinotic.accessControl" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import { AccessControlEditor, PageHeader } from '@kinotic-ai/frontend-common'
import { AuthorizationScopeKind, type AuthorizationScope } from '@kinotic-ai/management-api'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import { USER_STATE } from '@/states/IUserState'
const props = defineProps<{ applicationId?: string }>()
const tenantInput = ref('')
const tenantId = ref<string | null>(null)
const scope = computed<AuthorizationScope>(() => ({ organizationId: USER_STATE.getOrganizationId(), applicationId: props.applicationId ?? null,
  tenantId: props.applicationId ? tenantId.value : null,
  kind: !props.applicationId ? AuthorizationScopeKind.ORGANIZATION : tenantId.value ? AuthorizationScopeKind.APPLICATION_TENANT : AuthorizationScopeKind.APPLICATION }))
</script>
