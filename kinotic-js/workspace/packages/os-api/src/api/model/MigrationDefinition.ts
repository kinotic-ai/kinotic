/**
 * Represents a migration definition that can be sent via the API.
 * This is the API representation of a migration, containing the version, name, and file content.
 */
export interface MigrationDefinition {
    version: number;
    name: string;
    content: string;
}
