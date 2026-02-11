export enum OtelExporterType {
    OTLP = "OTLP",
    CONSOLE = "CONSOLE",
    NONE = "NONE"
}

export class OtelConfig {
    public exporterType: OtelExporterType
    public otelEndpoint: string

    constructor(exporterType: OtelExporterType, otelEndpoint: string) {
        this.exporterType = exporterType
        this.otelEndpoint = otelEndpoint
    }

    public static fromEnv(): OtelConfig {
        const exporterType = process.env.OTEL_EXPORTER_TYPE as OtelExporterType || OtelExporterType.CONSOLE
        const otelEndpoint = process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://127.0.0.1:4317'
        return new OtelConfig(exporterType, otelEndpoint)
    }

    public print(): void {
        console.log(`OTEL_EXPORTER_TYPE=${this.exporterType}`)
        console.log(`OTEL_EXPORTER_OTLP_ENDPOINT=${this.otelEndpoint}`)
    }
}
