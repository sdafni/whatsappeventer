# Google Calendar Integration Setup Guide

This guide explains how to set up Google Calendar API integration for the WhatsApp Eventer app.

## Prerequisites

1. **Google Cloud Console Account**: You need a Google account with access to the Google Cloud Console
2. **Android Studio**: For building and deploying the app
3. **WhatsApp Eventer App**: The base app should be working with overlay permissions

## Step 1: Google Cloud Console Setup

### 1.1 Create a New Project
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click "Select a project" → "New Project"
3. Enter project name: `whatsapp-eventer` (or your preferred name)
4. Click "Create"

### 1.2 Enable Google Calendar API
1. In your project, go to "APIs & Services" → "Library"
2. Search for "Google Calendar API"
3. Click on it and press "Enable"

### 1.3 Configure OAuth Consent Screen
1. Go to "APIs & Services" → "OAuth consent screen"
2. Choose "External" (unless you have a Google Workspace account)
3. Fill in required fields:
   - **App name**: WhatsApp Eventer
   - **User support email**: Your email
   - **Developer contact information**: Your email
4. Add scopes:
   - `auth/calendar` (to manage user's calendar events)
5. Save and continue through all steps

### 1.4 Create OAuth 2.0 Credentials
1. Go to "APIs & Services" → "Credentials"
2. Click "Create Credentials" → "OAuth 2.0 Client ID"
3. Choose "Android" as application type
4. Fill in:
   - **Name**: WhatsApp Eventer Android
   - **Package name**: `com.example.whatsappeventer`
   - **SHA-1 certificate fingerprint**: See section below

## Step 2: Get SHA-1 Certificate Fingerprint

### 2.1 For Debug Build (Development)
```bash
# Navigate to your project directory
cd /path/to/whatsappeventer

# Get debug SHA-1 fingerprint
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

### 2.2 For Release Build (Production)
```bash
# If you have a release keystore
keytool -list -v -keystore /path/to/your/release.keystore -alias your_alias_name
```

Copy the SHA-1 fingerprint and paste it into the Google Cloud Console credential configuration.

## Step 3: Update App Configuration

### 3.1 Update GoogleSignInManager.kt
Replace the placeholder client ID in `GoogleSignInManager.kt`:

```kotlin
private const val SERVER_CLIENT_ID = "YOUR_ACTUAL_CLIENT_ID_HERE"
```

You can find your client ID in the Google Cloud Console under "APIs & Services" → "Credentials".

### 3.2 Download google-services.json (Optional but Recommended)
1. In Google Cloud Console, go to "APIs & Services" → "Credentials"
2. Create additional credentials if needed for Firebase (optional)
3. Download the `google-services.json` file
4. Place it in `app/` directory of your Android project

## Step 4: Testing the Integration

### 4.1 Install and Test
1. Build and install the app: `./gradlew installDebug`
2. Grant all required permissions in the app:
   - Overlay permission
   - Usage stats permission  
   - Accessibility permission
3. Click "Sign in to Google Calendar" in the main app
4. Complete the OAuth flow
5. Open WhatsApp and start a conversation with calendar events
6. Click the overlay button to test event detection and calendar creation

### 4.2 Expected User Flow
1. **User clicks overlay button** → Event detection runs
2. **If not signed in** → Sign-in dialog appears → User signs in via app
3. **Calendar dialog opens** with pre-filled event details:
   - Title, description, date/time, location
4. **User reviews and edits** event details if needed
5. **User clicks "Add to Calendar"** → Event is created in Google Calendar
6. **Success/error toast** appears with feedback
7. **User returns to WhatsApp** → Overlay disappears

## Step 5: Production Considerations

### 5.1 App Verification
For production release:
1. Complete Google's app verification process
2. Provide privacy policy and terms of service
3. Explain why you need calendar permissions

### 5.2 Rate Limiting
- Google Calendar API has rate limits
- Implement proper error handling for quota exceeded errors
- Consider caching to reduce API calls

### 5.3 Security
- Store credentials securely
- Use proper OAuth 2.0 flows
- Never hardcode client secrets in the app

## Troubleshooting

### Common Issues:

**"Sign in failed" errors:**
- Check SHA-1 fingerprint matches exactly
- Verify package name is correct
- Ensure OAuth consent screen is configured

**"API not enabled" errors:**
- Confirm Google Calendar API is enabled in Cloud Console
- Check project is selected correctly

**Permission errors:**
- Verify calendar scope is requested: `CalendarScopes.CALENDAR`
- Check OAuth consent screen includes calendar permissions

**Network/SSL errors:**
- Ensure device has internet connection
- Check if corporate firewall blocks Google APIs

## Support

For additional help:
- [Google Calendar API Documentation](https://developers.google.com/calendar/api)
- [Android OAuth 2.0 Guide](https://developers.google.com/identity/protocols/oauth2/android-app)
- [Google Cloud Console Support](https://cloud.google.com/support)

## Notes

- The current implementation uses deprecated Google Sign-In APIs. Consider migrating to Credential Manager for production apps.
- Test thoroughly with different Google accounts and devices
- Monitor API usage in Google Cloud Console to stay within quotas