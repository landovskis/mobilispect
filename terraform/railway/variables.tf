variable "project_name" {
  description = "Name of the Railway project"
  type        = string
  default     = "mobilispect"
}

variable "github_repo" {
  description = "GitHub repository in the format 'owner/repo'"
  type        = string
}

variable "mongodb_uri" {
  description = "MongoDB connection URI (e.g., mongodb+srv://...)"
  type        = string
  sensitive   = true
}

variable "mongodb_database" {
  description = "MongoDB database name"
  type        = string
}

variable "transit_land_api_key" {
  description = "Transit Land API key for fetching transit data"
  type        = string
  sensitive   = true
}

variable "log_level" {
  description = "Logging level for the backend application"
  type        = string
  default     = "INFO"
}

variable "backend_custom_domain" {
  description = "Custom domain for the backend API (optional)"
  type        = string
  default     = ""
}

variable "frontend_custom_domain" {
  description = "Custom domain for the frontend web app (optional)"
  type        = string
  default     = ""
}

variable "backend_service_subdomain" {
  description = "Preferred subdomain for the backend service (leave empty for an auto-generated slug)"
  type        = string
  default     = ""
}

variable "frontend_service_subdomain" {
  description = "Preferred subdomain for the frontend service (leave empty for an auto-generated slug)"
  type        = string
  default     = ""
}

variable "railway_domain_suffix" {
  description = "Suffix to append to generated subdomains (defaults to Railway's '*.up.railway.app')"
  type        = string
  default     = ""
}
