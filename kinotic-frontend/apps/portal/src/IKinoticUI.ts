import { type Router } from 'vue-router';
import { reactive } from 'vue';
import { createDebug } from '@kinotic-ai/frontend-common';

const debug = createDebug('kinotic-ui');

export interface IKinoticUI {
    initialize(router: Router): void;
    navigate(path: string): Promise<any>;
}

class KinoticUI implements IKinoticUI {

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

export const KINOTIC_UI: IKinoticUI = reactive(new KinoticUI());