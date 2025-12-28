import { Continuum, ContinuumSingleton, IServiceProxy } from "@kinotic/continuum-client";
import { ClusterInfo } from "../domain/ClusterInfo";

export interface ClusterInfoService {

    getClusterInfo(): Promise<ClusterInfo>;

}

export class DefaultClusterInfoService implements ClusterInfoService {

    protected serviceProxy: IServiceProxy

    constructor(continuumSingleton?: ContinuumSingleton) {
        this.serviceProxy = (continuumSingleton ? continuumSingleton : Continuum).serviceProxy('org.kinotic.structures.api.services.cluster.ClusterInfoService')
    }

    getClusterInfo(): Promise<ClusterInfo> {
        return this.serviceProxy.invoke('getClusterInfo');
    }

}