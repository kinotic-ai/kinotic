import { type Router } from 'vue-router';
import { reactive } from 'vue';
import { createDebug } from './util/debug';

const debug = createDebug('continuum-ui');

export interface IContinuumUI {
    initialize(router: Router): void;
    navigate(path: string): Promise<any>;
}

class ContinuumUI implements IContinuumUI {

    private router!: Router;

    constructor() {}

    public initialize(router: Router): void {
        this.router = router;
    }

    public navigate(path: string): Promise<any> {
        debug('navigate called with path: %s', path);
        return this.router.push(path);
    }
}

export const CONTINUUM_UI: IContinuumUI = reactive(new ContinuumUI());