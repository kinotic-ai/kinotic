import {Entity, MultiTenancyType, EntityType, Id, TimeReference, DateTime} from '@kinotic-ai/persistence'

@Entity(MultiTenancyType.SHARED, EntityType.STREAM)
export class Alert {
    @Id
    public alertId: string = ''           // Unique identifier for the alert
    public message: string = ''          // Main alert message
    public severity: 'LOW' | 'MEDIUM' | 'HIGH' = 'LOW'  // Alert severity level

    @TimeReference
    @DateTime
    public timestamp: string = new Date().toISOString()  // When the alert occurred

    public source: string = ''            // Source system or component
    public active: boolean = true         // Whether alert is still active
}