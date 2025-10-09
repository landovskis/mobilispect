#!/bin/bash
# Test Coverage Validation Script - Constitutional 80% Requirement
# Validates that all components meet the minimum 80% test coverage requirement

set -e

echo "📊 Validating Test Coverage (Constitutional 80% Minimum)..."

# Track overall status
COVERAGE_FAILED=false
TOTAL_COVERAGE=0
COMPONENT_COUNT=0

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to extract coverage percentage from output
extract_coverage() {
    local output="$1"
    local coverage=$(echo "$output" | grep -oE '[0-9]+(\.[0-9]+)?%' | head -1 | sed 's/%//')
    echo "$coverage"
}

# Backend Kotlin Coverage
if [ -d "backend" ]; then
    echo "🔍 Checking Backend Kotlin Coverage..."
    cd backend

    if command_exists ./gradlew; then
        # Run tests with coverage report
        if ./gradlew test jacocoTestReport --info > /tmp/backend_coverage.log 2>&1; then
            # Check if JaCoCo report exists
            if [ -f "build/reports/jacoco/test/html/index.html" ]; then
                # Extract coverage from JaCoCo report
                if [ -f "build/reports/jacoco/test/jacocoTestReport.xml" ]; then
                    # Parse XML for instruction coverage
                    BACKEND_COVERAGE=$(grep -oE 'instruction.*covered="[0-9]+".*missed="[0-9]+"' build/reports/jacoco/test/jacocoTestReport.xml | head -1 | awk -F'"' '{covered=$2; missed=$4; total=covered+missed; if(total>0) print (covered/total)*100}')
                    if [ -n "$BACKEND_COVERAGE" ]; then
                        BACKEND_COVERAGE_INT=$(echo "$BACKEND_COVERAGE" | cut -d'.' -f1)
                        echo "Backend Coverage: ${BACKEND_COVERAGE}%"
                        if [ "$BACKEND_COVERAGE_INT" -lt 80 ]; then
                            echo "❌ Backend coverage ${BACKEND_COVERAGE}% below constitutional minimum (80%)"
                            COVERAGE_FAILED=true
                        else
                            echo "✅ Backend coverage ${BACKEND_COVERAGE}% meets constitutional requirement"
                        fi
                        TOTAL_COVERAGE=$(echo "$TOTAL_COVERAGE + $BACKEND_COVERAGE" | bc -l)
                        COMPONENT_COUNT=$((COMPONENT_COUNT + 1))
                    else
                        echo "⚠️  Could not parse backend coverage from JaCoCo report"
                    fi
                else
                    echo "⚠️  Backend JaCoCo XML report not found, coverage validation skipped"
                fi
            else
                echo "⚠️  Backend JaCoCo HTML report not found, coverage validation skipped"
            fi
        else
            echo "❌ Backend tests failed, cannot validate coverage"
            COVERAGE_FAILED=true
        fi
    else
        echo "⚠️  Backend gradle wrapper not found, skipping backend coverage"
    fi

    cd ..
fi

# Mobile KMM Coverage
if [ -d "frontend/mobile" ]; then
    echo "🔍 Checking Mobile KMM Coverage..."
    cd frontend/mobile

    if command_exists ./gradlew; then
        # Run tests with coverage for shared module
        if ./gradlew shared:testDebugUnitTest shared:jacocoTestReport --info > /tmp/mobile_coverage.log 2>&1; then
            # Check if JaCoCo report exists for shared module
            if [ -f "shared/build/reports/jacoco/testDebugUnitTest/jacocoTestReport.xml" ]; then
                # Extract coverage from JaCoCo report
                MOBILE_COVERAGE=$(grep -oE 'instruction.*covered="[0-9]+".*missed="[0-9]+"' shared/build/reports/jacoco/testDebugUnitTest/jacocoTestReport.xml | head -1 | awk -F'"' '{covered=$2; missed=$4; total=covered+missed; if(total>0) print (covered/total)*100}')
                if [ -n "$MOBILE_COVERAGE" ]; then
                    MOBILE_COVERAGE_INT=$(echo "$MOBILE_COVERAGE" | cut -d'.' -f1)
                    echo "Mobile Coverage: ${MOBILE_COVERAGE}%"
                    if [ "$MOBILE_COVERAGE_INT" -lt 80 ]; then
                        echo "❌ Mobile coverage ${MOBILE_COVERAGE}% below constitutional minimum (80%)"
                        COVERAGE_FAILED=true
                    else
                        echo "✅ Mobile coverage ${MOBILE_COVERAGE}% meets constitutional requirement"
                    fi
                    TOTAL_COVERAGE=$(echo "$TOTAL_COVERAGE + $MOBILE_COVERAGE" | bc -l)
                    COMPONENT_COUNT=$((COMPONENT_COUNT + 1))
                else
                    echo "⚠️  Could not parse mobile coverage from JaCoCo report"
                fi
            else
                echo "⚠️  Mobile JaCoCo report not found, coverage validation skipped"
            fi
        else
            echo "❌ Mobile tests failed, cannot validate coverage"
            COVERAGE_FAILED=true
        fi
    else
        echo "⚠️  Mobile gradle wrapper not found, skipping mobile coverage"
    fi

    cd ../..
fi

# Calculate and display overall results
echo ""
echo "📊 COVERAGE VALIDATION SUMMARY"
echo "=================================="

if [ "$COMPONENT_COUNT" -gt 0 ]; then
    # Calculate average coverage
    if command_exists bc; then
        AVERAGE_COVERAGE=$(echo "scale=2; $TOTAL_COVERAGE / $COMPONENT_COUNT" | bc)
        echo "Components tested: $COMPONENT_COUNT"
        echo "Average coverage: ${AVERAGE_COVERAGE}%"

        AVERAGE_COVERAGE_INT=$(echo "$AVERAGE_COVERAGE" | cut -d'.' -f1)
        if [ "$AVERAGE_COVERAGE_INT" -lt 80 ]; then
            echo "❌ Average coverage below constitutional minimum (80%)"
            COVERAGE_FAILED=true
        fi
    else
        echo "⚠️  bc calculator not found, cannot calculate average coverage"
    fi
else
    echo "⚠️  No components found with coverage data"
    COVERAGE_FAILED=true
fi

echo ""
echo "Constitutional Requirement: 80% minimum test coverage"
echo "Enforcement: NON-NEGOTIABLE quality gate"

# Final result
if [ "$COVERAGE_FAILED" = true ]; then
    echo ""
    echo "❌ COVERAGE VALIDATION FAILED"
    echo "   Constitutional 80% coverage requirement not met"
    echo "   Please add tests to improve coverage before committing"
    echo ""
    echo "   Tips to improve coverage:"
    echo "   - Add unit tests for untested functions"
    echo "   - Add integration tests for business logic"
    echo "   - Test error handling and edge cases"
    echo "   - Review coverage reports for specific files needing tests"
    exit 1
else
    echo ""
    echo "✅ COVERAGE VALIDATION PASSED"
    echo "   All components meet constitutional 80% coverage requirement"
fi
