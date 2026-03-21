output "cluster_name" {
  description = "KinD cluster name"
  value       = kind_cluster.kinotic.name
}

output "kubeconfig" {
  description = "Kubeconfig for the KinD cluster"
  value       = kind_cluster.kinotic.kubeconfig
  sensitive   = true
}

output "app_url" {
  description = "Kinotic application URL (via ingress)"
  value       = "https://localhost"
}

output "port_forward_commands" {
  description = "Commands for direct access to infrastructure services"
  value = merge(
    {
      elasticsearch = "kubectl port-forward svc/kinotic-es-es-http 9200:9200"
    },
    var.enable_keycloak ? {
      postgresql = "kubectl port-forward svc/keycloak-db-postgresql 5432:5432"
      keycloak   = "kubectl port-forward svc/keycloak 8888:8888"
    } : {}
  )
}
