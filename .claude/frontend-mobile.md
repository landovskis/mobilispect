# Frontend Mobile Configuration

## Technology Stack

- **Shared Logic**: Kotlin Multiplatform Mobile (KMM)
- **Android UI**: Jetpack Compose with Material Design 3
- **iOS UI**: SwiftUI with iOS Design Guidelines

## Development Workflow

Follow these steps for every mobile change (TDD is constitutional requirement):

### For Shared Logic (KMM)

#### Step 1: Create Your Test First

```bash
# Navigate to shared test directory
cd frontend/mobile/shared/src/commonTest/kotlin/com/mobilispect

# Create your test file (if new): YourFeatureTest.kt
# Write a failing test in commonTest
```

#### Step 2: Run Test to Verify It Fails

```bash
./gradlew :shared:test
```

Expected: Test fails (red) ✗

#### Step 3: Write Minimum Implementation

```bash
# Navigate to shared source directory
cd frontend/mobile/shared/src/commonMain/kotlin/com/mobilispect

# Write just enough code to make the test pass
```

#### Step 4: Run Test to Verify It Passes

```bash
./gradlew :shared:test
```

Expected: Test passes (green) ✓

#### Step 5: Format Your Code

```bash
./gradlew ktlintFormat
```

#### Step 6: Commit (follows steps 9-10 below)

### For Android-Specific Code

#### Step 1: Create Your Test First

```bash
# Navigate to Android test directory
cd frontend/mobile/androidApp/src/test/kotlin/com/mobilispect/android

# Create your test file (if new): YourFeatureTest.kt
# Write a failing test
```

#### Step 2: Run Test to Verify It Fails

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'YourFeatureTest'
```

Expected: Test fails (red) ✗

#### Step 3: Write Minimum Implementation

```bash
# Navigate to Android source directory
cd frontend/mobile/androidApp/src/main/kotlin/com/mobilispect/android

# Write just enough code to make the test pass
```

#### Step 4: Run Test to Verify It Passes

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'YourFeatureTest'
```

Expected: Test passes (green) ✓

#### Step 5: Format Your Code

```bash
./gradlew ktlintFormat
```

#### Step 6: Run Android Lint

```bash
./gradlew :androidApp:lintDebug
```

Fix any violations, then re-run until clean.

#### Step 7: Run All Android Tests

```bash
./gradlew :androidApp:testDebugUnitTest
```

All tests must pass ✓

#### Step 8: Verify Coverage

```bash
./scripts/validate-coverage.sh android
```

If below 80%, add more tests and repeat from Step 1.

### For iOS-Specific Code

#### Step 1: Create Your Test First

```bash
# Open Xcode project
open frontend/mobile/iosApp/iosApp.xcodeproj

# Create your test file in iosAppTests (if new): YourFeatureTests.swift
# Write a failing test using XCTest
```

#### Step 2: Run Test to Verify It Fails

```bash
# In Xcode: Cmd+U or via command line:
xcodebuild test -project iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 15'
```

Expected: Test fails (red) ✗

#### Step 3: Write Minimum Implementation

```bash
# Edit your Swift file in iosApp/
# Write just enough code to make the test pass
```

#### Step 4: Run Test to Verify It Passes

```bash
# In Xcode: Cmd+U or via command line:
xcodebuild test -project iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 15'
```

Expected: Test passes (green) ✓

#### Step 5: Format Your Code

```bash
swiftformat frontend/mobile/iosApp/
```

#### Step 6: Run SwiftLint

```bash
swiftlint lint --path frontend/mobile/iosApp/
```

Fix any violations, then re-run until clean.

#### Step 7: Run All iOS Tests

```bash
xcodebuild test -project iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 15'
```

All tests must pass ✓

#### Step 8: Verify Coverage

```bash
./scripts/validate-coverage.sh ios
```

If below 80%, add more tests and repeat from Step 1.

### Final Steps (All Platforms)

#### Step 9: Pre-Commit Verification

```bash
# Run all pre-commit hooks manually
pre-commit run --all-files
```

All hooks must pass ✓

#### Step 10: Commit Your Changes

```bash
git add .
git commit -m "feat: your feature description"
```

Pre-commit hooks will run automatically. If they fail, fix issues and retry.

## Pre-Commit Hooks

Mobile-specific hooks enforced via `.pre-commit-config.yaml`:

### Android
- **ktlint**: Kotlin code formatting (auto-fixes on commit)
- **Android lint**: Platform-specific linting (blocks commit on errors)
- **Unit tests**: JUnit tests must pass
- **Coverage validation**: ≥80% threshold

### iOS
- **SwiftFormat**: Code formatting (auto-fixes on commit)
- **SwiftLint**: Linting and code quality (blocks commit on warnings)
- **XCTest**: Unit tests execution (must pass)
- **Coverage validation**: ≥80% threshold

## Quick Reference Commands

### Shared (KMM)
```bash
# Test shared code
./gradlew :shared:test

# Build shared module
./gradlew :shared:build
```

### Android
```bash
# Format code
./gradlew ktlintFormat

# Lint check
./gradlew :androidApp:lintDebug

# Run unit tests
./gradlew :androidApp:testDebugUnitTest

# Run specific test
./gradlew :androidApp:testDebugUnitTest --tests 'YourTest'

# Check coverage
./scripts/validate-coverage.sh android

# Build APK
./gradlew :androidApp:assembleDebug
```

### iOS
```bash
# Format code
swiftformat iosApp/

# Lint check
swiftlint lint --path iosApp/

# Run tests
xcodebuild test -project iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 15'

# Check coverage
./scripts/validate-coverage.sh ios

# Build
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug
```

### All Platforms
```bash
# Run all pre-commit hooks
pre-commit run --all-files
```

## IDE Integration

### Android Studio
- Pre-commit plugin configuration
- Enable ktlint and Android lint plugins
- Compose preview support
- Configure "Reformat Code" to use ktlint
- Enable Kotlin Multiplatform Mobile plugin

### Xcode
- Build phases for SwiftLint/SwiftFormat integration
- Enable Swift package manager support
- Configure code coverage settings
- Add Run Script phases for SwiftLint/SwiftFormat
- Configure scheme for code coverage collection

## Mobile-Specific Requirements

See [CLAUDE.md](../CLAUDE.md) for constitutional requirements on accessibility (WCAG 2.1 AA), performance targets (60fps), light/dark theme parity, and testing standards (≥80% coverage). This section covers mobile-specific implementation details.

### Kotlin Multiplatform Mobile (KMM)

- Shared business logic in `commonMain`
- Platform-specific implementations in `androidMain` and `iosMain`
- Expect/actual declarations for platform APIs
- Minimize platform-specific code

### Android Implementation

- **UI**: Jetpack Compose with Material Design 3
- **Minimum SDK**: See constitution for version requirements
- **Accessibility**: TalkBack support with content descriptions
- **Touch targets**: Minimum 48dp (constitutional requirement)

### iOS Implementation

- **UI**: SwiftUI following iOS Design Guidelines
- **Minimum iOS version**: See constitution for version requirements
- **Accessibility**: VoiceOver support with accessibility labels
- **Touch targets**: Minimum 44pt (constitutional requirement)

### Mobile Testing Requirements

- **Shared logic tests**: Test in `commonTest`
- **Platform-specific tests**: Android (JUnit/Espresso), iOS (XCTest)
- **UI tests**: Compose UI Testing, SwiftUI Preview tests
- **Coverage**: ≥80% across all platforms (constitutional requirement)

### Mobile Accessibility Implementation

- Screen reader support (TalkBack on Android, VoiceOver on iOS)
- Color contrast validation for both light/dark themes
- Sufficient touch target sizes (48dp Android / 44pt iOS minimum)