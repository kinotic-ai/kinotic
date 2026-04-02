# Observability

> Monitoring, tracing, and logging across your Kinotic applications.

## Overview

Kinotic provides deep observability across your applications, aggregated at multiple levels: System, Organization, and Application.

## Metrics

Real-time monitoring of CPU, memory, and data throughput for all running services. Metrics are collected automatically for every deployed application and available through the Kinotic dashboard.

## Traces and Spans

Drill from high-level overviews into detailed execution logs. Distributed tracing follows requests across service boundaries, so you can pinpoint performance bottlenecks and errors in complex service interactions.

## LLM Observability

Trace user interactions with LLMs and track token utilization for cost analysis. LLM request and response data is indexed via Grafana Loki, giving you full-text search across all LLM interactions with filtering by user, application, model, and time range.

## Audit Logs

Track platform activity including:

- **Login history** — Who connected, when, and from where
- **Configuration changes** — OIDC provider updates, LLM configuration changes, and application settings modifications
- **Activity counts** — Aggregate usage metrics per user, application, and organization

## Application Logs

View microservice logs directly from the dashboard with the ability to temporarily adjust logging levels for debugging. Increase verbosity on a running service to investigate an issue, then restore normal levels when done — no redeployment required.
