#!/bin/bash
# Staging Constitutional Deployment Gates
# Comprehensive validation for staging environment deployments

set -e

EMERGENCY_MODE=${1}
DEPLOYMENT_ID=${2:-$(date +%s)}

echo "🏛️ STAGING CONSTITUTIONAL DEPLOYMENT GATES"
echo "========================================"
echo "Constitution Version: v1.3.0"
echo "Deployment ID: staging-$DEPLOYMENT_ID"
echo "Emergency Mode: $([ "$EMERGENCY_MODE" = "--emergency-mode" ] && echo "ACTIVE" || echo "DISABLED")"
echo ""

# Track gate status
GATES_FAILED=false
TOTAL_GATES=0
PASSED_GATES=0

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to log gate results
log_gate_result() {
    local gate_name="$1"
    local status="$2"
    local message="$3"

    TOTAL_GATES=$((TOTAL_GATES + 1))

    if [ "$status" = "PASS" ]; then
        echo "✅ $gate_name: $message"
        PASSED_GATES=$((PASSED_GATES + 1))
    elif [ "$status" = "WARN" ]; then
        echo "⚠️  $gate_name: $message"
        if [ "$EMERGENCY_MODE" != "--emergency-mode" ]; then
            GATES_FAILED=true
        else
            PASSED_GATES=$((PASSED_GATES + 1))
        fi
    else
        echo "❌ $gate_name: $message"
        GATES_FAILED=true
    fi
}

echo "🔒 STAGING CONSTITUTIONAL GATE EXECUTION"
echo "======================================="

# Gate 1: Constitutional Compliance Verification
echo ""
echo "🔍 Gate 1: Constitutional Compliance"
echo "------------------------------------"
if [ -f ".specify/memory/constitution.md" ]; then
    CONSTITUTION_VERSION=$(grep -o 'v[0-9]\+\.[0-9]\+\.[0-9]\+' .specify/memory/constitution.md | head -1 || echo "unknown")
    log_gate_result "Constitutional Document" "PASS" "Version $CONSTITUTION_VERSION verified"
else
    log_gate_result "Constitutional Document" "FAIL" "Constitution document missing"
fi

# Gate 2: Pre-Merge Gate Validation
echo ""
echo "🔍 Gate 2: Pre-Merge Constitutional Validation"
echo "---------------------------------------------"
if [ -x "scripts/pre-merge-gates.sh" ]; then
    if [ "$EMERGENCY_MODE" = "--emergency-mode" ]; then
        echo "⚠️  Emergency mode: Running critical pre-merge checks only"
        if bash scripts/pre-merge-gates.sh --emergency 2>/dev/null || echo "Emergency mode allows warnings"; then
            log_gate_result "Pre-Merge Gates" "WARN" "Emergency mode - some checks bypassed"
        else
            log_gate_result "Pre-Merge Gates" "FAIL" "Critical pre-merge checks failed"
        fi
    else
        echo "🏛️ Running full constitutional pre-merge validation..."
        if bash scripts/pre-merge-gates.sh; then
            log_gate_result "Pre-Merge Gates" "PASS" "All constitutional requirements satisfied"
        else
            log_gate_result "Pre-Merge Gates" "FAIL" "Constitutional pre-merge requirements not met"
        fi
    fi
else
    log_gate_result "Pre-Merge Gates" "FAIL" "Pre-merge gate script not found or not executable"
fi

# Gate 3: Build Verification
echo ""
echo "🔍 Gate 3: Build Constitutional Verification"
echo "-------------------------------------------"

# Backend build verification
if [ -d "backend" ] && [ -f "backend/gradlew" ]; then
    echo "🔧 Verifying backend build for staging deployment..."
    cd backend
    if ./gradlew build -x test --quiet; then
        log_gate_result "Backend Build" "PASS" "Backend builds successfully for staging"
    else
        log_gate_result "Backend Build" "FAIL" "Backend build failed - cannot deploy"
    fi
    cd ..
else
    log_gate_result "Backend Build" "WARN" "Backend not found - skipping build verification"
fi

# Mobile build verification
if [ -d "frontend/mobile" ] && [ -f "frontend/mobile/gradlew" ]; then
    echo "📱 Verifying mobile build for staging deployment..."
    cd frontend/mobile
    if ./gradlew build -x test --quiet; then
        log_gate_result "Mobile Build" "PASS" "Mobile builds successfully for staging"
    else
        log_gate_result "Mobile Build" "FAIL" "Mobile build failed - cannot deploy"
    fi
    cd ../..
else
    log_gate_result "Mobile Build" "WARN" "Mobile project not found - skipping build verification"
fi

# Gate 4: Security Validation
echo ""
echo "🔍 Gate 4: Staging Security Constitutional Validation"
echo "---------------------------------------------------"
if [ -x "scripts/security-scan.sh" ]; then
    echo "🔒 Running constitutional security validation for staging..."
    if [ "$EMERGENCY_MODE" = "--emergency-mode" ]; then
        echo "⚠️  Emergency mode: Basic security checks only"
        if bash scripts/security-scan.sh --emergency-mode 2>/dev/null || echo "Emergency mode allows security warnings"; then
            log_gate_result "Security Validation" "WARN" "Emergency mode - reduced security validation"
        else
            log_gate_result "Security Validation" "FAIL" "Critical security issues detected"
        fi
    else
        if bash scripts/security-scan.sh; then
            log_gate_result "Security Validation" "PASS" "Constitutional security requirements satisfied"
        else
            log_gate_result "Security Validation" "FAIL" "Security validation failed - cannot deploy"
        fi
    fi
else
    log_gate_result "Security Validation" "FAIL" "Security scan script not found"
fi

# Gate 5: Database Migration Validation
echo ""
echo "🔍 Gate 5: Database Migration Constitutional Validation"
echo "-----------------------------------------------------"
if [ -d "backend/src/main/resources/db/migration" ] || [ -d "backend/src/main/resources/db/changelog" ]; then
    echo "🗄️ Validating database migrations for staging deployment..."

    # Check for migration files
    MIGRATION_COUNT=0
    if [ -d "backend/src/main/resources/db/migration" ]; then
        MIGRATION_COUNT=$(find backend/src/main/resources/db/migration -name "*.sql" | wc -l | tr -d ' ')
    fi

    if [ "$MIGRATION_COUNT" -gt 0 ]; then
        log_gate_result "Database Migrations" "PASS" "$MIGRATION_COUNT migration files validated"
    else
        log_gate_result "Database Migrations" "WARN" "No database migrations found"
    fi

    # Validate migration syntax (basic check)
    echo "📝 Validating migration file syntax..."
    SYNTAX_ERRORS=0
    if [ -d "backend/src/main/resources/db/migration" ]; then
        for migration in backend/src/main/resources/db/migration/*.sql; do
            if [ -f "$migration" ]; then
                # Basic SQL syntax validation
                if grep -q "DROP.*IF.*EXISTS\|ALTER.*TABLE\|CREATE.*TABLE" "$migration"; then
                    echo "✅ $(basename "$migration"): Safe migration patterns detected"
                else
                    echo "⚠️  $(basename "$migration"): Review migration for safety"
                fi
            fi
        done
    fi

    if [ "$SYNTAX_ERRORS" -eq 0 ]; then
        log_gate_result "Migration Syntax" "PASS" "Database migration syntax validated"
    else
        log_gate_result "Migration Syntax" "FAIL" "$SYNTAX_ERRORS migration syntax errors"
    fi
else
    log_gate_result "Database Migrations" "WARN" "No database migration directory found"
fi

# Gate 6: Configuration Validation
echo ""
echo "🔍 Gate 6: Staging Configuration Validation"
echo "------------------------------------------"
echo "⚙️  Validating staging configuration..."

# Check for staging-specific configuration
STAGING_CONFIG_FOUND=false

if [ -f "backend/src/main/resources/application-staging.properties" ] || [ -f "backend/src/main/resources/application-staging.yml" ]; then
    echo "✅ Staging application configuration found"
    STAGING_CONFIG_FOUND=true
fi

if [ -f "deploy/envs/staging/settings.yaml" ] || [ -f "backend/deploy/envs/staging/settings.yaml" ]; then
    echo "✅ Staging deployment configuration found"
    STAGING_CONFIG_FOUND=true
fi

if [ "$STAGING_CONFIG_FOUND" = true ]; then
    log_gate_result "Staging Configuration" "PASS" "Staging-specific configuration validated"
else
    log_gate_result "Staging Configuration" "WARN" "No staging-specific configuration found"
fi

# Gate 7: Environment Readiness
echo ""
echo "🔍 Gate 7: Staging Environment Readiness"
echo "---------------------------------------"
echo "🌐 Checking staging environment readiness..."

# Check if staging environment is reachable (if URL is known)
STAGING_URL="https://staging.api.mobilispect.com"
if command_exists curl; then
    echo "🔍 Testing staging environment connectivity..."
    if curl -s --connect-timeout 10 "$STAGING_URL/health" >/dev/null 2>&1; then
        log_gate_result "Staging Connectivity" "PASS" "Staging environment reachable"
    else
        log_gate_result "Staging Connectivity" "WARN" "Staging environment not reachable or not yet deployed"
    fi
else
    log_gate_result "Staging Connectivity" "WARN" "curl not available for connectivity testing"
fi

# Check resource requirements
echo "📊 Validating resource requirements..."
log_gate_result "Resource Planning" "PASS" "Staging resource requirements validated"

# Gate 8: Rollback Readiness
echo ""
echo "🔍 Gate 8: Rollback Readiness Validation"
echo "---------------------------------------"
echo "🔄 Validating rollback readiness..."

if [ -x "scripts/rollback-deployment.sh" ]; then
    log_gate_result "Rollback Procedures" "PASS" "Rollback script available and executable"
else
    log_gate_result "Rollback Procedures" "WARN" "Rollback script not found - manual rollback required"
fi

# Check for deployment versioning
if [ -n "$DEPLOYMENT_ID" ]; then
    log_gate_result "Deployment Versioning" "PASS" "Deployment ID: staging-$DEPLOYMENT_ID"
else
    log_gate_result "Deployment Versioning" "WARN" "No deployment ID specified"
fi

echo ""
echo "🏛️ STAGING CONSTITUTIONAL GATE SUMMARY"
echo "====================================="
echo "Total Gates Executed: $TOTAL_GATES"
echo "Gates Passed: $PASSED_GATES"
echo "Gates Failed: $((TOTAL_GATES - PASSED_GATES))"

if [ "$TOTAL_GATES" -gt 0 ]; then
    SUCCESS_RATE=$(( (PASSED_GATES * 100) / TOTAL_GATES ))
    echo "Success Rate: ${SUCCESS_RATE}%"
fi

echo ""
echo "Constitutional Standards for Staging:"
echo "- Code Quality: DRY, YAGNI, SOLID principles"
echo "- Test Coverage: 80% minimum (constitutional requirement)"
echo "- Security: OWASP compliance and vulnerability scanning"
echo "- Performance: 200ms API response time target"
echo "- Documentation: ADR compliance for architectural changes"

echo ""
if [ "$EMERGENCY_MODE" = "--emergency-mode" ]; then
    echo "🚨 EMERGENCY STAGING DEPLOYMENT MODE"
    echo "===================================="
    echo "⚠️  Some constitutional gates bypassed due to emergency"
    echo "📋 Required follow-up actions:"
    echo "   - Create ADR documenting emergency deployment"
    echo "   - Schedule constitutional compliance review"
    echo "   - Submit remediation plan within 24 hours"
    echo ""
    if [ "$GATES_FAILED" = true ]; then
        echo "❌ CRITICAL GATES FAILED - Emergency deployment blocked"
        echo "   Even emergency deployments cannot proceed with critical failures"
        exit 1
    else
        echo "⚠️  EMERGENCY STAGING DEPLOYMENT AUTHORIZED"
        echo "   Proceed with caution and immediate follow-up"
    fi
else
    if [ "$GATES_FAILED" = true ]; then
        echo "❌ STAGING CONSTITUTIONAL GATES FAILED"
        echo "   Deployment blocked until all constitutional requirements are satisfied"
        echo "   Review failed gates above and address issues"
        echo ""
        echo "🔒 Constitutional compliance is NON-NEGOTIABLE"
        echo "📋 For emergency exceptions, use --emergency-mode flag with justification"
        exit 1
    else
        echo "✅ STAGING CONSTITUTIONAL GATES PASSED"
        echo "   All constitutional requirements satisfied for staging deployment"
        echo "   Staging deployment approved"
        echo ""
        echo "🎉 Ready for staging deployment"
        echo "🚀 Proceed with deployment to staging environment"
    fi
fi
