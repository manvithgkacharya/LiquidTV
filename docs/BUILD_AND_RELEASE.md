# Build, Packaging & Release Guide

## Prerequisites

- **Java Development Kit**: JDK 17 (recommended: Eclipse Temurin 17 or Android Studio bundled JDK).
- **Android SDK**: Build Tools 35.0.0, SDK Platform 35.
- **Gradle**: 8.9 (configured via Gradle Wrapper).
- **Android Studio**: Ladybug (2024.2.1) or higher.

---

## Command Line Build

### 1. Build Debug APK
```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
Output artifact:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Build Release APK (Minified with R8)
```bash
# Windows
.\gradlew.bat assembleRelease

# macOS / Linux
./gradlew assembleRelease
```
Output artifact:
`app/build/outputs/apk/release/app-release-unsigned.apk`

---

## Installing on Android TV / Google TV

### Step 1: Enable Developer Options on TV
1. On your Android TV / Google TV, go to **Settings** → **System** → **About**.
2. Scroll to **Android TV OS Build** and click the OK button 7 times until you see "You are now a developer!".
3. Go back to **Settings** → **System** → **Developer Options**.
4. Enable **USB Debugging** and **Network Debugging**.

### Step 2: Connect via ADB
Find your TV's IP address in **Settings** → **Network & Internet** → Connected Wi-Fi or Ethernet.
```bash
adb connect <TV_IP_ADDRESS>:5555
```
A prompt will appear on your television asking to allow USB/Network debugging. Check **Always allow from this computer** and select **OK**.

### Step 3: Install APK
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Launching
LIQUID TV will now appear in your Android TV Leanback launcher row with its branded banner.
You can also launch it directly via ADB:
```bash
adb shell am start -n com.liquidtv.app.debug/com.liquidtv.app.MainActivity
```

