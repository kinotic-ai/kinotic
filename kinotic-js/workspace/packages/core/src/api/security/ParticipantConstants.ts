/**
 * Some common constants used for the {@link Participant} and {@link Participant#getMetadata()}
 * Created by navid on 2/3/20
 */
export class ParticipantConstants {
    static readonly PARTICIPANT_TYPE_METADATA_KEY: string = 'type'
    static readonly PARTICIPANT_TYPE_USER: string = 'user'
    /** A client (CLI, MCP host) acting on behalf of a user that authorized it. */
    static readonly PARTICIPANT_TYPE_DELEGATE: string = 'delegate'
    /** A non-human caller with its own credential — a platform daemon or an external API client. */
    static readonly PARTICIPANT_TYPE_MACHINE: string = 'machine'
    /** The clientId TestSecurityService (clienttest profile) maps to the ANONYMOUS role in core package tests. */
    static readonly CLI_PARTICIPANT_ID: string = '-42-Kinotic-CLI-42-'
}
