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

    /**
     * Reads the configuration from {@code OTEL_EXPORTER_TYPE} and {@code OTEL_EXPORTER_OTLP_ENDPOINT}.
     * An unset exporter type means {@link OtelExporterType.CONSOLE}; a value that is not one of
     * {@link OtelExporterType} throws.
     */
    public static fromEnv(): OtelConfig {
        const raw = process.env.OTEL_EXPORTER_TYPE
        const types = Object.values(OtelExporterType)
        let exporterType: OtelExporterType
        if (!raw) {
            exporterType = OtelExporterType.CONSOLE
        } else if (types.includes(raw as OtelExporterType)) {
            exporterType = raw as OtelExporterType
        } else {
            throw new Error(`OTEL_EXPORTER_TYPE must be one of ${types.join(', ')}, got '${raw}'`)
        }
        const otelEndpoint = process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://127.0.0.1:4317'
        return new OtelConfig(exporterType, otelEndpoint)
    }

    public print(): void {
        console.log(`OTEL_EXPORTER_TYPE=${this.exporterType}`)
        console.log(`OTEL_EXPORTER_OTLP_ENDPOINT=${this.otelEndpoint}`)
    }
}
