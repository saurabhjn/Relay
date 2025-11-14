# SMS Relay Android App

This is a simple but powerful Android application designed to relay incoming SMS messages from a device to a configurable API endpoint. It is built with modern Android development practices using Jetpack Compose for the UI and Ktor for networking.

## Features

- **Automatic Forwarding**: Automatically detects incoming SMS messages and forwards their content to a specified API.
- **Manual Sending Mode**: Provides a UI to manually load messages from the last 48 hours, select specific messages, and send them on demand.
- **Persistent History**: Keeps a local, persistent record of messages that have been successfully sent to prevent sending duplicates, even across app restarts and device reboots.
- **Configurable API Endpoints**: Easily switch between a "Production" and "Development" server URL directly from the UI.
- **Autostart on Boot**: The app can automatically start when the device boots up to ensure it is always active.
- **Modern UI**: A clean, functional user interface built with Jetpack Compose that includes:
  - A selectable list of recent messages with their status.
  - An auto-scrolling log for real-time activity monitoring.

## How to Use the App

The app's interface is designed for testing and control.

1.  **Server Selection**: 
    - Use the **Prod** and **Dev** buttons to select the target API server. The active server will be highlighted (filled in).
    - The **Test** button sends a pre-defined dummy message to the selected server, allowing you to quickly check API connectivity.

2.  **Manual Sending Workflow**:
    - **Load (Refresh Icon)**: Press this to load all SMS messages from the last 48 hours into the main list. The status of each message will be displayed: `(Read)`, `(Unread)`, or `(Sent)` if the app has already processed it.
    - **Select Messages**: Use the checkboxes to select one or more messages from the list.
    - **Send (Send Icon)**: Press this to send *only* the messages you have checked to the API.

3.  **Clear History (Delete Icon)**:
    - This is a testing utility. It completely erases the app's local memory of which messages have been sent. This allows you to re-send messages for testing purposes.

## Building and Installation

### Debug Build (for Development)

1.  Open the project in Android Studio.
2.  Connect an Android device with Developer Mode and USB Debugging enabled.
3.  Click the **Run 'app'** button (green triangle) in the toolbar.

### Release Build (Shareable APK)

To create a shareable `.apk` file that can be installed on other phones:

1.  In the Android Studio menu, go to **Build > Generate Signed Bundle / APK...**
2.  Select **APK** and click **Next**.
3.  If you don't have one, **create a new keystore**. This is a file that contains your digital signature. **Keep this file and its passwords safe!**
4.  Follow the on-screen instructions to configure the keystore and key alias.
5.  After creating the key, select the **release** build variant and click **Finish**.
6.  Android Studio will build the signed APK. When it's done, a notification will appear. Click the **locate** link to find the `app-release.apk` file in the `app/release` folder.
7.  You can copy this `.apk` file to another phone to install it. You may need to enable "Install from unknown sources" in the phone's security settings.
