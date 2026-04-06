output "cluster_name" {
  description = "KinD cluster name"
  value       = kind_cluster.kinotic.name
}

output "kubeconfig" {
  description = "Kubeconfig for the KinD cluster"
  value       = kind_cluster.kinotic.kubeconfig
  sensitive   = true
}

output "endpoints" {
  description = "Kinotic service endpoints"
  value = var.use_mkcert ? {
    ui      = "https://localhost/"
    api     = "https://localhost:8080/api/"
    graphql = "https://localhost:4000/graphql/"
    stomp   = "wss://localhost:58503/v1"
  } : {
    ui      = "http://localhost:9090/"
    api     = "http://localhost:8080/api/"
    graphql = "http://localhost:4000/graphql/"
    stomp   = "ws://localhost:58503/v1"
  }
}

output "keycloak" {
  description = "Keycloak endpoints (when enabled)"
  value = var.enable_keycloak ? {
    admin_console = var.use_mkcert ? "https://localhost:8888/auth/admin" : "http://localhost:8888/auth/admin"
    credentials   = "admin / admin"
  } : null
}

output "grafana" {
  description = "Grafana log dashboard"
  value = {
    url         = var.use_mkcert ? "https://localhost:3000" : "http://localhost:3000"
    credentials = "admin / admin"
  }
}

output "port_forward_commands" {
  description = "Commands for direct access to infrastructure services"
  value = merge(
    {
      elasticsearch = "kubectl port-forward svc/kinotic-es-es-http 9200:9200"
    },
    var.enable_keycloak ? {
      postgresql = "kubectl port-forward svc/keycloak-db-postgresql 5432:5432"
    } : {}
  )
}
