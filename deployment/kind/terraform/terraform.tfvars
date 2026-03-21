# Default values for local KinD development.
# Override on the command line: terraform apply -var="kinotic_version=3.5.2"

cluster_name    = "kinotic-cluster"
kinotic_version = "4.2.0-SNAPSHOT"
worker_count    = 3
enable_keycloak = false
