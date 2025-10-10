#!/bin/bash
# ADR Compliance Script - Constitutional Documentation Requirements
# Validates Architecture Decision Record compliance per Mobilispect Constitution

set -e

echo "📋 CONSTITUTIONAL ADR COMPLIANCE VALIDATION"
echo "=========================================="
echo "Requirement: All architectural decisions must be documented"
echo "Constitution: v1.3.0 - ADR documentation MANDATORY"
echo ""

# Track ADR compliance status
ADR_COMPLIANCE_FAILED=false

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to log ADR results
log_adr_result() {
    local check_name="$1"
    local status="$2"
    local message="$3"

    if [ "$status" = "PASS" ]; then
        echo "✅ $check_name: $message"
    elif [ "$status" = "WARN" ]; then
        echo "⚠️  $check_name: $message"
    else
        echo "❌ $check_name: $message"
        ADR_COMPLIANCE_FAILED=true
    fi
}

echo "🏗️  ADR DIRECTORY STRUCTURE VALIDATION"
echo "====================================="

# Check if ADR directory exists
if [ ! -d "docs/adr" ]; then
    echo "📁 Creating ADR directory structure..."
    mkdir -p docs/adr

    # Create ADR README
    cat > docs/adr/README.md << 'EOF'
# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records as required by the Mobilispect Constitution v1.3.0.

## Constitutional Requirement

**ALL architectural decisions MUST be documented here** - this is a NON-NEGOTIABLE constitutional requirement.

## ADR Format

Each ADR must follow this structure:

### File Naming
- Format: `NNNN-decision-title.md`
- Example: `0001-use-kotlin-for-backend.md`
- Sequential numbering starting from 0001

### Required Sections

```markdown
# NNNN. [Decision Title]

**Status**: [Proposed | Accepted | Deprecated | Superseded]
**Date**: YYYY-MM-DD
**Deciders**: [List of decision makers]

## Context
What is the issue that we're seeing that is motivating this decision or change?

## Decision
What is the change that we're proposing and/or doing?

## Consequences
What becomes easier or more difficult to do because of this change?

## Alternatives Considered
What other options were evaluated and why were they rejected?

## Implementation Notes
Technical details, migration steps, or other implementation considerations.

## References
Links to relevant documentation, discussions, or resources.
```

## Examples of Decisions Requiring ADRs

- Technology stack choices (frameworks, languages, databases)
- Architecture patterns (microservices, event-driven, etc.)
- API design decisions
- Data storage and modeling decisions
- Security architecture choices
- DevOps and deployment strategies
- Performance optimization approaches
- Third-party service integrations

## Constitutional Enforcement

- **Pre-merge checks verify ADR compliance**
- **Significant architectural changes require ADR creation**
- **ADR reviews are part of the code review process**
- **No exceptions without constitutional emergency procedures**

## ADR Tools

- Use `scripts/adr-compliance.sh` to validate compliance
- Template available in this directory
- Integration with CI/CD pipeline for enforcement

---

*This documentation structure enforces Mobilispect Constitution v1.3.0 requirements.*
EOF

    # Create ADR template
    cat > docs/adr/template.md << 'EOF'
# NNNN. [Decision Title]

**Status**: Proposed
**Date**: YYYY-MM-DD
**Deciders**: [Your Name, Team Lead, etc.]

## Context

[Describe the context and problem statement that led to this decision.]

## Decision

[Describe the decision that was made.]

## Consequences

### Positive
- [List positive consequences]

### Negative
- [List negative consequences or trade-offs]

### Neutral
- [List neutral consequences]

## Alternatives Considered

### Option 1: [Alternative Name]
- **Pros**: [List advantages]
- **Cons**: [List disadvantages]
- **Reason for rejection**: [Explain why this wasn't chosen]

### Option 2: [Alternative Name]
- **Pros**: [List advantages]
- **Cons**: [List disadvantages]
- **Reason for rejection**: [Explain why this wasn't chosen]

## Implementation Notes

[Technical details, migration steps, timeline, etc.]

## References

- [Link to relevant documentation]
- [Link to discussions or RFCs]
- [Link to related ADRs]

---

*ADR created per Mobilispect Constitution v1.3.0 requirements.*
EOF

    log_adr_result "ADR Directory Structure" "PASS" "Created with constitutional template"
else
    log_adr_result "ADR Directory Structure" "PASS" "Directory exists"
fi

echo ""
echo "📄 ADR CONTENT VALIDATION"
echo "========================"

# Count existing ADRs
ADR_COUNT=$(find docs/adr -name "*.md" -not -name "README.md" -not -name "template.md" | wc -l | tr -d ' ')
echo "📊 Found $ADR_COUNT existing ADRs"

if [ "$ADR_COUNT" -eq 0 ]; then
    log_adr_result "ADR Documentation" "WARN" "No ADRs found - ensure architectural decisions are documented"
else
    log_adr_result "ADR Documentation" "PASS" "$ADR_COUNT ADRs documented"

    # Validate ADR naming convention
    echo ""
    echo "🔍 Validating ADR naming convention..."
    INVALID_NAMES=0
    for adr_file in docs/adr/*.md; do
        if [ -f "$adr_file" ] && [ "$(basename "$adr_file")" != "README.md" ] && [ "$(basename "$adr_file")" != "template.md" ]; then
            filename=$(basename "$adr_file")
            if [[ ! "$filename" =~ ^[0-9]{4}-.*\.md$ ]]; then
                echo "⚠️  Invalid ADR naming: $filename (should be NNNN-title.md)"
                INVALID_NAMES=$((INVALID_NAMES + 1))
            fi
        fi
    done

    if [ "$INVALID_NAMES" -eq 0 ]; then
        log_adr_result "ADR Naming Convention" "PASS" "All ADRs follow NNNN-title.md format"
    else
        log_adr_result "ADR Naming Convention" "WARN" "$INVALID_NAMES ADRs have invalid naming"
    fi
fi

echo ""
echo "🔍 ARCHITECTURAL CHANGE DETECTION"
echo "================================"

# Check for recent architectural changes that might need ADRs
echo "🔍 Scanning for recent architectural changes..."

ARCHITECTURAL_FILES=()
if command_exists git; then
    # Check last 5 commits for architectural changes
    for commit in $(git log --oneline -5 --format="%H" 2>/dev/null || echo ""); do
        if [ -n "$commit" ]; then
            # Look for architectural files in this commit
            while IFS= read -r file; do
                if [[ "$file" =~ \.(gradle|kts|yaml|yml|properties|json|xml|toml)$ ]] && [[ ! "$file" =~ ^(build/|\.gradle/|node_modules/) ]]; then
                    ARCHITECTURAL_FILES+=("$file")
                fi
            done < <(git diff-tree --no-commit-id --name-only -r "$commit" 2>/dev/null || echo "")
        fi
    done
fi

# Remove duplicates and count
UNIQUE_ARCH_FILES=($(printf "%s\n" "${ARCHITECTURAL_FILES[@]}" | sort -u))
ARCH_CHANGES=${#UNIQUE_ARCH_FILES[@]}

echo "📊 Detected $ARCH_CHANGES recent architectural file changes:"
for file in "${UNIQUE_ARCH_FILES[@]:0:10}"; do  # Show first 10
    echo "   - $file"
done

if [ "$ARCH_CHANGES" -gt 10 ]; then
    echo "   ... and $((ARCH_CHANGES - 10)) more"
fi

# Assess if ADRs might be needed
if [ "$ARCH_CHANGES" -gt 5 ]; then
    log_adr_result "Architectural Change Impact" "WARN" "$ARCH_CHANGES files changed - review if ADRs needed"
elif [ "$ARCH_CHANGES" -gt 0 ]; then
    log_adr_result "Architectural Change Impact" "PASS" "$ARCH_CHANGES files changed - minimal impact"
else
    log_adr_result "Architectural Change Impact" "PASS" "No significant architectural changes detected"
fi

echo ""
echo "🔧 ADR TOOLING VALIDATION"
echo "========================"

# Check for ADR tooling
if command_exists adr; then
    log_adr_result "ADR CLI Tool" "PASS" "adr-tools available for ADR management"
else
    log_adr_result "ADR CLI Tool" "WARN" "Consider installing adr-tools for easier ADR management"
    echo "   Install: brew install adr-tools"
fi

# Check for git hooks integration
if [ -f ".pre-commit-config.yaml" ]; then
    if grep -q "adr" .pre-commit-config.yaml; then
        log_adr_result "ADR Pre-commit Integration" "PASS" "ADR checks integrated with pre-commit"
    else
        log_adr_result "ADR Pre-commit Integration" "WARN" "Consider adding ADR validation to pre-commit"
    fi
else
    log_adr_result "ADR Pre-commit Integration" "WARN" "No pre-commit configuration found"
fi

echo ""
echo "📚 ADR BEST PRACTICES VALIDATION"
echo "==============================="

# Check for common ADR quality indicators
if [ -d "docs/adr" ] && [ "$ADR_COUNT" -gt 0 ]; then
    echo "🔍 Analyzing ADR quality indicators..."

    # Check for template compliance
    TEMPLATE_COMPLIANT=0
    MISSING_SECTIONS=0

    for adr_file in docs/adr/*.md; do
        if [ -f "$adr_file" ] && [ "$(basename "$adr_file")" != "README.md" ] && [ "$(basename "$adr_file")" != "template.md" ]; then
            # Check for required sections
            if grep -q "## Context" "$adr_file" && grep -q "## Decision" "$adr_file" && grep -q "## Consequences" "$adr_file"; then
                TEMPLATE_COMPLIANT=$((TEMPLATE_COMPLIANT + 1))
            else
                MISSING_SECTIONS=$((MISSING_SECTIONS + 1))
            fi
        fi
    done

    if [ "$MISSING_SECTIONS" -eq 0 ]; then
        log_adr_result "ADR Template Compliance" "PASS" "All ADRs follow required template structure"
    else
        log_adr_result "ADR Template Compliance" "WARN" "$MISSING_SECTIONS ADRs missing required sections"
    fi

    # Check for recent ADR activity
    if command_exists git; then
        RECENT_ADR_COMMITS=$(git log --since="30 days ago" --oneline -- docs/adr/ 2>/dev/null | wc -l | tr -d ' ')
        if [ "$RECENT_ADR_COMMITS" -gt 0 ]; then
            log_adr_result "ADR Activity" "PASS" "$RECENT_ADR_COMMITS ADR updates in last 30 days"
        else
            log_adr_result "ADR Activity" "WARN" "No ADR updates in last 30 days - ensure decisions are being documented"
        fi
    fi
fi

echo ""
echo "📋 CONSTITUTIONAL ADR COMPLIANCE SUMMARY"
echo "======================================="

echo "🏛️ Constitutional Requirements:"
echo "   - ADR documentation: MANDATORY for all architectural decisions"
echo "   - Template compliance: Required sections must be present"
echo "   - Naming convention: NNNN-decision-title.md format"
echo "   - Regular updates: Document decisions as they are made"

echo ""
echo "📊 Compliance Metrics:"
echo "   - ADR Directory: $([ -d "docs/adr" ] && echo "✅ Present" || echo "❌ Missing")"
echo "   - ADR Count: $ADR_COUNT documented decisions"
echo "   - Recent Changes: $ARCH_CHANGES architectural files modified"
echo "   - Template Compliance: $([ "$MISSING_SECTIONS" -eq 0 ] && echo "✅ Good" || echo "⚠️ Needs improvement")"

echo ""
echo "🎯 ADR Best Practices:"
echo "   - Document decisions when made, not after implementation"
echo "   - Include context, alternatives, and consequences"
echo "   - Link to relevant code changes and discussions"
echo "   - Update status when decisions are superseded"
echo "   - Review ADRs during architectural reviews"

# Final ADR compliance result
echo ""
if [ "$ADR_COMPLIANCE_FAILED" = true ]; then
    echo "❌ ADR COMPLIANCE FAILED"
    echo "   Constitutional documentation requirements NOT met"
    echo "   Address ADR compliance issues before merge"
    echo ""
    echo "📋 Required actions:"
    echo "   - Create missing ADR directory structure"
    echo "   - Document recent architectural decisions"
    echo "   - Fix ADR naming convention violations"
    echo "   - Ensure template compliance"
    exit 1
else
    echo "✅ ADR COMPLIANCE PASSED"
    echo "   Constitutional documentation requirements satisfied"
    echo "   ADR compliance gate approved"
    echo ""
    echo "📚 Continue documenting architectural decisions as they are made"
    echo "🏛️ Constitutional documentation standards maintained"
fi
