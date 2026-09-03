export class MmdsConfig {
	public networkInterfaces!: string[];
	public data!: Record<string, unknown>;
	public ipv4Address?: string;
	public imdsCompat?: boolean;
}
