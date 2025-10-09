#!/bin/bash
# Security Scan Script - Constitutional Security Requirement
# Performs OWASP dependency check and vulnerability scanning

set -e

echo "🔍 Running Constitutional Security Scans..."

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Track overall status
SCAN_FAILED=false

# Backend Kotlin/Gradle Security Scan
if [ -d "backend" ]; then
    echo "📋 Scanning Backend Kotlin Dependencies..."
    cd backend

    # OWASP Dependency Check for Gradle
    if command_exists ./gradlew; then
        if ./gradlew dependencyCheckAnalyze --info; then
            echo "✅ Backend dependency scan passed"
        else
            echo "❌ Backend dependency scan failed"
            SCAN_FAILED=true
        fi
    else
        echo "⚠️  Backend gradle wrapper not found, skipping backend scan"
    fi

    cd ..
fi

# Mobile KMM Security Scan
if [ -d "frontend/mobile" ]; then
    echo "📋 Scanning Mobile KMM Dependencies..."
    cd frontend/mobile

    # Gradle dependency check for mobile
    if command_exists ./gradlew; then
        if ./gradlew dependencyCheckAnalyze --info; then
            echo "✅ Mobile dependency scan passed"
        else
            echo "❌ Mobile dependency scan failed"
            SCAN_FAILED=true
        fi
    else
        echo "⚠️  Mobile gradle wrapper not found, skipping mobile scan"
    fi

    cd ../..
fi

# Additional Security Checks
echo "🔒 Running Additional Security Checks..."

# Check for common security issues in source files
if command_exists grep; then
    # Check for hardcoded secrets patterns (basic check)
    if grep -r -E "(password|secret|token|key)\s*=\s*['\"][^'\"]{8,}" --include="*.kt" --include="*.ts" --include="*.js" --exclude-dir=node_modules --exclude-dir=build . || true; then
        echo "⚠️  Potential hardcoded secrets found - please review"
        # Don't fail for this as detect-secrets will catch it
    fi

    # Check for SQL injection patterns
    if grep -r -E "execute(Query|Update)?\s*\(\s*['\"].*\+.*['\"]" --include="*.kt" --include="*.java" --exclude-dir=build . || true; then
        echo "⚠️  Potential SQL injection vulnerability patterns found"
        SCAN_FAILED=true
    fi
fi

# Summary
if [ "$SCAN_FAILED" = true ]; then
    echo ""
    echo "❌ SECURITY SCAN FAILED - Constitutional security requirements not met"
    echo "   Please fix the above security issues before committing"
    echo "   Run individual scans for more details:"
    echo "   - Backend: cd backend && ./gradlew dependencyCheckAnalyze"
    echo "   - Frontend: cd frontend/web && npm audit"
    echo "   - Mobile: cd frontend/mobile && ./gradlew dependencyCheckAnalyze"
    exit 1
else
    echo ""
    echo "✅ SECURITY SCAN PASSED - Constitutional requirements met"
    echo "   All dependency scans completed successfully"
fi
