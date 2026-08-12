# Building and Running the Docker Container

## Prerequisites

Ensure you have Docker installed on your machine. You can download it from [here](https://www.docker.com/products/docker-desktop).

## Building the Docker Image

To build the Docker image, navigate to the `kinotic-js/load-generator` directory and run the following command:

```sh
docker build -t load-generator .
```

## Running the Docker Container

To run the Docker container using the `.env.docker` file for environment variables, use the following command:

```sh
docker run --env-file .env.docker load-generator
```

This command will:

- Use the environment variables defined in the `.env.docker` file.

## Environment Variables

`src/` reads the following:

| Variable | Purpose |
|---|---|
| `KINOTIC_HOST`, `KINOTIC_PORT`, `KINOTIC_USE_SSL` | Server to connect to over STOMP |
| `TEST_NAME` | Which load test to run (e.g. `generateComplexEntities`) |
| `NUMBER_OF_TENANTS`, `BEGIN_TENANT_ID_NUMBER` | Tenant range to generate against |
| `MAX_CONCURRENT_REQUESTS`, `MAX_REQUESTS_PER_SECOND` | Concurrency and rate limit |
| `OTEL_EXPORTER_TYPE`, `OTEL_EXPORTER_OTLP_ENDPOINT` | Telemetry export (`NONE` to disable) |

The same variables are set by `deployment/docker-compose/compose.gen-schemas.yml` and by the
`load-generator` Helm chart in `deployment/helm/load-generator/`.

## Summary

1. **Build the Docker image**:
    ```sh
    docker build -t load-generator .
    ```

2. **Run the Docker container with environment variables**:
    ```sh
    docker run --env-file .env.docker load-generator
    ```
