# Railway Deployment with Terraform

This directory contains Terraform configuration for deploying Mobilispect to Railway.

## Prerequisites

1. **Railway Account**: Sign up at [railway.app](https://railway.app)
2. **Terraform**: Install from [terraform.io](https://www.terraform.io/downloads)
3. **Railway CLI** (optional): Install with `npm i -g @railway/cli`
4. **MongoDB Atlas**: Set up a MongoDB cluster (or use existing)
5. **Transit Land API Key**: Get from [transit.land](https://www.transit.land/)

## Setup Steps

### 1. Get Railway API Token

```bash
# Option 1: Using Railway CLI
railway login
railway whoami --token

# Option 2: From Railway Dashboard
# Go to: https://railway.app/account/tokens
# Click "Create New Token"
```

Set the token as an environment variable:

```bash
export RAILWAY_TOKEN="your-token-here"
```

### 2. Configure Terraform Variables

Copy the example file and fill in your values:

```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your configuration:

```hcl
# Required variables
github_repo = "yourusername/mobilispect"

# MongoDB connection
mongodb_uri = "mongodb+srv://u:p@host/db" # pragma: allowlist secret
mongodb_database = "mobilispect"

# Transit Land API key
transit_land_api_key = "your-api-key-here"

# Optional: Custom domains
backend_custom_domain = "api.yourdomain.com"    # Optional
frontend_custom_domain = "yourdomain.com"       # Optional

# Optional: Override generated Railway subdomains
backend_service_subdomain  = ""
frontend_service_subdomain = ""

# Optional: Override the default Railway domain suffix (default: "up.railway.app")
railway_domain_suffix = ""
```

### 3. Initialize Terraform

```bash
terraform init
```

This will download the Railway provider plugin.

### 4. Review the Deployment Plan

```bash
terraform plan
```

This shows what resources will be created:

- Railway project
- Production environment (Railway automatically creates the default environment
  when the project is created)
- Backend service (Spring Boot API)
- Frontend service (Angular web app)
- Environment variables
- Custom domains (if configured)

### 5. Deploy to Railway

```bash
terraform apply
```text

Type `yes` when prompted to confirm the deployment.

### 6. View Deployment URLs

After deployment completes, Terraform will output the URLs:

```bash
terraform output
```

Example output:

```text
backend_url = "https://mobilispect-backend-production.railway.app"
frontend_url = "https://mobilispect-frontend-production.railway.app"
```

## Configuration Details

### Backend Service

The backend service:

- Builds from `/backend` directory using the Dockerfile
- Runs on Java 21 with Spring Boot
- Exposes port 8080
- Connects to MongoDB Atlas
- Uses Transit Land API for transit data

Environment variables set:

- `SPRING_DATA_MONGODB_URI` - MongoDB connection string
- `SPRING_DATA_MONGODB_DATABASE` - Database name
- `TRANSIT_LAND_API_KEY` - Transit Land API key
- `LOGGING_LEVEL_COM_MOBILISPECT_BACKEND` - Log level
- `PORT` - Application port (8080)

### Frontend Service

The frontend service:

- Builds from `/frontend/web` directory
- Uses Node.js 20 to build Angular app
- Serves static files with `serve` package
- Runs on port 3000
- Automatically configured to connect to backend API

Environment variables set:

- `API_URL` - Backend API URL (automatically set)

Each service also gets a predictable Railway-managed subdomain derived from
`project_name`, and Terraform assumes the standard `*.up.railway.app` suffix.
Override `backend_service_subdomain`, `frontend_service_subdomain`, or
`railway_domain_suffix` if you need specific naming.

## Custom Domains

To use custom domains:

1. Configure DNS records for your domains:
   - For backend: Add CNAME pointing to Railway's domain
   - For frontend: Add CNAME pointing to Railway's domain

2. Set the domain variables in `terraform.tfvars`:

   ```hcl
   backend_custom_domain = "api.yourdomain.com"
   frontend_custom_domain = "yourdomain.com"
   ```

3. Apply changes:

   ```bash
   terraform apply
   ```

Railway will automatically provision SSL certificates.

## Managing the Deployment

### View Resources

```bash
# List all resources
terraform state list

# Show details of a specific resource
terraform show
```

### Update Configuration

1. Modify `terraform.tfvars` or Terraform files
2. Run `terraform plan` to preview changes
3. Run `terraform apply` to apply changes

### Destroy Resources

⚠️ **Warning**: This will delete all resources and data!

```bash
terraform destroy
```

## Monitoring and Logs

### Via Railway CLI

```bash
# View backend logs
railway logs --service backend

# View frontend logs
railway logs --service frontend

# Open Railway dashboard
railway open
```

### Via Railway Dashboard

Visit [railway.app](https://railway.app) and navigate to your project.

## Troubleshooting

### Build Failures

**Backend build fails:**

- Check that Java 21 is specified in Dockerfile
- Verify all dependencies are accessible
- Check Railway build logs: `railway logs --service backend`

**Frontend build fails:**

- Verify Node.js version (should use 20)
- Check that `serve` is in dependencies
- Review `nixpacks.toml` configuration

### Connection Issues

**Backend can't connect to MongoDB:**

- Verify MongoDB URI is correct
- Check MongoDB Atlas IP whitelist (should allow all: 0.0.0.0/0)
- Ensure network access is enabled in MongoDB Atlas

**Frontend can't reach backend:**

- Check that `API_URL` environment variable is set correctly
- Verify CORS is configured in backend
- Check Railway service URLs in outputs

### Environment Variables

To update environment variables:

1. Edit `terraform.tfvars`
2. Run `terraform apply`

Or use Railway CLI:

```bash
railway variables set KEY=value --service backend
```

## Cost Optimization

Railway pricing is based on:

- Active CPU/memory usage
- Bandwidth
- Deployed services

Tips to reduce costs:

- Use Railway's free tier for development
- Scale down services when not in use
- Monitor usage in Railway dashboard

## Additional Resources

- [Railway Documentation](https://docs.railway.app/)
- [Terraform Railway Provider](https://registry.terraform.io/providers/terraform-community-providers/railway)
- [Railway Pricing](https://railway.app/pricing)
- [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)

## Support

For issues with:

- **Mobilispect application**: Open issue on GitHub
- **Railway platform**: Check [Railway docs](https://docs.railway.app/) or Discord
- **Terraform**: See [Terraform docs](https://www.terraform.io/docs)
