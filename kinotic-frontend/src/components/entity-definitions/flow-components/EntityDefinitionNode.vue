<script setup lang="ts">
import { computed, ref } from "vue"
import InputText from "primevue/inputtext"
import Button from "primevue/button"
import Popover from "primevue/popover"
import PropertyType from "@/components/entity-definitions/flow-components/PropertyType.vue"
import { useEntityDefinitionStore } from "@/stores/editor.ts"
import {usePropertyTypes} from "@/composables/entity-definition/usePropertyTypes.ts";
import {Handle, Position} from "@vue-flow/core";
import type { PropertyDefinition } from "@kinotic-ai/idl";
import type {FieldData} from "@/util/graph.ts";

interface INodeData {
  label: string;
  fields: FieldData[];
  type: string;
  color: string
}

const props = defineProps<{
  data: INodeData
}>()

const popover = ref<InstanceType<typeof Popover>>()
const addButton = ref<HTMLElement>()
const typeEditPopover = ref<InstanceType<typeof Popover>>()

const entityDefinitionStore = useEntityDefinitionStore()

const editingNameIndex = ref<number | null>(null)
const selectedPropertyIndex = ref<number | null>(null)
const newPropertyName = ref('')
const newPropertyTypeClass = ref('')

const errors = ref<{ name?: string; type?: string }>({
  name: '',
  type: ''
});

const objectType = computed(() => {
  return entityDefinitionStore.findObjectById(
      entityDefinitionStore.entityDefinition!.schema,
      props.data.label
  )
})

const properties = computed(() => {
  return objectType.value?.properties ?? []
})

const types = usePropertyTypes();

const typeOptions = computed(() => {
  return types.typeOptions
})

function startEditingName(index: number) {
  editingNameIndex.value = index
}

function finishEditingName(index: number) {
  if (!objectType.value || !properties.value[index]) {
    editingNameIndex.value = null;
    return;
  }

  // Reset errors before validating
  errors.value = {};

  const oldName = properties.value[index].name;
  let newName = props.data.fields[index].label.trim();

  // Remove all spaces from the name
  newName = newName.replace(/\s+/g, "");

  // 1. Empty name check
  if (!newName) {
    errors.value.name = "Name is required";
  }

  // 2. Duplicate check (exclude current property)
  if (!errors.value.name) {
    const exists = properties.value.some(
        (p: PropertyDefinition, i: number) => i !== index && p.name === newName
    );
    if (exists) {
      errors.value.name = `Name already exists`;
    }
  }

  // If validation failed, revert the visual label to the old name
  if (errors.value.name) {
    props.data.fields[index].label = oldName;
    editingNameIndex.value = null;
    return;
  }

  // 3. Apply rename in store if name changed
  if (oldName !== newName) {
    entityDefinitionStore.renameProperty(props.data.label, oldName, newName);
  }

  editingNameIndex.value = null;
}


function selectProperty(index: number) {
  selectedPropertyIndex.value = index
}

function togglePopover(event: MouseEvent) {
  popover.value?.toggle(event, addButton.value)
}

function addProperty() {
  // reset errors before validating
  errors.value = {};

  // 1. Remove all spaces from the name
  newPropertyName.value = newPropertyName.value.replace(/\s+/g, "");

  // 2. Name empty check
  if (!newPropertyName.value) {
    errors.value.name = "Name is required";
  }

  // 3. Name uniqueness check (only if not empty)
  if (!errors.value.name) {
    const exists = properties.value.some(
        (p: PropertyDefinition) => p.name === newPropertyName.value
    );
    if (exists) {
      errors.value.name = `Name already exists`;
    }
  }

  // 4. Type required check
  if (!newPropertyTypeClass.value) {
    errors.value.type = "Type is required";
  }

  // 5. Stop if any error
  if (errors.value.name || errors.value.type) {
    return;
  }

  // ✅ Passed all checks — add property
  entityDefinitionStore.addProperty(
      props.data.label,
      newPropertyName.value,
      newPropertyTypeClass.value
  );

  // reset form
  newPropertyName.value = '';
  newPropertyTypeClass.value = '';
  popover.value?.hide();
}

function editType(e: MouseEvent, index: number) {
  errors.value = {};

  const property = properties.value[index];
  if (!property) return;

  newPropertyTypeClass.value =
      property.type?.type ||
      property.type?.constructor?.name?.toLowerCase() ||
      '';

  selectedPropertyIndex.value = index;
  typeEditPopover.value?.toggle(e, undefined);
}

function updateTypeForSelectedProperty() {
  if (selectedPropertyIndex.value === null) return;

  errors.value = {};

  if (!newPropertyTypeClass.value) {
    errors.value.type = "Type is required";
    return;
  }

  const property = properties.value[selectedPropertyIndex.value];
  if (!property) return;

  entityDefinitionStore.updatePropertyType(
      props.data.label,
      property.name,
      newPropertyTypeClass.value
  );

  // Reset
  newPropertyTypeClass.value = '';
  selectedPropertyIndex.value = null;
  typeEditPopover.value?.hide();
}
</script>

<template>
  <div class="rounded-lg shadow bg-white text-xs w-72">
    <!-- Header -->
    <div
        :class="[
        'flex','items-center','gap-2','rounded-t-lg','font-bold','px-3','py-2',
        data.color,
        { 'text-white !bg-black': data.type === 'entityDefinition' },
        ]"
    >
      <i v-if="data.type === 'entityDefinition'" class="pi pi-table" style="color: var(--p-lime-300)"/>
      <span>{{ data.label }}</span>
    </div>

    <!-- Property list -->
    <div v-if="data.fields.length" class="flex flex-col">
      <div v-for="(property, index) in data.fields" :key="index"
           class="relative flex items-center justify-between px-3 py-2 border-b border-surface-100"
           :class="{'border !border-primary': selectedPropertyIndex === index}"
           @click="selectProperty(index)">

        <!-- Editable Property Name -->
        <div class="w-2/3 font-medium text-sm truncate">
          <template v-if="editingNameIndex === index">
            <InputText v-model="property.label"
                       autofocus
                       class="!w-full !h-5 !p-0 !m-0 !shadow-none !border-0 focus:!ring-0"
                       :class="{
                          '!border-0': !(errors.name && editingNameIndex === index),
                          '!border-red-500 !border': errors.name && editingNameIndex === index
              }"
                       @blur="finishEditingName(index)"
                       @keyup.enter="finishEditingName(index)" />
          </template>
          <template v-else>
            <span class="cursor-pointer" @dblclick="startEditingName(index)">
              {{ property.label }}
            </span>
          </template>
        </div>

        <!-- Property Type -->
        <PropertyType
            v-if="data.type !== 'enum'"
            :type="property.type"
            :color="data.color"
            @edit="(e: MouseEvent) => editType(e,index)"
        />

        <Handle
            type="source"
            :position="Position.Right"
            :id="`out-${index}`"
            class="absolute right-0 top-1/2 transform -translate-y-1/2"
        />
      </div>
    </div>

    <!-- Add Property Button -->
    <div
        v-if="['entityDefinition', 'object'].includes(data.type)"
        ref="addButton"
        class="flex justify-center items-center gap-2 font-bold hover:bg-primary-50 px-3 py-2 text-primary cursor-pointer"
        @click="togglePopover"
    >
      <i class="pi pi-plus"/>
      <span>Add property</span>
    </div>

    <Popover ref="popover">
      <div class="w-full flex flex-col gap-4">
        <div class="w-full flex gap-3">
          <!-- Name Input -->
          <div class="w-1/2 flex flex-col gap-1">
            <label for="name" class="text-sm">Property Name</label>
            <InputText
                id="name"
                v-model="newPropertyName"
                :class="{'p-invalid': !!errors.name}"
                aria-describedby="name"
            />
            <small v-if="errors.name" class="!text-text-xs">{{ errors.name }}</small>
          </div>

          <!-- Type Dropdown -->
          <div class="w-1/2 flex flex-col gap-1">
            <label for="type" class="text-sm">Type</label>
            <CascadeSelect
                id="type"
                v-model="newPropertyTypeClass"
                :options="typeOptions"
                optionGroupLabel="label"
                optionLabel="label"
                optionValue="code"
                :optionGroupChildren="['children']"
                placeholder="Select type"
                :class="{'p-invalid': !!errors.type}"
                class="w-full"
            />
            <small v-if="errors.type" class="!text-text-xs">{{ errors.type }}</small>
          </div>
        </div>

        <!-- Add Property Button -->
        <Button class="self-start" label="Add property" @click="addProperty"/>
      </div>
    </Popover>

    <Popover ref="typeEditPopover" class="min-w-60">
      <div class="w-full flex flex-col gap-4">
        <!-- Type Selection -->
        <div class="w-full flex flex-col gap-1">
          <label for="edit-type" class="text-sm">Type</label>
          <CascadeSelect
              id="edit-type"
              v-model="newPropertyTypeClass"
              :options="typeOptions"
              optionGroupLabel="label"
              optionLabel="label"
              optionValue="code"
              :optionGroupChildren="['children']"
              placeholder="Select type"
              :class="{ 'p-invalid': !!errors.type }"
              class="w-full"
          />
          <small v-if="errors.type" class="!text-text-xs">{{ errors.type }}</small>
        </div>

        <!-- Update Button -->
        <Button
            class="self-start"
            label="Update type"
            @click="updateTypeForSelectedProperty"
        />
      </div>
    </Popover>
  </div>
</template>

<style scoped>
.vue-flow__handle {
  background: none;
  border: none;
}
</style>