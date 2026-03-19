import {Person} from '@/entity/domain/Person.js'
import {faker} from '@faker-js/faker'
import { v4 as uuidv4 } from 'uuid'

export function generatePeople(number: number): Person[] {
    const persons: Person[] = []
    for(let i = 0; i < number; i++){
        let person = {
            id: uuidv4(),
            firstName: faker.person.firstName(),
            lastName: faker.person.lastName(),
            birthDate: faker.date.birthdate({ min: 18, max: 65, mode: 'age' }),
            age: faker.number.int({ min: 18, max: 65 }),
            address: {
                street: faker.location.streetAddress(false),
                city: faker.location.city(),
                state: faker.location.state(),
                zip: faker.location.zipCode()
            }
        }
        persons.push(person)
    }
    return persons
}

export function formatDuration(durationMs: number): string {
    // Convert milliseconds to hours, minutes, seconds, and remaining milliseconds
    const hours = Math.floor(durationMs / 3600000)
    const minutes = Math.floor((durationMs % 3600000) / 60000)
    const seconds = Math.floor((durationMs % 60000) / 1000)
    const milliseconds = Math.floor(durationMs % 1000)

    // Build human-readable time string
    const timeParts = []
    if (hours > 0) timeParts.push(`${hours}h`)
    if (minutes > 0) timeParts.push(`${minutes}m`)
    if (seconds > 0) timeParts.push(`${seconds}s`)
    if (milliseconds > 0 || timeParts.length === 0) timeParts.push(`${milliseconds}ms`)

    return timeParts.join(' ')
}

/**
 * Generates a deterministic identifier based on the input number.
 * **How It Works: **
 * - The function deterministically maps each input number to a unique combination of letters and digits.
 * - Each of the five letters is chosen based on different scales of division and modulus operations,
 *   ensuring that small changes in the input number cause changes in the letters.
 * - The last two digits (00-99) are determined based on the input modulo 10.
 *
 * **Input Space: **
 * - There are 5 positions for letters (XXXXX), each position can be any of 26 possible letters ('a'-'z'),
 *   so there are `26^5 = 11,881,376` unique letter combinations.
 * - The two digits add an additional factor of 100 combinations, resulting in
 *   `11,881,376 * 100 = 1,188,137,600` possible unique combinations.
 *
 * @param input A number between 0 and 1,000,000
 */
export function generateDeterministicId(input: number): string {
    if (input < 0 || input > 1000000) {
        throw new Error("Input must be between 0 and 1,000,000.")
    }

    const letters = 'abcdefghijklmnopqrstuvwxyz'
    const letterCount = letters.length
    const numberCount = 10 // 0-9

    // Precompute powers of letterCount to use for letter extraction
    const powersOfLetterCount = [1, letterCount, letterCount ** 2, letterCount ** 3, letterCount ** 4]

    // Generate the letters using a loop
    let identifier = ''
    for (let i = 4; i >= 0; i--) {
        const letterIndex = Math.floor(input / powersOfLetterCount[i]) % letterCount
        identifier += letters[letterIndex]
    }

    // Compute the two-digit suffix
    const secondDigit = input % numberCount
    const firstDigit = Math.floor(input / numberCount) % numberCount

    // Construct the final identifier with letters and digits
    return `${identifier}${firstDigit}${secondDigit}`
}

/**
 * Generates multiple deterministic identifiers starting from 0 and incrementing by 1, up to the given count.
 * @param count The number of identifiers to generate
 */
export function generateMultipleDeterministicIds(count: number): string[] {
    const ids: string[] = []
    for (let i = 0; i < count; i++) {
        ids.push(generateDeterministicId(i))
    }
    return ids
}

