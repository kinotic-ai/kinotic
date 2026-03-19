// useLayout.ts
import dagre from 'dagre'
import type { Node, Edge } from '@vue-flow/core'
interface PositionedNode extends Node {
  dimensions?: {
    width: number
    height: number
  }
}

export function useLayout() {
function layout(nodes: PositionedNode[], edges: Edge[], direction: 'LR' | 'TB' = 'LR'): Node[] {
    const dagreGraph = new dagre.graphlib.Graph()
    dagreGraph.setDefaultEdgeLabel(() => ({}))
    dagreGraph.setGraph({ rankdir: direction })

    nodes.forEach((node) => {
      dagreGraph.setNode(node.id, {
        width: node.dimensions?.width || 200,
        height: node.dimensions?.height || 100,
      })
    })

    edges.forEach((edge) => {
      dagreGraph.setEdge(edge.source, edge.target)
    })

    dagre.layout(dagreGraph)

    return nodes.map((node) => {
      const { x, y } = dagreGraph.node(node.id)
      return {
        ...node,
        position: { x, y },
        // avoid Vue Flow shifting things after layout
        positionAbsolute: { x, y },
      }
    })
  }

  return { layout }
}
