# Contributing to LIQUID TV

Thank you for your interest in contributing to LIQUID TV! 🎉

## How Can I Contribute?

### Reporting Bugs
- Use GitHub Issues to report bugs.
- Include device model, Android TV OS version, and logcat output if available.
- Describe steps to reproduce the bug clearly.

### Suggesting Enhancements
- Open a feature request on GitHub Issues.
- Explain why this feature would be useful for TV users.

### Submitting Pull Requests
1. Fork the repository.
2. Create a new branch (`git checkout -b feature/my-new-feature`).
3. Ensure the project builds cleanly:
   ```bash
   ./gradlew assembleDebug
   ```
4. Test on an Android TV device or emulator with a D-pad remote.
5. Commit your changes with descriptive commit messages.
6. Push to your branch and submit a Pull Request against `main`.

## Code Style & Guidelines
- Follow official Kotlin coding conventions.
- Maintain Clean Architecture separation (data, domain, presentation).
- All new UI components must support 100% remote D-pad focus states.
