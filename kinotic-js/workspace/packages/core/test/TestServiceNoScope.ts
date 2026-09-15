import { Publish } from "../src"
import { interval, Observable, of } from "rxjs"
import { map } from "rxjs/operators"

@Publish("com.example")
export class TestServiceNoScope {

    greet(name: string): string {
        return `Hello, ${name}!`
    }

    async fetchData(id: number): Promise<{ id: number; value: string }> {
        return Promise.resolve({ id, value: `Data for ${id}` })
    }

    combine(a: string, b: number): string {
        return `${a} - ${b}`
    }

    async multiArgs(x: number, y: string, z: boolean): Promise<{ x: number; y: string; z: boolean }> {
        return Promise.resolve({ x, y, z })
    }

    failSync(): string {
        throw new Error("Sync failure")
    }

    async failAsync(): Promise<never> {
        return Promise.reject(new Error("Async failure"))
    }

    processComplexObject(obj: { name: string, age: number, details: { active: boolean, score: number } }): string {
        return `${obj.name} is ${obj.age} years old, active: ${obj.details.active}, score: ${obj.details.score}`
    }

    processListOfComplexObjects(list: { id: number, tags: string[] }[]): number {
        return list.reduce((sum, item) => sum + item.id + item.tags.length, 0)
    }

    countTo(n: number): Observable<number> {
        return of(...Array.from({ length: n }, (_, i) => i + 1))
    }

    /** Emits once a period, for as long as something is subscribed. */
    tick(periodMs: number): Observable<number> {
        return interval(periodMs).pipe(map(i => i + 1))
    }
}
