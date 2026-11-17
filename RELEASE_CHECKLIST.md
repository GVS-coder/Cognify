# Release Checklist

Ensure the following tasks are manually verified on a physical device or emulator before finalizing the submission:

## 1. Authentication
- [ ] Sign up with a new email and password.
- [ ] Log out and log back in.
- [ ] Test anonymous login fallback.

## 2. Core Modules
- [ ] **Dashboard**: Verify greeting and summary lists populate correctly.
- [ ] **Tasks**: Create a task with high priority. Complete it. Delete it.
- [ ] **Knowledge Vault**: Create a new note. Edit it.
- [ ] **Calendar**: Verify tasks show up on their respective due dates.

## 3. Advanced Features
- [ ] **AI Assistant**: Send a message and ensure the Gemini response is contextually relevant.
- [ ] **Reminders**: Schedule a reminder and wait for the local notification to trigger.
- [ ] **Analytics**: Verify stats update after completing a task.
- [ ] **Export**: Generate a PDF/CSV preview and verify it contains real data.
- [ ] **Backup**: Force a manual sync and verify Firestore collections update.

## 4. Settings & Profile
- [ ] Update display name and profile picture.
- [ ] Toggle Dark Theme / Dynamic Colors.
- [ ] **Danger Zone**: Test account deletion and ensure user is returned to the login screen with data cleared.

## 5. Offline Capabilities
- [ ] Disconnect from WiFi/Data.
- [ ] Create a task.
- [ ] Reconnect to WiFi/Data and verify background sync to Firestore.
