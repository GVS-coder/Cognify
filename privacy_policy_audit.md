# Cognify Privacy Policy Audit

## Production Data Types Found
- **Local Database (Room)**:
  - Tasks (`TaskEntity`)
  - Knowledge Vault notes (`KnowledgeEntity`)
  - Reminders (`ReminderEntity`)
  - Analytics Events (`AnalyticsEventEntity`)
  - AI Conversations & Messages (`ConversationEntity`, `ConversationMessageEntity`)
  - Calculator History (`CalculatorHistoryEntity`)
  - AI Daily Usage stats (`AiDailyUsageEntity`)
- **Cloud Database (Firebase Firestore)**:
  - Users (`users/{uid}`)
  - Tasks (`users/{uid}/tasks`)
  - Knowledge Vault (`users/{uid}/knowledgeVault`)
  - Reminders (`users/{uid}/reminders`)
- **Profile Information**:
  - Display Name, Email
  - Profile Photo URL (managed via Firebase Authentication `userProfileChangeRequest`, no Firebase Storage used)

## Third-Party Services Found
- **Firebase Authentication**: User identity and profile data.
- **Firebase Firestore**: Cloud synchronization of user data.
- **Cloudflare Worker**: Proxy/backend routing for AI requests (`/ai` and `/document` endpoints).
- **Google Gemini API**: AI Assistant text generation processing.
- **OCR.space**: Document optical character recognition processing.
- **NVIDIA AI**: Document analysis and processing.

## Firebase Data Found
- `users/{uid}` (Contains display name updates)
- `users/{uid}/tasks`
- `users/{uid}/knowledgeVault`
- `users/{uid}/reminders`

## Local Data Found
Data stored locally on the user's device via Android Room:
- Tasks, Knowledge Notes, Reminders.
- AI usage statistics (daily counts).
- AI Conversations and message history.
- Calculator calculation history.
- Analytics events.

## Account Deletion Behavior
- **Cloud Data**: Deletes the user's Firebase Authentication account, the root Firestore document (`users/{uid}`), and manually iterates and deletes documents within the `tasks`, `knowledgeVault`, and `reminders` subcollections to prevent orphaned data.
- **Security Constraint**: Deletion may throw `FirebaseAuthRecentLoginRequiredException`, meaning the user may be asked to log out and log back in before proceeding.
- **Local Data**: The application explicitly executes `AppDatabase.clearAllTables()` upon successful account deletion, ensuring all local data is immediately wiped from the device.

## Gmail / Smart Inbox
- **Explicitly Excluded**: The production build (`BuildConfig.GMAIL_FEATURE_ENABLED = false`) disables Gmail OAuth, Gmail.readonly scopes, Smart Inbox, and Email Analysis. These features are not documented in the production Privacy Policy.

## Items Requiring Manual Confirmation
- **Children's Privacy / COPPA**: Assumed age limit of 13. Please verify this matches the final Play Console target audience declaration.
- **Public Deletion URL**: The URL `[PUBLIC ACCOUNT DELETION URL]` must be replaced once Firebase Hosting is deployed.
