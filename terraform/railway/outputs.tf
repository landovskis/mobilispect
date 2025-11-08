output "project_id" {
  description = "Railway project ID"
  value       = railway_project.mobilispect.id
}

output "backend_service_id" {
  description = "Backend service ID"
  value       = railway_service.backend.id
}

output "backend_url" {
  description = "Backend service URL"
  value       = var.backend_custom_domain != "" ? "https://${var.backend_custom_domain}" : "https://${local.backend_domain}"
}

output "frontend_service_id" {
  description = "Frontend service ID"
  value       = railway_service.frontend.id
}

output "frontend_url" {
  description = "Frontend service URL"
  value       = var.frontend_custom_domain != "" ? "https://${var.frontend_custom_domain}" : "https://${local.frontend_domain}"
}

output "backend_custom_domain" {
  description = "Backend custom domain (if configured)"
  value       = var.backend_custom_domain != "" ? var.backend_custom_domain : "Not configured"
}

output "frontend_custom_domain" {
  description = "Frontend custom domain (if configured)"
  value       = var.frontend_custom_domain != "" ? var.frontend_custom_domain : "Not configured"
}
