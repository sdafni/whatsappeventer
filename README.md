# WhatsApp Eventer

An Android app that displays an overlay button over other apps when WhatsApp is in the foreground.

## Features

- **Overlay Button**: A floating button that appears over other apps
- **WhatsApp Detection**: Automatically shows/hides the button based on WhatsApp usage
- **Draggable**: The overlay button can be dragged to different positions
- **Clickable**: The button responds to taps with custom actions
- **Persistent**: Continues running even when the button is clicked

## How It Works

### Core Mechanism

The app uses a combination of:
1. **Overlay Permission** (`SYSTEM_ALERT_WINDOW`) - Allows drawing over other apps
2. **Usage Stats Permission** (`PACKAGE_USAGE_STATS`) - Monitors which app is in foreground
3. **Foreground Service** - Keeps the overlay running continuously
4. **UsageStatsManager** - Detects when WhatsApp becomes active

### Technical Implementation

1. **MainActivity**: Handles permissions and controls the overlay service
2. **OverlayService**: Manages the floating button and monitors WhatsApp usage
3. **Permission Management**: Guides users through granting necessary permissions
4. **Continuous Monitoring**: Checks every second if WhatsApp is in foreground

### Why This Approach?

- **No Accessibility Required**: Unlike accessibility services, this doesn't require complex user setup
- **Persistent**: The button continues showing even after being clicked
- **Efficient**: Only shows when needed (WhatsApp active)
- **User-Friendly**: Simple permission grants, no complex settings

## Permissions Required

1. **Overlay Permission**: `SYSTEM_ALERT_WINDOW`
   - Allows the app to draw over other apps
   - User must manually enable in Settings > Apps > Special app access > Display over other apps

2. **Usage Stats Permission**: `PACKAGE_USAGE_STATS`
   - Allows monitoring which app is currently active
   - User must manually enable in Settings > Apps > Special app access > Usage access

3. **Foreground Service**: `FOREGROUND_SERVICE`
   - Allows the service to run continuously
   - Automatically granted

## Usage

1. **Install the app**
2. **Grant Overlay Permission**: Tap "Grant Overlay Permission" and follow system prompts
3. **Grant Usage Stats Permission**: Tap "Grant Usage Stats Permission" and follow system prompts
4. **Start Overlay**: Tap "Start Overlay" to begin monitoring
5. **Use WhatsApp**: The overlay button will automatically appear when WhatsApp is active
6. **Stop Overlay**: Tap "Stop Overlay" to stop the service

## Customization

The overlay button can be customized by modifying:
- `overlay_button.xml` - Button layout and appearance
- `overlay_button_background.xml` - Button background style
- `ic_whatsapp.xml` - Button icon
- `OverlayService.kt` - Button behavior and positioning

## Technical Details

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin
- **Architecture**: Service-based with foreground notification

## Security Considerations

- The app only monitors app usage, doesn't access app content
- Overlay permission is required for the floating button functionality
- All permissions are clearly explained to the user
- The service runs with minimal privileges

## Troubleshooting

- **Button not showing**: Check if overlay permission is granted
- **Button not appearing over WhatsApp**: Check if usage stats permission is granted
- **Service stops unexpectedly**: Ensure the app is not being killed by battery optimization
- **Button position resets**: This is normal behavior for security reasons

## Future Enhancements

- Custom button actions
- Multiple button positions
- Different trigger apps
- Button appearance customization
- Action logging and analytics 