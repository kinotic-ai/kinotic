/*
 * Copyright 2008-2021 Mind Ignited and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import { Publish, Context } from "../src"

interface CustomServiceContext {
  realm: string;
  apiKey: string;
}

@Publish("com.example")
export class TestServiceWithContext {
    greetWithContext(name: string, @Context() context: CustomServiceContext): string {
        return `Hello, ${name}! Realm: ${context.realm}, API Key: ${context.apiKey}`
    }

    async fetchDataWithContext(id: number, @Context() context: CustomServiceContext): Promise<{ id: number; value: string; realm: string; apiKey: string }> {
        return Promise.resolve({ id, value: `Data for ${id}`, realm: context.realm, apiKey: context.apiKey })
    }
}
