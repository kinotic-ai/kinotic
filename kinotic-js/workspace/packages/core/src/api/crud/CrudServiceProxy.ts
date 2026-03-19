import {FindAllIterablePage} from '@/internal/api/crud/FindAllIterablePage'
import {SearchIterablePage} from '@/internal/api/crud/SearchIterablePage'
import type {ICrudServiceProxy} from './ICrudServiceProxy'
import type {Identifiable, IServiceProxy, IterablePage} from '@/index'
import type {Page} from './Page'
import { Pageable } from './Pageable'

export class CrudServiceProxy<T extends Identifiable<string>> implements ICrudServiceProxy<T> {

    protected serviceProxy: IServiceProxy

    constructor(serviceProxy: IServiceProxy) {
        this.serviceProxy = serviceProxy
    }

    public count(): Promise<number> {
        return this.serviceProxy.invoke('count')
    }

    public create(entity: T): Promise<T> {
        return this.serviceProxy.invoke('create', [entity])
    }

    public deleteById(id: string): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [id])
    }

    public async findAll(pageable: Pageable): Promise<IterablePage<T>> {
        const page = await this.findAllSinglePage(pageable)
        return new FindAllIterablePage(pageable, page, this)
    }

    public findAllSinglePage(pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('findAll', [pageable])
    }

    public findById(id: string): Promise<T> {
        return this.serviceProxy.invoke('findById', [id])
    }

    public save(entity: T): Promise<T> {
        return this.serviceProxy.invoke('save', [entity])
    }

    public findByIdNotIn(ids: string[], page: Pageable): Promise<Page<Identifiable<string>>> {
        return (this.serviceProxy as IServiceProxy).invoke('findByIdNotIn', [ids, page])
    }

    public async search(searchText: string, pageable: Pageable): Promise<IterablePage<T>> {
        const page = await this.searchSinglePage(searchText, pageable)
        return new SearchIterablePage(pageable, page, searchText, this)
    }

    public searchSinglePage(searchText: string, pageable: Pageable): Promise<Page<T>> {
        return this.serviceProxy.invoke('search', [searchText, pageable])
    }
}
