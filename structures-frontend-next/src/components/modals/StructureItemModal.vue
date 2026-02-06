<script lang="ts">
import { Vue, Prop, Emit, Component } from "vue-facing-decorator";
import dagre from "dagre";
import { VueFlow, type Node, type Edge, MarkerType } from "@vue-flow/core";
import { Background } from "@vue-flow/background";
import { Controls } from "@vue-flow/controls";
import { MiniMap } from "@vue-flow/minimap";
import { ArrowsRightLeftIcon, ArrowsUpDownIcon } from "@heroicons/vue/24/solid";
import GlobalObjectNode from "@/components/nodes/GlobalObjectNode.vue";
import ObjectNode from "@/components/nodes/ObjectNode.vue";
import EnumNode from "@/components/nodes/EnumNode.vue";
import UnionNode from "@/components/nodes/UnionNode.vue";

import "@vue-flow/core/dist/style.css";
import "@vue-flow/core/dist/theme-default.css";

@Component({
  components: {
    VueFlow,
    Background,
    Controls,
    MiniMap,
    ArrowsRightLeftIcon,
    ArrowsUpDownIcon,
  },
})
export default class StructureItemModal extends Vue {
  @Prop({ default: null }) item!: any;
  visible = true;

  flowNodes: Node[] = [];
  flowEdges: Edge[] = [];

  @Emit("close") closeModal() {
    return true;
  }
  onHide() {
    this.visible = false;
    this.closeModal();
  }

  get nodeTypes() {
    return {
      globalObject: GlobalObjectNode,
      objectNode: ObjectNode,
      enumNode: EnumNode,
      unionNode: UnionNode,
    };
  }

  mounted() {
    this.setupGraph();
  }

  setupGraph() {
    const entity = this.item?.entityDefinition;
    if (!entity || !Array.isArray(entity.properties)) return;

    this.flowEdges = [];
    let nodeCounter = 0;
    let yOffset = 20;
    const nodes: Node[] = [];

    const getRandomColor = (): string => {
      const colors = [
        "bg-orange-300",
        "bg-blue-300",
        "bg-green-300",
        "bg-purple-300",
        "bg-pink-300",
        "bg-yellow-300",
        "bg-teal-300",
      ];
      return colors[Math.floor(Math.random() * colors.length)];
    };

    const estimateUnionNodeHeight = (
      variants: { fields: string[] }[]
    ): number => {
      const baseHeight = 60;
      const variantPadding = 20;
      const perFieldHeight = 20;

      const totalHeight = variants.reduce((acc, variant) => {
        return acc + (variant.fields.length * perFieldHeight + variantPadding);
      }, baseHeight);

      return Math.max(150, totalHeight);
    };

    const createNode = (
      id: string,
      label: string,
      fields: { name: string; type: string }[],
      depth = 0,
      nodeType = "objectNode"
    ): string => {
      const nodeId = `${id}_${nodeCounter++}`;
      const estimatedHeight = Math.max(150, 50 + fields.length * 20);

      nodes.push({
        id: nodeId,
        type: depth === 0 ? "globalObject" : nodeType,
        position: { x: 100 + depth * 350, y: yOffset },
        data: { label, fields, color: getRandomColor() },
      });

      yOffset += estimatedHeight + 40;
      return nodeId;
    };

    const processedStructures = new Map<string, string>();

    const processProperties = (
      properties: any[],
      label: string,
      depth = 0
    ): string => {
      const structureKey =
        label + JSON.stringify(properties.map((p) => p.name));
      if (processedStructures.has(structureKey))
        return processedStructures.get(structureKey)!;

      const fields: { name: string; type: string }[] = [];
      const nodeId = createNode(
        label,
        label,
        fields,
        depth,
        depth === 0 ? "globalObject" : "objectNode"
      );
      processedStructures.set(structureKey, nodeId);

      properties.forEach((prop: any, idx: number) => {
        const propName = prop.name || `prop${idx}`;
        const type = prop.type?.type;
        let childId = "";
        const fieldIndex = fields.length;

        if (type === "object" && Array.isArray(prop.type?.properties)) {
          childId = processProperties(
            prop.type.properties,
            prop.type.name || propName,
            depth + 1
          );
          fields.push({ name: propName, type: prop.type?.name || "object" });
        } else if (type === "array") {
          const containsType = prop.type.contains?.type;
          const containsName = prop.type.contains?.name || propName;

          if (
            containsType === "object" &&
            Array.isArray(prop.type.contains?.properties)
          ) {
            childId = processProperties(
              prop.type.contains.properties,
              containsName,
              depth + 1
            );
            fields.push({ name: propName, type: `${containsName}[]` });
          } else if (
            containsType === "union" &&
            Array.isArray(prop.type.contains?.types)
          ) {
            fields.push({ name: propName, type: "union[]" });

            const unionVariants = prop.type.contains.types.map(
              (unionType: any, uIdx: number) => ({
                name: unionType.name || `${propName}_Variant_${uIdx + 1}`,
                fields: (unionType.properties || []).map(
                  (p: any) => `${p.name}: ${p.type?.type || "unknown"}`
                ),
              })
            );

            const unionNodeId = `${propName}_union_group_${nodeCounter++}`;
            nodes.push({
              id: unionNodeId,
              type: "unionNode",
              position: { x: 100 + (depth + 1) * 350, y: yOffset },
              data: {
                label: propName,
                variants: unionVariants,
                color: "bg-red-500",
              },
            });
            yOffset += estimateUnionNodeHeight(unionVariants) + 40;

            this.flowEdges.push({
              id: `e-${nodeId}-${unionNodeId}`,
              source: nodeId,
              sourceHandle: `out-${fieldIndex}`,
              target: unionNodeId,
              targetHandle: "in-0",
              animated: true,
              markerEnd: { type: MarkerType.Arrow },
            });
          } else if (
            containsType === "enum" &&
            Array.isArray(prop.type.contains?.values)
          ) {
            fields.push({ name: propName, type: `${containsName}[]` });
            const enumFields = prop.type.contains.values.map((val: string) => ({
              name: val,
              type: "",
            }));
            const enumKey = JSON.stringify(enumFields) + containsName;
            let enumNodeId: string;
            if (processedStructures.has(enumKey)) {
              enumNodeId = processedStructures.get(enumKey)!;
            } else {
              enumNodeId = createNode(
                `${propName}_enum_array`,
                containsName,
                enumFields,
                depth + 1,
                "enumNode"
              );
              processedStructures.set(enumKey, enumNodeId);
            }
            this.flowEdges.push({
              id: `e-${nodeId}-${enumNodeId}`,
              source: nodeId,
              sourceHandle: `out-${fieldIndex}`,
              target: enumNodeId,
              targetHandle: "in-0",
              type: "default",
              animated: true,
              markerEnd: { type: MarkerType.Arrow },
            });
          } else {
            fields.push({ name: propName, type: `${containsType}[]` });
          }
        } else if (type === "union" && Array.isArray(prop.type.types)) {
          fields.push({ name: propName, type: "union" });

          const unionVariants = prop.type.types.map(
            (unionType: any, uIdx: number) => ({
              name: unionType.name || `${propName}_Variant_${uIdx + 1}`,
              fields: (unionType.properties || []).map(
                (p: any) => `${p.name}: ${p.type?.type || "unknown"}`
              ),
            })
          );

          const unionNodeId = `${propName}_union_group_${nodeCounter++}`;
          nodes.push({
            id: unionNodeId,
            type: "unionNode",
            position: { x: 100 + (depth + 1) * 350, y: yOffset },
            data: {
              label: propName,
              variants: unionVariants,
              color: "bg-red-500",
            },
          });
          yOffset += estimateUnionNodeHeight(unionVariants) + 40;

          this.flowEdges.push({
            id: `e-${nodeId}-${unionNodeId}`,
            source: nodeId,
            sourceHandle: `out-${fieldIndex}`,
            target: unionNodeId,
            targetHandle: "in-0",
            animated: true,
            markerEnd: { type: MarkerType.Arrow },
          });
        } else if (type === "enum" && Array.isArray(prop.type?.values)) {
          const enumLabel = prop.type?.name || `${propName}_Enum`;
          fields.push({ name: propName, type: enumLabel });
          const enumFields = prop.type.values.map((val: string) => ({
            name: val,
            type: "",
          }));
          const enumKey = JSON.stringify(enumFields) + enumLabel;
          let enumNodeId: string;
          if (processedStructures.has(enumKey)) {
            enumNodeId = processedStructures.get(enumKey)!;
          } else {
            enumNodeId = createNode(
              `${propName}_enum`,
              enumLabel,
              enumFields,
              depth + 1,
              "enumNode"
            );
            processedStructures.set(enumKey, enumNodeId);
          }
          this.flowEdges.push({
            id: `e-${nodeId}-${enumNodeId}`,
            source: nodeId,
            sourceHandle: `out-${fieldIndex}`,
            target: enumNodeId,
            targetHandle: "in-0",
            type: "default",
            animated: true,
            markerEnd: { type: MarkerType.Arrow },
          });
        } else {
          fields.push({ name: propName, type: type || "unknown" });
        }

        if (childId) {
          this.flowEdges.push({
            id: `e-${nodeId}-${childId}`,
            source: nodeId,
            sourceHandle: `out-${fieldIndex}`,
            target: childId,
            targetHandle: "in-0",
            type: "default",
            animated: true,
            markerEnd: { type: MarkerType.Arrow },
          });
        }
      });

      return nodeId;
    };

    processProperties(entity.properties, entity.name || "Root");
    this.flowNodes = nodes;
    this.applyAutoLayout("LR");

    this.$nextTick(() => {
      (this.$refs.flow as any)?.fitView?.();
    });
  }
  applyAutoLayout(direction: "LR" | "TB" = "LR") {
  const g = new dagre.graphlib.Graph({ multigraph: true });

  g.setGraph({
    rankdir: direction,
    nodesep: direction === "LR" ? 200 : 600,
    ranksep: direction === "LR" ? 600 : 200,
    marginx: 20,
    marginy: 40,
  });

  g.setDefaultEdgeLabel(() => ({}));
  this.flowNodes.forEach((node) => {
    const numFields =
      node.data?.fields?.length || node.data?.variants?.length || 0;
    const height = Math.max(100, 40 + numFields * 30);
    const width = 220;
    g.setNode(node.id, { width, height });
  });
  this.flowEdges.forEach((edge) => {
    g.setEdge(edge.source, edge.target);
  });
  dagre.layout(g);
  const rowMap = new Map<number, number>();
  let rowIndexCounter = 0;

  this.flowNodes = this.flowNodes.map((node) => {
    const pos = g.node(node.id);
    if (!pos) return node;
    const primaryAxis = direction === "TB" ? pos.y : pos.x;
    const roundedAxis = Math.round(primaryAxis / 50) * 50;
    if (!rowMap.has(roundedAxis)) {
      rowMap.set(roundedAxis, rowIndexCounter++);
    }

    const rowIndex = rowMap.get(roundedAxis);

    return {
      ...node,
      position: { x: pos.x, y: pos.y },
      positionAbsolute: { x: pos.x, y: pos.y },
      data: {
        ...node.data,
        rowIndex
      },
    };
  });
}

}
</script>

<template>
  <div
    v-show="visible"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50"
  >
    <div class="relative w-full h-screen bg-white shadow-lg overflow-hidden">
      <div
        class="flex items-center justify-between p-4 border-b border-gray-200"
      >
        <h3 class="text-xl font-semibold text-gray-900">Structure Details</h3>
        <Controls position="top-center" class="flex gap-2">
          <button
            @click="applyAutoLayout('LR')"
            title="Horizontal Layout"
            class="p-2 bg-gray-400 rounded hover:bg-gray-500"
          >
            <ArrowsRightLeftIcon class="w-5 h-5 text-white" />
          </button>
          <button
            @click="applyAutoLayout('TB')"
            title="Vertical Layout"
            class="p-2 bg-gray-400 rounded hover:bg-gray-500"
          >
            <ArrowsUpDownIcon class="w-5 h-5 text-white" />
          </button>
        </Controls>
        <button
          @click="onHide"
          class="text-gray-400 hover:text-gray-900 hover:bg-gray-200 rounded-lg text-sm w-8 h-8 flex items-center justify-center"
        >
          <svg
            class="w-3 h-3"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 14 14"
          >
            <path
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="m1 1 6 6m0 0 6 6M7 7l6-6M7 7l-6 6"
            />
          </svg>
        </button>
      </div>
      <div class="h-full">
        <VueFlow
          ref="flow"
          :nodes="flowNodes"
          :edges="flowEdges"
          :node-types="nodeTypes"
          :minZoom="0.01"
        >
          <Background pattern-color="#ccc" :gap="20" />
          <MiniMap />
          <Controls position="top-left" />
        </VueFlow>
      </div>
    </div>
  </div>
</template>

<style scoped>
.p-row-even,
.p-row-odd {
  cursor: pointer !important;
}
</style>
