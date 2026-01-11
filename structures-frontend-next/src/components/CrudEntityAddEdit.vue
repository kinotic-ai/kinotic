<template>
  <div class="flex justify-between h-screen max-w-[1440px] mx-auto">
    <div class="pt-6 pl-8 w-1/2 flex flex-col">
      <div class="flex justify-content-start items-center">
        <Button
          icon="pi pi-times"
          class="p-button-text p-button-plain mr-3"
          @click="close"
          aria-label="Close"
        />
        <span class="text-black text-xl">Create Application</span>
      </div>
      <div class="mt-[260px] w-4/6 my-auto">
        <h2 class="text-3xl">
          Let's begin by choosing a name for your application.
        </h2>
        <div class="my-[60px]">
          <InputText
            v-model="syncedEntity.id"
            :disabled="!identityEditable"
            :placeholder="identityLabel"
            class="w-full max-w-[540px] h-[56px]"
            :class="{ 'p-invalid': !valid && rulesForIdentity.length > 0 }"
            @input="validateIdentity()"
          />
          <small
            v-if="!valid && rulesForIdentity.length > 0"
            class="p-error block mt-1"
          >
            {{ identityLabel }} is required
          </small>
        </div>

        <div class="mt-4">
          <Button
            label="Create"
            class="rounded-[10px] max-h-[56px] !py-[18px] !w-4/6 !text-base"
            @click="save"
          />
        </div>
      </div>
    </div>

    <div class="w-1/2 h-full relative">
      <img
        src="@/assets/create-application-image.svg"
        alt="Create Application Image"
        class="absolute left-0 top-0 max-h-[100vh]"
      />
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue, Emit } from 'vue-facing-decorator'
import { Continuum, type ICrudServiceProxy, type Identifiable } from '@mindignited/continuum-client'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'

type RuleValidator = (value: string) => string | boolean

interface ApplicationEntity extends Identifiable<string> {
  id: string
  name?: string
  description?: string
}

@Component({
  components: {
    Dialog,
    InputText,
    Button,
    ProgressSpinner,
  }
})
export default class CrudEntityAddEdit extends Vue {
  @Prop({ type: String, required: true }) public crudServiceIdentifier!: string
  @Prop({ type: String, required: true }) public title!: string
  @Prop({ type: String, default: null }) public identity!: string | null
  @Prop({ type: String, default: 'Enter your name here' }) public identityLabel!: string
  @Prop({ type: Array, default: () => [] }) public identityRules!: RuleValidator[]
  @Prop({ type: Boolean, default: true }) public identityEditable!: boolean
  @Prop({ type: Boolean, default: true }) public showBasicInfoSubheader!: boolean
  @Prop({ type: Object, default: () => ({ id: '' }) }) public entity!: ApplicationEntity

  public syncedEntity: ApplicationEntity = { id: '' }
  private crudServiceProxy!: ICrudServiceProxy<ApplicationEntity>
  private editing = false
  public valid = true
  public loading = false
  private rulesForIdentity: RuleValidator[] = []

  public async mounted() {
    this.rulesForIdentity =
      this.identityRules.length > 0
        ? this.identityRules
        : [(v: string) => !!v || `${this.identityLabel} is required`]

    try {
      this.crudServiceProxy = Continuum.crudServiceProxy(this.crudServiceIdentifier)

      if (this.identity !== null) {
        this.editing = true
        this.loading = true

        const item = await this.crudServiceProxy.findById(this.identity)
        this.syncedEntity = item
        this.afterLoad(item)
        this.loading = false
      }
    } catch (error: unknown) {
      this.loading = false
      this.displayAlert((error as Error).message || 'Error connecting or loading data')
    }
  }

  public close() {
    this.$router.back()
  }

  public validateIdentity() {
    this.valid = this.rulesForIdentity.every(rule => rule(this.syncedEntity.id) === true)
  }

  public async save() {
    this.validateIdentity()
    if (!this.valid) return

    this.loading = true
    this.beforeSave()

    try {
      if (!this.editing) {
        await this.crudServiceProxy.create(this.syncedEntity)
      } else {
        await this.crudServiceProxy.save(this.syncedEntity)
      }

      this.$router.push({ path: '/application', query: { created: 'true' } })
    } catch (error: unknown) {
      this.displayAlert((error as Error).message)
    }

    this.loading = false
  }

  @Emit() public beforeSave(): ApplicationEntity {
    return this.syncedEntity
  }

  @Emit() public afterLoad(item: ApplicationEntity): ApplicationEntity {
    return item
  }

  private displayAlert(text: string) {
    this.$toast.add({
      severity: 'error',
      summary: 'Error',
      detail: text,
      life: 3000
    })
  }
}
</script>