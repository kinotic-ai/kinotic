import {C3Decorator} from '@/api/decorators/C3Decorator'

/**
 * Marks a function as a Model Context Protocol tool, exposed to LLM callers with the title and description
 * they choose it by and the four hints as MCP tool behavior annotations.
 */
export class McpToolDecorator extends C3Decorator {

    /**
     * The human-readable display title.
     */
    public title: string

    /**
     * The LLM-facing description of what the tool does.
     */
    public description: string

    /**
     * Indicates the tool does not modify its environment.
     */
    public readOnlyHint: boolean = false

    /**
     * Indicates the tool may perform destructive updates (only meaningful when not read-only).
     */
    public destructiveHint: boolean = false

    /**
     * Indicates repeated calls with the same arguments have no additional effect.
     */
    public idempotentHint: boolean = false

    /**
     * Indicates the tool reaches a system outside the platform, such as a third-party API.
     */
    public openWorldHint: boolean = false

    constructor(title: string, description: string) {
        super()
        this.type = 'McpTool'
        this.title = title
        this.description = description
    }
}
