export class Cat {
    type!: 'Cat'
    name!: string
    age!: number
}

export class Dog {
    type!: 'Dog'
    name!: string
    age!: number
}

export type Pet = Cat | Dog
