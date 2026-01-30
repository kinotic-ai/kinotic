import {defineStore} from 'pinia'
import {Structure} from '@kinotic/structures-api'
import {
    ArrayC3Type,
    C3Decorator,
    PropertyDefinition,
    C3Type,
    ObjectC3Type,
    EnumC3Type,
    UnionC3Type,
    StringC3Type,
    BooleanC3Type,
    IntC3Type,
    LongC3Type,
    FloatC3Type,
    DoubleC3Type,
    DateC3Type,
    CharC3Type,
    ByteC3Type,
    ShortC3Type,
    MapC3Type
} from '@kinotic/continuum-idl'
import type {Edge, Node} from "@vue-flow/core"
import {generateVueFlowGraphFromSchema} from '@/util/graph.ts'

export const useStructureStore = defineStore('structure', {
    state: () => ({
        structure: null as Structure | null,
        nodes: [] as Node[],
        edges: [] as Edge[],
    }),

    actions: {
        /** Create a brand-new structure with a root ObjectC3Type */
        initNewStructure(applicationId: string, projectId: string) {
            const rootType = new ObjectC3Type('NewStructure123', 'default.namespace')
            rootType.addDecorator({
                type: 'Entity',
                multiTenancyType: 'NONE',
                entityType: 'TABLE'
            } as C3Decorator)
            this.structure = new Structure(applicationId, projectId, rootType.name, rootType)

            this.generateGraph()
        },

        /** Adds a new property to an ObjectC3Type node */
        addProperty(parentId: string, propertyName: string, typeCode: string, decorators?: C3Decorator[]) {
            if (!this.structure) return;

            const parent = this.findObjectById(this.structure.entityDefinition, parentId);
            if (!parent) return;

            const newType = this.buildType(typeCode, propertyName);
            const propDef = new PropertyDefinition(propertyName, newType, decorators || []);
            parent.addPropertyDefinition(propDef);

            this.generateGraph();
        },

        renameProperty(parentId: string, oldName: string, newName: string) {
            if (!this.structure) return;
            const parent = this.findObjectById(this.structure.entityDefinition, parentId);
            if (!parent) return;

            const prop = parent.properties.find(p => p.name === oldName);
            if (prop) {
                if (prop.type instanceof ObjectC3Type ||
                    prop.type instanceof EnumC3Type ||
                    prop.type instanceof UnionC3Type) {
                    prop.type.name = newName
                }
                prop.name = newName;
            }
            this.generateGraph();
        },

        updatePropertyType(parentId: string, propertyName: string, typeCode: string) {
            if (!this.structure) return;
            const parent = this.findObjectById(this.structure.entityDefinition, parentId);
            if (!parent) return;

            const prop = parent.properties.find(p => p.name === propertyName);
            if (prop) {
                prop.type = this.buildType(typeCode, propertyName);
            }
            this.generateGraph();
        },

        buildType(typeCode: string, name: string): C3Type {
            const namespace = this.structure?.entityDefinition.namespace || 'default';

            // Primitive types mapping
            switch (typeCode) {
                case 'string': return new StringC3Type();
                case 'boolean': return new BooleanC3Type();
                case 'integer': return new IntC3Type();
                case 'long': return new LongC3Type();
                case 'float': return new FloatC3Type();
                case 'double': return new DoubleC3Type();
                case 'date': return new DateC3Type();
                case 'char': return new CharC3Type();
                case 'byte': return new ByteC3Type();
                case 'short': return new ShortC3Type();
                case 'map': return new MapC3Type();
            }

            // Object
            if (typeCode === 'object') {
                return new ObjectC3Type(name, namespace);
            }

            // Enum
            if (typeCode === 'enum') {
                return new EnumC3Type(name, namespace);
            }

            // Union
            if (typeCode === 'union') {
                return new UnionC3Type(name, namespace);
            }

            // Array
            if (typeCode.startsWith('array<')) {
                const innerTypeCode = typeCode.slice(6, -1);
                return new ArrayC3Type(this.buildType(innerTypeCode, name));
            }

            throw new Error(`Unknown type code: ${typeCode}`);
        },


        /** Rename the structure itself */
        updateStructureName(newName: string) {
            if (this.structure) {
                const finalName = newName.replace(/\s+/g, "");
                this.structure.name = finalName;
                this.structure.entityDefinition.name = finalName;
                this.generateGraph()
            }
        },

        /** Internal: Regenerate Vue Flow nodes & edges */
        generateGraph() {
            if (!this.structure) return
            const {nodes, edges} = generateVueFlowGraphFromSchema(this.structure.entityDefinition)
            this.nodes = nodes
            this.edges = edges
        },

        /** Internal: Recursive search for ObjectC3Type by ID */
        findObjectById(obj: ObjectC3Type, id: string): ObjectC3Type | null {
            if (obj.name === id) return obj;

            for (const prop of obj.properties) {
                // Direct object
                if (prop.type instanceof ObjectC3Type) {
                    const found = this.findObjectById(prop.type, id);
                    if (found) return found;
                }

                // Array of objects
                if (prop.type instanceof ArrayC3Type && prop.type.contains instanceof ObjectC3Type) {
                    const found = this.findObjectById(prop.type.contains, id);
                    if (found) return found;
                }
            }
            return null;
        }
    }
})
