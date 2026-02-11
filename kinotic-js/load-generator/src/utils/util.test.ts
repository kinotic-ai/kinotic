import {generateDeterministicId} from '@/utils/DataUtil.js'
import {expect, test} from 'vitest'

test('Test Deterministic Id', () => {
    const collisionCheck = new Set<string>()
    for(let i=0; i<1000000; i++){
        const id = generateDeterministicId(i)
        expect(collisionCheck.has(id)).toBe(false)
        collisionCheck.add(id)
    }
})
