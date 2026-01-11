import dagre from 'dagre';
import { type Node, type Edge, MarkerType } from "@vue-flow/core";
import { ArrayC3Type, ObjectC3Type, EnumC3Type, UnionC3Type, PropertyDefinition } from "@mindignited/continuum-idl";

export interface FieldData {
    label: string;
    type: string;
}

export function generateVueFlowGraphFromSchema(rootType: ObjectC3Type) {
    const nodes: Node[] = [];
    const edges: Edge[] = [];
    let nodeCounter = 0;
    const processedStructures = new Map<string, string>();

    const levelColors = [
        'bg-lime-200',
        'bg-lime-200',
        'bg-blue-200',
        'bg-purple-200',
        'bg-pink-200',
        'bg-orange-200',
    ];

    function getColorForDepth(depth: number): string {
        return levelColors[depth % levelColors.length];
    }

    function createNode(
        idBase: string,
        label: string,
        type: string,
        fields: FieldData[],
        depth: number
    ): string {
        const nodeId = `${idBase}_${nodeCounter++}`;
        nodes.push({
            id: nodeId,
            type: 'structure',
            data: { label, fields, type, color: getColorForDepth(depth) },
            position: { x: 0, y: 0 },
        });
        return nodeId;
    }

    function processProperties(
        properties: PropertyDefinition[],
        label: string,
        nodeType: string,
        isRoot = false,
        depth = 0
    ): string {
        const key = label + JSON.stringify(properties.map((p) => p.name));
        if (processedStructures.has(key)) {
            return processedStructures.get(key)!;
        }

        const fields: FieldData[] = [];
        const nodeId = createNode(label, label, isRoot ? "structure" : nodeType, fields, depth);
        processedStructures.set(key, nodeId);

        properties.forEach((prop: PropertyDefinition, idx: number) => {
            const propName = prop.name || `prop${idx}`;
            const typeInstance = prop.type;
            const type = typeInstance.type;
            let fieldType = type;
            let childId = '';

            if (type === 'object' && typeInstance instanceof ObjectC3Type) {
                childId = processProperties(typeInstance.properties, typeInstance.name || propName, 'object', false, depth + 1);
            } else if (type === 'array' && typeInstance instanceof ArrayC3Type) {
                const contains = typeInstance.contains;
                const containsType = contains.type;

                if (containsType === 'object' && contains instanceof ObjectC3Type) {
                    fieldType = `${containsType}[]`;
                    childId = processProperties(contains.properties, contains.name || propName, 'object', false, depth + 1);
                } else if (containsType === 'union' && contains instanceof UnionC3Type) {
                    fieldType = `union[]`;
                    contains.types.forEach((unionType: ObjectC3Type, uIdx: number) => {
                        if (unionType.properties) {
                            const unionName = unionType.name || `${propName}_Union_${uIdx}`;
                            const unionId = processProperties(unionType.properties, unionName, 'union', false, depth + 1);
                            edges.push(makeEdge(nodeId, unionId, idx, `union-${uIdx}`));
                        }
                    });
                } else if (containsType === 'enum' && contains instanceof EnumC3Type) {
                    fieldType = `${containsType}[]`;
                    const enumNodeId = createEnumNode(propName, contains.name, contains.values, depth + 1);
                    edges.push(makeEdge(nodeId, enumNodeId, idx));
                } else {
                    fieldType = `${containsType}[]`;
                }
            } else if (type === 'union' && typeInstance instanceof UnionC3Type) {
                fieldType = `union`;
                typeInstance.types.forEach((unionType: ObjectC3Type, uIdx: number) => {
                    if (unionType.properties) {
                        const unionName = unionType.name || `${propName}_Union_${uIdx}`;
                        const unionId = processProperties(unionType.properties, unionName, 'union', false, depth + 1);
                        edges.push(makeEdge(nodeId, unionId, idx, `union-${uIdx}`));
                    }
                });
            } else if (type === 'enum' && typeInstance instanceof EnumC3Type) {
                const enumName = typeInstance.name || 'enum';
                fieldType = 'enum';
                const enumNodeId = createEnumNode(propName, enumName, typeInstance.values, depth + 1);
                edges.push(makeEdge(nodeId, enumNodeId, idx));
            }

            fields.push({ label: propName, type: fieldType });

            if (childId) {
                edges.push(makeEdge(nodeId, childId, idx));
            }
        });

        return nodeId;
    }

    function createEnumNode(propName: string, enumName: string, values: string[], depth: number) {
        const enumFields: FieldData[] = values.map((v) => ({ label: v, type: 'enum-value' }));
        const enumKey = JSON.stringify(enumFields) + enumName;
        if (processedStructures.has(enumKey)) {
            return processedStructures.get(enumKey)!;
        }
        const enumNodeId = createNode(`${propName}_enum`, enumName, 'enum', enumFields, depth);
        processedStructures.set(enumKey, enumNodeId);
        return enumNodeId;
    }

    function makeEdge(sourceId: string, targetId: string, sourceIdx: number, suffix = ''): Edge {
        return {
            id: `e-${sourceId}-${targetId}${suffix ? '-' + suffix : ''}`,
            source: sourceId,
            sourceHandle: `out-${sourceIdx}`,
            target: targetId,
            targetHandle: 'in-0',
            animated: true,
            markerEnd: { type: MarkerType.Arrow }
        };
    }

    // Build graph
    processProperties(rootType.properties, rootType.name, 'object', true, 0);

    // Apply dagre layout
    const g = new dagre.graphlib.Graph();
    g.setGraph({ rankdir: 'LR', nodesep: 50, ranksep: 100 });
    g.setDefaultEdgeLabel(() => ({}));

    nodes.forEach(node => g.setNode(node.id, { width: 200, height: 100 }));
    edges.forEach(edge => g.setEdge(edge.source, edge.target));

    dagre.layout(g);

    nodes.forEach(node => {
        const { x, y } = g.node(node.id) as { x: number; y: number };
        node.position = { x, y };
    });

    return { nodes, edges };
}
