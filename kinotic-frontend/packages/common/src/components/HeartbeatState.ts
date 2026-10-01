import { TINTS } from '../util/tints'

/** What a HeartbeatIcon shows: a live beat, a flat line, or a last beat that dropped. */
export enum HeartbeatState {
  ALIVE = 'alive',
  IDLE = 'idle',
  FAILED = 'failed'
}

/** The icon tile tint that goes with each state: purple while alive, red once failed, grey otherwise. */
export const HEARTBEAT_TINTS: Record<HeartbeatState, string> = {
  [HeartbeatState.ALIVE]: TINTS.purple,
  [HeartbeatState.IDLE]: TINTS.surface,
  [HeartbeatState.FAILED]: TINTS.red
}
