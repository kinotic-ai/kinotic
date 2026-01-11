# Building and Running the Docker Container

## Prerequisites

Ensure you have Docker installed on your machine. You can download it from [here](https://www.docker.com/products/docker-desktop).

## Building the Docker Image

To build the Docker image, navigate to the `structures-js/load-generator` directory and run the following command:

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

## Summary

1. **Build the Docker image**:
    ```sh
    docker build -t load-generator .
    ```

2. **Run the Docker container with environment variables**:
    ```sh
    docker run --env-file .env.docker load-generator
    ```