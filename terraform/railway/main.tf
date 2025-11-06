terraform {
  required_version = ">= 1.0"

  required_providers {
    railway = {
      source  = "terraform-community-providers/railway"
      version = "~> 0.3.0"
    }
  }
}

provider "railway" {
  # Railway API token should be set via RAILWAY_API_TOKEN environment variable
  # or can be set here (not recommended for security reasons)
  # token = var.railway_token
}

# Create a Railway project
resource "railway_project" "mobilispect" {
  name        = var.project_name
  description = "Mobilispect - Transit inspection and management platform"
}

# Create a Railway environment (production)
resource "railway_environment" "production" {
  project_id = railway_project.mobilispect.id
  name       = "production"
}

# Backend service
resource "railway_service" "backend" {
  project_id = railway_project.mobilispect.id
  name       = "backend"

  source {
    repo = var.github_repo
    branch = var.github_branch

    # Build from backend directory
    root_directory = "/backend"
  }
}

# Backend environment variables
resource "railway_variable" "backend_mongodb_uri" {
  project_id     = railway_project.mobilispect.id
  environment_id = railway_environment.production.id
  service_id     = railway_service.backend.id

  name  = "SPRING_DATA_MONGODB_URI"
  value = var.mongodb_uri
}

resource "railway_variable" "backend_mongodb_database" {
  project_id     = railway_project.mobilispect.id
  environment_id = railway_environment.production.id
  service_id     = railway_service.backend.id

  name  = "SPRING_DATA_MONGODB_DATABASE"
  value = var.mongodb_database
}

resource "railway_variable" "backend_transit_api_key" {
  project_id     = railway_project.mobilispect.id
  environment_id = railway_environment.production.id
  service_id     = railway_service.backend.id

  name  = "TRANSIT_LAND_API_KEY"
  value = var.transit_land_api_key
}

resource "railway_variable" "backend_log_level" {
  project_id     = railway_project.mobilispect.id
  environment_id = railway_environment.production.id
  service_id     = railway_service.backend.id

  name  = "LOGGING_LEVEL_COM_MOBILISPECT_BACKEND"
  value = var.log_level
}

resource "railway_variable" "backend_port" {
  project_id     = railway_project.mobilispect.id
  environment_id = railway_environment.production.id
  service_id     = railway_service.backend.id

  name  = "PORT"
  value = "8080"
}

# Frontend service (Angular web app)
resource "railway_service" "frontend" {
  project_id = railway_project.mobilispect.id
  name       = "frontend"

  source {
    repo = var.github_repo
    branch = var.github_branch

    # Build from frontend/web directory
    root_directory = "/frontend/web"
  }
}

# Frontend environment variables for API endpoint
resource "railway_variable" "frontend_api_url" {
  project_id     = railway_project.mobilispect.id
  environment_id = railway_environment.production.id
  service_id     = railway_service.frontend.id

  name  = "API_URL"
  value = "https://${railway_service.backend.domain}"
}

# Custom domain for backend (optional)
resource "railway_custom_domain" "backend" {
  count = var.backend_custom_domain != "" ? 1 : 0

  project_id = railway_project.mobilispect.id
  service_id = railway_service.backend.id
  domain     = var.backend_custom_domain
}

# Custom domain for frontend (optional)
resource "railway_custom_domain" "frontend" {
  count = var.frontend_custom_domain != "" ? 1 : 0

  project_id = railway_project.mobilispect.id
  service_id = railway_service.frontend.id
  domain     = var.frontend_custom_domain
}
