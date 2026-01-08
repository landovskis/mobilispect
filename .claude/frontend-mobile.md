# Frontend Mobile Configuration

## Technology Stack

- **Shared Logic**: Kotlin Multiplatform Mobile (KMM)
- **Android UI**: Jetpack Compose with Material Design 3
- **iOS UI**: SwiftUI with iOS Design Guidelines

## Pre-Commit Hooks

Mobile-specific hooks enforced via `.pre-commit-config.yaml`:

### Android
- **ktlint**: Kotlin code formatting
- **Android lint**: Platform-specific linting
- **Unit tests**: JUnit tests must pass
- **Coverage validation**: ≥80% threshold

### iOS
- **SwiftFormat**: Code formatting
- **SwiftLint**: Linting and code quality
- **XCTest**: Unit tests execution
- **Coverage validation**: ≥80% threshold

## IDE Integration

### Android Studio
- Pre-commit plugin configuration
- Enable ktlint and Android lint plugins
- Compose preview support

### Xcode
- Build phases for SwiftLint/SwiftFormat integration
- Enable Swift package manager support
- Configure code coverage settings

## Mobile-Specific Requirements

### Kotlin Multiplatform Mobile (KMM)

- Shared business logic in `commonMain`
- Platform-specific implementations in `androidMain` and `iosMain`
- Expect/actual declarations for platform APIs
- Minimize platform-specific code

### Android Requirements

- **UI**: Jetpack Compose with Material Design 3
- **Minimum SDK**: Define in constitution
- **Theme parity**: Light/dark mode support
- **Performance**: 60fps UI rendering
- **Accessibility**: TalkBack support, content descriptions

### iOS Requirements

- **UI**: SwiftUI following iOS Design Guidelines
- **Minimum iOS version**: Define in constitution
- **Theme parity**: Light/dark mode support
- **Performance**: 60fps UI rendering
- **Accessibility**: VoiceOver support, accessibility labels

### Testing Requirements

- **Shared logic tests**: Test in `commonTest`
- **Platform-specific tests**: Android (JUnit/Espresso), iOS (XCTest)
- **UI tests**: Compose UI Testing, SwiftUI Preview tests
- **≥80% coverage** across all platforms

### Accessibility

- WCAG 2.1 AA compliance on mobile
- Screen reader support (TalkBack, VoiceOver)
- Sufficient touch target sizes (48dp/44pt minimum)
- Color contrast requirements
- Light/dark theme parity