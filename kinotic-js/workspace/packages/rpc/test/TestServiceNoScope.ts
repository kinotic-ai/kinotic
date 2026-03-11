/*
 * Copyright 2008-present Mind Ignited and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import { Publish } from "../src"

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
}
