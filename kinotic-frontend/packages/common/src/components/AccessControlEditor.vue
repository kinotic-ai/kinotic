<template>
  <section class="flex flex-col gap-5" aria-label="Access control">
    <div class="flex items-center gap-3">
      <Button label="Reload" severity="secondary" :loading="busy" @click="reload" />
      <Button v-if="policy" label="Save access" :loading="busy" @click="save" />
      <Button v-if="policy" label="Republish permissions" severity="secondary" :loading="busy" @click="republish" />
      <span v-if="policy" class="text-sm text-muted-color">Revision {{ policy.revision }}</span>
    </div>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <Message v-if="saved" severity="success" :closable="false">Access changes published.</Message>
    <Message v-if="pending" severity="warn" :closable="false">Publication is pending. Requests are blocked until an authorized organization administrator or operator repairs and republishes access.</Message>
    <div v-if="loaded && !policy" class="flex flex-col gap-3">
      <p>Choose the first administrator for this scope.</p>
      <Select v-model="firstAdministrator" :options="identities" option-label="label" option-value="id" placeholder="Administrator" />
      <Button label="Initialize access" :disabled="!firstAdministrator" :loading="busy" @click="initialize" />
    </div>
    <template v-if="policy">
      <div>
        <h2 class="mb-2 text-lg font-semibold">Administrators</h2>
        <p class="mb-2 text-sm text-muted-color">Administrators can manage access and use every permission available in this scope.</p>
        <MultiSelect v-model="policy.administrators" :options="identities" option-label="label" option-value="id" placeholder="Administrators" class="w-full" />
      </div>
      <div>
        <h2 class="mb-2 text-lg font-semibold">Roles</h2>
        <div v-for="role in policy.roles" :key="role.id" class="mb-3 flex flex-wrap items-start gap-2 rounded border border-surface-200 p-3 dark:border-surface-700">
          <InputText v-model="role.name" placeholder="Role name" aria-label="Role name" />
          <MultiSelect v-model="role.permissions" :options="permissionOptions" option-label="label" option-value="value" placeholder="Permissions" class="min-w-72 flex-1" filter />
          <Button label="Remove role" severity="danger" text @click="removeRole(role.id)" />
        </div>
        <Button label="Add role" severity="secondary" @click="addRole" />
      </div>
      <div>
        <h2 class="mb-2 text-lg font-semibold">Groups</h2>
        <div v-for="group in policy.groups" :key="group.id" class="mb-3 flex flex-wrap gap-2 rounded border border-surface-200 p-3 dark:border-surface-700">
          <InputText v-model="group.name" placeholder="Group name" aria-label="Group name" />
          <MultiSelect v-model="group.memberIds" :options="identities" option-label="label" option-value="id" placeholder="Members" class="min-w-72 flex-1" filter />
          <Button label="Remove group" severity="danger" text @click="removeGroup(group.id)" />
        </div>
        <Button label="Add group" severity="secondary" @click="addGroup" />
      </div>
      <div>
        <h2 class="mb-2 text-lg font-semibold">Assignments</h2>
        <p class="mb-2 text-sm text-muted-color">Grant a user or group a role on all resources or one resource ID. A deny overrides an allow.</p>
        <div v-for="assignment in policy.assignments" :key="assignment.id" class="mb-3 grid gap-2 rounded border border-surface-200 p-3 md:grid-cols-3 dark:border-surface-700">
          <Select v-model="assignment.roleId" :options="policy.roles" option-label="name" option-value="id" placeholder="Role" aria-label="Role" />
          <Select v-model="assignment.subjectKind" :options="subjectKinds" aria-label="Subject kind" @change="assignment.subjectId = ''" />
          <Select v-model="assignment.subjectId" :options="assignment.subjectKind === AuthorizationSubjectKind.IDENTITY ? identities : groupOptions" option-label="label" option-value="id" placeholder="User or group" aria-label="User or group" filter />
          <Select v-model="assignment.resourceType" :options="resourceTypes(assignment.roleId)" placeholder="Resource type" aria-label="Resource type" />
          <Select v-model="assignment.selector" :options="selectors" aria-label="Resource scope" @change="assignment.resourceId = null" />
          <InputText v-if="assignment.selector === AuthorizationSelector.EXACT" v-model="assignment.resourceId" placeholder="Resource ID" aria-label="Exact resource ID" />
          <Select v-model="assignment.effect" :options="effects" aria-label="Allow or deny" />
          <Button label="Remove assignment" severity="danger" text @click="policy.assignments = policy.assignments.filter(value => value.id !== assignment.id)" />
        </div>
        <Button label="Add assignment" severity="secondary" @click="addAssignment" />
      </div>
    </template>
    <Button v-if="nextCursor" label="Load more users" severity="secondary" :loading="busy" @click="loadMoreIdentities" />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Pageable } from '@kinotic-ai/core'
import { AuthorizationEffect, AuthorizationSelector, AuthorizationSubjectKind, type AuthorizationIdentityOption, type AuthorizationPermission, type AuthorizationPolicy, type AuthorizationPolicyView, type AuthorizationScope, type IAccessControlService } from '@kinotic-ai/management-api'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import MultiSelect from 'primevue/multiselect'
import Select from 'primevue/select'

const props = defineProps<{ scope: AuthorizationScope; service: IAccessControlService }>()
const busy = ref(false)
const error = ref('')
const saved = ref(false)
const loaded = ref(false)
const pending = ref(false)
const policy = ref<AuthorizationPolicy | null>(null)
const permissions = ref<AuthorizationPermission[]>([])
const identities = ref<AuthorizationIdentityOption[]>([])
const nextCursor = ref<string | null>(null)
const firstAdministrator = ref('')
let scopeRevision = 0
const subjectKinds = Object.values(AuthorizationSubjectKind)
const selectors = Object.values(AuthorizationSelector)
const effects = Object.values(AuthorizationEffect)
const groupOptions = computed(() => policy.value?.groups.map(group => ({ id: group.id, label: group.name })) ?? [])
const permissionOptions = computed(() => {
  const entries = new Map(permissions.value.map(permission => [permission.permission, { value: permission.permission, label: `${permission.label || permission.permission} (${permission.permission})` }]))
  entries.set('*', { value: '*', label: 'All available permissions (*)' })
  for (const permission of permissions.value) {
    const parts = permission.permission.split('.')
    for (let length = 1; length < parts.length; length++) {
      const value = `${parts.slice(0, length).join('.')}.*`
      entries.set(value, { value, label: `All permissions in ${value}` })
    }
  }
  for (const role of policy.value?.roles ?? []) for (const permission of role.permissions) if (!entries.has(permission)) entries.set(permission, { value: permission, label: permission })
  return [...entries.values()]
})
function apply(view: AuthorizationPolicyView, revision: number) {
  if (revision !== scopeRevision) return false
  policy.value = view.policy
  permissions.value = view.permissions
  pending.value = view.pending
  loaded.value = true
  return true
}
async function action(work: () => Promise<void>, revision = scopeRevision) {
  if (busy.value) return
  busy.value = true; error.value = ''; saved.value = false
  try { await work() } catch (failure) { if (revision === scopeRevision) error.value = failure instanceof Error ? failure.message : String(failure) }
  finally { if (revision === scopeRevision) busy.value = false }
}
async function identityPage(cursor: string | null, scope = { ...props.scope }, revision = scopeRevision) {
  const page = await props.service.findIdentities(scope, Pageable.createWithCursor(cursor, 200))
  if (revision !== scopeRevision) return
  identities.value = cursor ? [...identities.value, ...(page.content ?? [])] : page.content ?? []
  nextCursor.value = page.cursor ?? null
}
function reload() {
  const scope = { ...props.scope }; const revision = scopeRevision
  return action(async () => { if (apply(await props.service.load(scope), revision)) await identityPage(null, scope, revision) }, revision)
}
function loadMoreIdentities() { return action(() => identityPage(nextCursor.value)) }
function save() {
  const value = policy.value; const revision = scopeRevision
  return action(async () => { if (value && apply(await props.service.save(value, value.revision), revision)) saved.value = true }, revision)
}
function republish() {
  const scope = { ...props.scope }; const value = policy.value; const revision = scopeRevision
  return action(async () => { if (value && apply(await props.service.republish(scope, value.revision), revision)) saved.value = true }, revision)
}
function initialize() {
  const scope = { ...props.scope }; const administrator = firstAdministrator.value; const revision = scopeRevision
  return action(async () => { if (apply(await props.service.initialize(scope, administrator), revision)) saved.value = true }, revision)
}
function addRole() { policy.value?.roles.push({ id: crypto.randomUUID(), name: 'New role', permissions: [] }) }
function addGroup() { policy.value?.groups.push({ id: crypto.randomUUID(), name: 'New group', memberIds: [] }) }
function removeRole(id: string) { if (policy.value) { policy.value.roles = policy.value.roles.filter(value => value.id !== id); policy.value.assignments = policy.value.assignments.filter(value => value.roleId !== id) } }
function removeGroup(id: string) { if (policy.value) { policy.value.groups = policy.value.groups.filter(value => value.id !== id); policy.value.assignments = policy.value.assignments.filter(value => value.subjectKind !== AuthorizationSubjectKind.GROUP || value.subjectId !== id) } }
function addAssignment() { policy.value?.assignments.push({ id: crypto.randomUUID(), roleId: '', subjectKind: AuthorizationSubjectKind.IDENTITY, subjectId: '', resourceType: '', selector: AuthorizationSelector.ALL, resourceId: null, effect: AuthorizationEffect.ALLOW }) }
function resourceTypes(roleId: string) {
  const role = policy.value?.roles.find(value => value.id === roleId)
  return [...new Set(permissions.value.filter(permission => role?.permissions.some(pattern => pattern === '*' || pattern === permission.permission || pattern.endsWith('.*') && permission.permission.startsWith(pattern.slice(0, -1)))).map(permission => permission.resourceType))]
}
watch(() => JSON.stringify(props.scope), () => {
  scopeRevision++; busy.value = false; error.value = ''; saved.value = false; pending.value = false
  loaded.value = false; policy.value = null; identities.value = []; nextCursor.value = null; firstAdministrator.value = ''
  void reload()
}, { immediate: true })
</script>
