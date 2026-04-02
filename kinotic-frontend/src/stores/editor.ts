import { shallowRef, triggerRef } from 'vue'
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
} from '@kinotic-ai/idl'
import type {Edge, Node} from "@vue-flow/core"
import {generateVueFlowGraphFromSchema} from '@/util/graph.ts'
import {EntityDefinition} from "@kinotic-ai/os-api";

export interface IStructureStore {
    structure: EntityDefinition | null
    nodes: Node[];
    edges: Edge[]

    initNewStructure(applicationId: string, projectId: string): void
    addProperty(parentId: string, propertyName: string, typeCode: string, decorators?: C3Decorator[]): void
    renameProperty(parentId: string, oldName: string, newName: string): void
    updatePropertyType(parentId: string, propertyName: string, typeCode: string): void
    updateStructureName(newName: string): void
    updateStructureDescription(description: string): void
    updateEntityType(entityType: string): void
    updateMultiTenancyType(multiTenancyType: string): void
    findObjectById(obj: ObjectC3Type, id: string): ObjectC3Type | null
}

interface EntityDecorator extends C3Decorator {
    entityType?: string
    multiTenancyType?: string
}

class StructureStore implements IStructureStore {
    private readonly structureRef = shallowRef<EntityDefinition | null>(null)
    private readonly nodesRef = shallowRef<Node[]>([])
    private readonly edgesRef = shallowRef<Edge[]>([])

    get structure(): EntityDefinition | null {
        return this.structureRef.value
    }

    set structure(value: EntityDefinition | null) {
        this.structureRef.value = value
    }

    get nodes(): Node[] {
        return this.nodesRef.value
    }

    set nodes(value: Node[]) {
        this.nodesRef.value = value
    }

    get edges(): Edge[] {
        return this.edgesRef.value
    }

    set edges(value: Edge[]) {
        this.edgesRef.value = value
    }

    initNewStructure(applicationId: string, projectId: string) {
        const rootType = new ObjectC3Type('NewStructure123', 'default.namespace')
        rootType.addDecorator({
            type: 'Entity',
            multiTenancyType: 'NONE',
            entityType: 'TABLE'
        } as C3Decorator)
        this.structure = new EntityDefinition(applicationId, projectId, rootType.name, rootType)

        this.generateGraph()
    }

    addProperty(parentId: string, propertyName: string, typeCode: string, decorators?: C3Decorator[]) {
        if (!this.structure) return

        const parent = this.findObjectById(this.structure.schema, parentId)
        if (!parent) return

        const newType = this.buildType(typeCode, propertyName)
        const propDef = new PropertyDefinition(propertyName, newType, decorators || [])
        parent.addPropertyDefinition(propDef)

        this.generateGraph()
    }

    renameProperty(parentId: string, oldName: string, newName: string) {
        if (!this.structure) return
        const parent = this.findObjectById(this.structure.schema, parentId)
        if (!parent) return

        const prop = parent.properties.find(p => p.name === oldName)
        if (prop) {
            if (prop.type instanceof ObjectC3Type ||
                prop.type instanceof EnumC3Type ||
                prop.type instanceof UnionC3Type) {
                prop.type.name = newName
            }
            prop.name = newName
        }
        triggerRef(this.structureRef)
        this.generateGraph()
    }

    updatePropertyType(parentId: string, propertyName: string, typeCode: string) {
        if (!this.structure) return
        const parent = this.findObjectById(this.structure.schema, parentId)
        if (!parent) return

        const prop = parent.properties.find(p => p.name === propertyName)
        if (prop) {
            prop.type = this.buildType(typeCode, propertyName)
        }
        triggerRef(this.structureRef)
        this.generateGraph()
    }

    private buildType(typeCode: string, name: string): C3Type {
        const namespace = this.structure?.schema.namespace || 'default'

        switch (typeCode) {
            case 'string': return new StringC3Type()
            case 'boolean': return new BooleanC3Type()
            case 'integer': return new IntC3Type()
            case 'long': return new LongC3Type()
            case 'float': return new FloatC3Type()
            case 'double': return new DoubleC3Type()
            case 'date': return new DateC3Type()
            case 'char': return new CharC3Type()
            case 'byte': return new ByteC3Type()
            case 'short': return new ShortC3Type()
            case 'map': return new MapC3Type()
        }

        if (typeCode === 'object') {
            return new ObjectC3Type(name, namespace)
        }

        if (typeCode === 'enum') {
            return new EnumC3Type(name, namespace)
        }

        if (typeCode === 'union') {
            return new UnionC3Type(name, namespace)
        }

        if (typeCode.startsWith('array<')) {
            const innerTypeCode = typeCode.slice(6, -1)
            return new ArrayC3Type(this.buildType(innerTypeCode, name))
        }

        throw new Error(`Unknown type code: ${typeCode}`)
    }

    updateStructureName(newName: string) {
        if (this.structure) {
            const finalName = newName.replace(/\s+/g, "")
            this.structure.name = finalName
            this.structure.schema.name = finalName
            triggerRef(this.structureRef)
            this.generateGraph()
        }
    }

    updateStructureDescription(description: string) {
        if (!this.structure) return
        this.structure.description = description
        triggerRef(this.structureRef)
    }

    updateEntityType(entityType: string) {
        const entityDecorator = this.getEntityDecorator()
        if (!entityDecorator) return
        entityDecorator.entityType = entityType
        triggerRef(this.structureRef)
    }

    updateMultiTenancyType(multiTenancyType: string) {
        const entityDecorator = this.getEntityDecorator()
        if (!entityDecorator) return
        entityDecorator.multiTenancyType = multiTenancyType
        triggerRef(this.structureRef)
    }

    private generateGraph() {
        if (!this.structure) return
        const {nodes, edges} = generateVueFlowGraphFromSchema(this.structure.schema)
        this.nodes = nodes
        this.edges = edges
    }

    private getEntityDecorator(): EntityDecorator | undefined {
        return this.structure?.schema.decorators?.find(
            (decorator): decorator is EntityDecorator => decorator.type === 'Entity'
        )
    }

    findObjectById(obj: ObjectC3Type, id: string): ObjectC3Type | null {
        if (obj.name === id) return obj

        for (const prop of obj.properties) {
            if (prop.type instanceof ObjectC3Type) {
                const found = this.findObjectById(prop.type, id)
                if (found) return found
            }

            if (prop.type instanceof ArrayC3Type && prop.type.contains instanceof ObjectC3Type) {
                const found = this.findObjectById(prop.type.contains, id)
                if (found) return found
            }
        }
        return null
    }
}

const STRUCTURE_STORE: IStructureStore = new StructureStore()

export const useStructureStore = (): IStructureStore => {
    return STRUCTURE_STORE
}
