/*
 * Copyright 2008-2021 Mind Ignited and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import { Publish, Scope } from "../src"

@Publish("com.example")
export class TestServiceWithScope {
    @Scope
    scope: string = "tenant"

    greet(name: string): string {
        return `Hello, ${name} from ${this.scope}!`
    }

    async fetchData(id: number): Promise<{ id: number; value: string; scope: string }> {
        return Promise.resolve({ id, value: `Data for ${id}`, scope: this.scope })
    }

    combine(a: string, b: number): string {
        return `${a} - ${b} in ${this.scope}`
    }

    async multiArgs(x: number, y: string, z: boolean): Promise<{ x: number; y: string; z: boolean; scope: string }> {
        return Promise.resolve({ x, y, z, scope: this.scope })
    }

    failSync(): string {
        throw new Error("Scoped sync failure")
    }

    async failAsync(): Promise<never> {
        return Promise.reject(new Error("Scoped async failure"))
    }

    processComplexObject(obj: { name: string, age: number, details: { active: boolean, score: number } }): string {
        return `${obj.name} is ${obj.age} years old, active: ${obj.details.active}, score: ${obj.details.score} in ${this.scope}`
    }

    processListOfComplexObjects(list: { id: number, tags: string[] }[]): number {
        return list.reduce((sum, item) => sum + item.id + item.tags.length, 0) + this.scope.length
    }
}
