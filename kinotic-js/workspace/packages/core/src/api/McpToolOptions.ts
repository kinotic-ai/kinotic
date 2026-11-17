/**
 * What a {@link McpTool} declaration states about the tool. Each value is a literal, read from the source by
 * `kinotic generate`.
 */
export interface McpToolOptions {
    /**
     * Half of the tool's display title. On the service class it is the service's half, on a method it is the
     * function's, and the two are joined by a space. An empty half is built from the name it stands for, so
     * `findByRepoFullName` on `ProjectService` is titled `Project Service Find By Repo Full Name`.
     */
    title?: string

    /**
     * What the tool does, in the words an LLM reads to decide whether to call it. Only read on a method. When
     * it is empty the method's JSDoc is used, and when the method has none, its name as a sentence.
     */
    description?: string

    /**
     * Indicates the tool does not modify its environment. All four hints are read together from the method's
     * declaration, exactly as it writes them; a method with no declaration of its own is hinted by the words
     * in its name instead.
     */
    readOnlyHint?: boolean

    /**
     * Indicates the tool may perform destructive updates (only meaningful when not read-only).
     */
    destructiveHint?: boolean

    /**
     * Indicates repeated calls with the same arguments have no additional effect.
     */
    idempotentHint?: boolean

    /**
     * Indicates the tool reaches a system outside the platform, such as a third-party API.
     */
    openWorldHint?: boolean
}
