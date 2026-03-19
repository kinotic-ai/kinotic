export class NavItem {
    public icon: string
    public label: string
    public path: string
    public parent: NavItem | null = null
    public children: NavItem[] = []
    public readonly navigateFunction: () => Promise<void>

    constructor(
        icon: string,
        label: string,
        path: string,
        navigateFunction: () => Promise<void>
    ) {
        this.icon = icon
        this.label = label
        this.path = path
        this.navigateFunction = navigateFunction
    }

    public navigate(): Promise<void> {
        return this.navigateFunction()
    }

    public addChild(child: NavItem): void {
        child.parent = this
        this.children.push(child)
    }
}