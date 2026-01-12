import { IServiceProxy, IServiceRegistry } from '@kinotic-foundation/continuum-js'
import { container, inject, injectable } from 'inversify-props'
import { Observable } from 'rxjs'
import { KafkaConsumerGroupInfo } from '@/frontends/develop/models/KafkaConsumerGroupInfo'

/**
 * Kafka service proxy
 */
export interface IKafkaService {

    findAllKafkaConsumers(): Observable<KafkaConsumerGroupInfo>

}


@injectable()
export class KafkaService implements IKafkaService {

    protected serviceProxy: IServiceProxy

    constructor(@inject() serviceRegistry: IServiceRegistry) {
        this.serviceProxy = serviceRegistry.serviceProxy('org.mindignited.continuum.orbiter.api.KafkaService')
    }

    public findAllKafkaConsumers(): Observable<KafkaConsumerGroupInfo> {
        return this.serviceProxy.invokeStream('findAllKafkaConsumers') as Observable<KafkaConsumerGroupInfo>
    }

}

container.addSingleton<IKafkaService>(KafkaService)
