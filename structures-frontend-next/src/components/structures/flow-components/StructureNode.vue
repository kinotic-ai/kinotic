<script lang="ts">
import { Vue, Component, Prop, Ref } from "vue-facing-decorator"
import InputText from "primevue/inputtext"
import Button from "primevue/button"
import Popover from "primevue/popover"
import PropertyType from "@/components/structures/flow-components/PropertyType.vue"
import { useStructureStore } from "@/stores/editor.ts"
import {usePropertyTypes} from "@/composables/structure/usePropertyTypes.ts";
import {Handle, Position} from "@vue-flow/core";
import type {FieldData} from "@/util/graph.ts";

interface INodeData {
  label: string;
  fields: FieldData[];
  type: string;
  color: string
}

@Component({
  components: {Handle, InputText, Button, Popover, PropertyType } })
export default class StructureNode extends Vue {
  @Prop({ required: true }) data!: INodeData
  @Ref() popover!: InstanceType<typeof Popover>
  @Ref() addButton!: HTMLElement
  @Ref() typeEditPopover!: InstanceType<typeof Popover>;

  get Position() {
    return Position
  }

  structureStore = useStructureStore()

  editingNameIndex: number | null = null
  selectedPropertyIndex: number | null = null
  newPropertyName = ''
  newPropertyTypeClass = ''

  errors: { name?: string; type?: string } = {
    name: '',
    type: ''
  };

  get objectType() {
    return this.structureStore.findObjectById(
        this.structureStore.structure!.entityDefinition,
        this.data.label
    )
  }

  get properties() {
    return this.objectType?.properties ?? []
  }

  types = usePropertyTypes();

  get typeOptions() {
    return this.types.typeOptions
  }

  startEditingName(index: number) {
    this.editingNameIndex = index
  }

  finishEditingName(index: number) {
    if (!this.objectType || !this.properties[index]) {
      this.editingNameIndex = null;
      return;
    }

    // Reset errors before validating
    this.errors = {};

    const oldName = this.properties[index].name;
    let newName = this.data.fields[index].label.trim();

    // Remove all spaces from the name
    newName = newName.replace(/\s+/g, "");

    // 1. Empty name check
    if (!newName) {
      this.errors.name = "Name is required";
    }

    // 2. Duplicate check (exclude current property)
    if (!this.errors.name) {
      const exists = this.properties.some(
          (p, i) => i !== index && p.name === newName
      );
      if (exists) {
        this.errors.name = `Name already exists`;
      }
    }

    // If validation failed, revert the visual label to the old name
    if (this.errors.name) {
      this.data.fields[index].label = oldName;
      this.editingNameIndex = null;
      return;
    }

    // 3. Apply rename in store if name changed
    if (oldName !== newName) {
      this.structureStore.renameProperty(this.data.label, oldName, newName);
    }

    this.editingNameIndex = null;
  }


  selectProperty(index: number) {
    this.selectedPropertyIndex = index
  }

  togglePopover(event: MouseEvent) {
    this.popover?.toggle(event, this.addButton)
  }

  addProperty() {
    // reset errors before validating
    this.errors = {};

    // 1. Remove all spaces from the name
    this.newPropertyName = this.newPropertyName.replace(/\s+/g, "");

    // 2. Name empty check
    if (!this.newPropertyName) {
      this.errors.name = "Name is required";
    }

    // 3. Name uniqueness check (only if not empty)
    if (!this.errors.name) {
      const exists = this.properties.some(
          (p) => p.name === this.newPropertyName
      );
      if (exists) {
        this.errors.name = `Name already exists`;
      }
    }

    // 4. Type required check
    if (!this.newPropertyTypeClass) {
      this.errors.type = "Type is required";
    }

    // 5. Stop if any error
    if (this.errors.name || this.errors.type) {
      return;
    }

    // ✅ Passed all checks — add property
    this.structureStore.addProperty(
        this.data.label,
        this.newPropertyName,
        this.newPropertyTypeClass
    );

    // reset form
    this.newPropertyName = '';
    this.newPropertyTypeClass = '';
    this.popover?.hide();
  }

  editType(e: MouseEvent, index: number) {
    this.errors = {};

    const property = this.properties[index];
    if (!property) return;

    this.newPropertyTypeClass =
        property.type?.type ||
        property.type?.constructor?.name?.toLowerCase() ||
        '';

    this.selectedPropertyIndex = index;
    this.typeEditPopover?.toggle(e, undefined);
  }

  updateTypeForSelectedProperty() {
    if (this.selectedPropertyIndex === null) return;

    this.errors = {};

    if (!this.newPropertyTypeClass) {
      this.errors.type = "Type is required";
      return;
    }

    const property = this.properties[this.selectedPropertyIndex];
    if (!property) return;

    this.structureStore.updatePropertyType(
        this.data.label,
        property.name,
        this.newPropertyTypeClass
    );

    // Reset
    this.newPropertyTypeClass = '';
    this.selectedPropertyIndex = null;
    this.typeEditPopover?.hide();
  }

}
</script>

<template>
  <div class="rounded-lg shadow bg-white text-xs w-72">
    <!-- Header -->
    <div
        :class="[
        'flex','items-center','gap-2','rounded-t-lg','font-bold','px-3','py-2',
        data.color,
        { 'text-white !bg-black': data.type === 'structure' },
        ]"
    >
      <i v-if="data.type === 'structure'" class="pi pi-table" style="color: #C8FB39"/>
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
        v-if="['structure', 'object'].includes(data.type)"
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