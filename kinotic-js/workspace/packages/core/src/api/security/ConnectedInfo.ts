import {Participant} from '@/api/security/Participant'

/**
 * Contains information about the connection that was established
 */
export class ConnectedInfo {

    public sessionId!: string;

    public replyToId!: string;

    public participant!: Participant;

}
