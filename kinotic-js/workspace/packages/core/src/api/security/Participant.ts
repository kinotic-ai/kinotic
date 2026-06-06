import type {IParticipant} from './IParticipant'

/**
 * A logged-in participant on the RPC layer. Alias of {@link IParticipant}; the Kinotic OS contract
 * ({@code @kinotic-ai/os-api}) narrows this to scope-typed participants.
 *
 * Created by Navid Mitchell on 6/2/20
 */
export type Participant = IParticipant
