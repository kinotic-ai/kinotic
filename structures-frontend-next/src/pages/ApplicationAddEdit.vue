<template>
  <CrudEntityAddEdit :crud-service-identifier="crudServiceIdentifier" title="Application" :identity="id"
    :identityRules="applicationRules" :entity.sync="application" update:entity="handleEntityUpdate">
    <template #basic-info="{ entity }">
      <div class="mb-4">
        <label for="description" class="block text-sm font-medium text-gray-700 mb-1">Description</label>
        <InputText id="description" v-model="entity.description"
          class="w-full p-2 border rounded-m"
          placeholder="Enter description" />
      </div>
    </template>
  </CrudEntityAddEdit>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-facing-decorator'
import InputText from 'primevue/inputtext'
import CrudEntityAddEdit from '@/components/CrudEntityAddEdit.vue'
import { Application } from '@kinotic/structures-api'
import { IndexNameHelper } from '@/util/IndexNameHelper'

type RuleValidator = (value: string) => string | boolean

@Component({
  components: {
    CrudEntityAddEdit,
    InputText,
  },
})
export default class ApplicationAddEdit extends Vue {
  @Prop({ type: String, required: false, default: null })
  public id!: string | null

  private crudServiceIdentifier = 'org.mindignited.structures.api.services.ApplicationService'

  private application: Application = new Application('', '')

  private applicationRules: RuleValidator[] = [
    (v: string) => !!v || 'Name is required',
    (v: string) => {
      const result: string = IndexNameHelper.checkNameAndNamespace(v, 'Name')
      return result.length === 0 ? true : result
    }
  ]

  handleEntityUpdate(updated: Application): void {
    this.application = updated
  }
}
</script>
