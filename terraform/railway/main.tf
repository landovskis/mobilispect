terraform {
  required_version = ">= 1.0"

  required_providers {
    railway = {
      source  = "terraform-community-providers/railway"
      version = "~> 0.3.0"
    }
  }
}

locals {
  project_name_tokens = regexall("[a-z0-9]+", lower(var.project_name))
  slug_base_raw       = length(local.project_name_tokens) > 0 ? join("-", local.project_name_tokens) : "mobilispect"
  slug_base           = length(local.slug_base_raw) > 32 ? substr(local.slug_base_raw, 0, 32) : local.slug_base_raw

  backend_subdomain  = var.backend_service_subdomain != "" ? var.backend_service_subdomain : "${local.slug_base}-backend"
  frontend_subdomain = var.frontend_service_subdomain != "" ? var.frontend_service_subdomain : "${local.slug_base}-frontend"
  domain_suffix      = var.railway_domain_suffix != "" ? var.railway_domain_suffix : "up.railway.app"
  backend_domain     = "${local.backend_subdomain}.${local.domain_suffix}"
  frontend_domain    = "${local.frontend_subdomain}.${local.domain_suffix}"
}

provider "railway" {
  # Railway API token should be set via RAILWAY_TOKEN environment variable
  # or can be set here (not recommended for security reasons)
  # token = var.railway_token
}

# Create a Railway project
resource "railway_project" "mobilispect" {
  name        = var.project_name
  description = "Mobilispect - Transit inspection and management platform"
}

# Backend service
resource "railway_service" "backend" {
  project_id     = railway_project.mobilispect.id
  name           = "backend"
  source_repo    = var.github_repo
  root_directory = "/backend"
}

# Backend environment variables
resource "railway_variable" "backend_mongodb_uri" {
  environment_id = railway_project.mobilispect.default_environment.id
  service_id     = railway_service.backend.id

  name  = "SPRING_DATA_MONGODB_URI"
  value = var.mongodb_uri
}

resource "railway_variable" "backend_mongodb_database" {
  environment_id = railway_project.mobilispect.default_environment.id
  service_id     = railway_service.backend.id

  name  = "SPRING_DATA_MONGODB_DATABASE"
  value = var.mongodb_database
}

resource "railway_variable" "backend_transit_api_key" {
  environment_id = railway_project.mobilispect.default_environment.id
  service_id     = railway_service.backend.id

  name  = "TRANSIT_LAND_API_KEY"
  value = var.transit_land_api_key
}

resource "railway_variable" "backend_log_level" {
  environment_id = railway_project.mobilispect.default_environment.id
  service_id     = railway_service.backend.id

  name  = "LOGGING_LEVEL_COM_MOBILISPECT_BACKEND"
  value = var.log_level
}

resource "railway_variable" "backend_port" {
  environment_id = railway_project.mobilispect.default_environment.id
  service_id     = railway_service.backend.id

  name  = "PORT"
  value = "8080"
}

# Frontend service (Angular web app)
resource "railway_service" "frontend" {
  project_id     = railway_project.mobilispect.id
  name           = "frontend"
  source_repo    = var.github_repo
  root_directory = "/frontend/web"
}

# Frontend environment variables for API endpoint
resource "railway_variable" "frontend_api_url" {
  environment_id = railway_project.mobilispect.default_environment.id
  service_id     = railway_service.frontend.id

  name  = "API_URL"
  value = var.backend_custom_domain != "" ? "https://${var.backend_custom_domain}" : "https://${local.backend_domain}"
}

# Custom domain for backend (optional)
resource "railway_custom_domain" "backend" {
  count = var.backend_custom_domain != "" ? 1 : 0

  environment_id = railway_project.mobilispect.default_environment.id
  service_id     = railway_service.backend.id
  domain         = var.backend_custom_domain
}

# Custom domain for frontend (optional)
resource "railway_custom_domain" "frontend" {
  count = var.frontend_custom_domain != "" ? 1 : 0

  environment_id = railway_project.mobilispect.default_environment.id
  service_id     = railway_service.frontend.id
  domain         = var.frontend_custom_domain
}
