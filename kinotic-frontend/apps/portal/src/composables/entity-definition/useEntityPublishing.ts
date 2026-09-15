import { ref } from 'vue'
import type { MenuItem } from 'primevue/menuitem'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { Kinotic } from '@kinotic-ai/core'
import type { EntityDefinition } from '@kinotic-ai/management-api'
import { showErrorToast } from '@kinotic-ai/frontend-common'

/**
 * Publishing and unpublishing entity definitions, shared by the entity lists and the entity
 * page. Publishing runs at once; unpublishing confirms first, since it deletes the entity's
 * data. {@code onChanged} runs after either succeeds so the caller can reload what it shows.
 * The caller's template must host a {@code ConfirmDialog} (a CrudTable brings its own).
 */
export function useEntityPublishing(onChanged: () => void) {
  const confirm = useConfirm()
  const toast = useToast()

  /** Id of the entity definition whose publish state is being changed, for loading indicators. */
  const busyId = ref<string | null>(null)

  async function publish(entity: EntityDefinition): Promise<void> {
    busyId.value = entity.id
    try {
      await Kinotic.entityDefinitions.publish(entity.id!)
      toast.add({ severity: 'success', summary: `Published ${entity.name}`, life: 4000 })
      onChanged()
    } catch (error) {
      showErrorToast(toast, `Failed to publish ${entity.name}`, error, { life: 8000 })
    } finally {
      busyId.value = null
    }
  }

  function unpublish(entity: EntityDefinition): void {
    confirm.require({
      header: `Unpublish ${entity.name}?`,
      message: 'All data saved under this entity will be permanently deleted. This cannot be undone.',
      icon: 'pi pi-exclamation-triangle',
      rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
      acceptProps: { label: 'Unpublish', severity: 'danger' },
      accept: async () => {
        busyId.value = entity.id
        try {
          await Kinotic.entityDefinitions.unPublish(entity.id!)
          toast.add({ severity: 'success', summary: `Unpublished ${entity.name}`, life: 4000 })
          onChanged()
        } catch (error) {
          showErrorToast(toast, `Failed to unpublish ${entity.name}`, error, { life: 8000 })
        } finally {
          busyId.value = null
        }
      }
    })
  }

  /** The row menu items for one entity definition. */
  function rowActions(entity: EntityDefinition): MenuItem[] {
    return [
      {
        label: entity.published ? 'Unpublish' : 'Publish',
        icon: entity.published ? 'pi pi-eye-slash' : 'pi pi-eye',
        command: () => { entity.published ? unpublish(entity) : void publish(entity) }
      }
    ]
  }

  return { busyId, publish, unpublish, rowActions }
}
