variable "project_name" {
  description = "Name of the Railway project"
  type        = string
  default     = "mobilispect"
}

variable "github_repo" {
  description = "GitHub repository in the format 'owner/repo'"
  type        = string
}

variable "github_branch" {
  description = "Git branch to deploy from"
  type        = string
  default     = "main"
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
