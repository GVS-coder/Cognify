# COGNIFY – BACKEND SECURITY STATUS

## Free-Tier Usage Limits
- **AI Chat Limits**: Maximum 20 successful AI chat requests per user, per day.
- **Document Analysis Limits**: Maximum 3 successful document analysis requests per user, per day.
- **Cooldown Interval**: Minimum 5 seconds between consecutive AI requests (regardless of type).

## Firestore Usage Structure
User usage is stored securely in Firestore and updated exclusively by the Cloud Functions environment (server-authoritative).
- **Collection Path**: `/users/{uid}/usage/{YYYY-MM-DD}`
- **Document Schema**:
  ```json
  {
      "aiChatCount": number,
      "documentAnalysisCount": number,
      "lastAiRequestAt": Timestamp,
      "updatedAt": Timestamp
  }
  ```

## Atomic Transaction Behavior
- **Data Integrity**: Usage counting is enforced using Firestore atomic transactions (`admin.firestore().runTransaction`).
- **Quota Processing Strategy**: A simplified, robust approach is used to prevent the quota inaccuracies that can arise from a crashing cloud function. During an AI request, the atomic transaction verifies that both the daily quota limits and the cooldown period are satisfied. Once verified, the relevant usage counters are incremented prior to executing the Gemini API call.
- **Failed Gemini Requests**: As a trade-off for transaction integrity and resilience against crashes, an accepted request consumes the user's quota immediately. If the backend subsequently fails to retrieve a successful response from Gemini, the consumed quota is *not* rolled back. This behavior prevents edge cases where a crashed function leaves the quota improperly consumed, guaranteeing that the usage count only ever moves strictly forward per user action.

## Timezone Strategy
- **Timezone**: UTC (Coordinated Universal Time)
- **Rationale**: The date segment `{YYYY-MM-DD}` is explicitly computed in UTC by the Cloud Function. This ensures consistent limit resets worldwide and prevents users from exploiting client-side clock manipulation to reset their quota prematurely.

## Error Handling & Obfuscation
- The server responds with generalized client-safe `HttpsError` objects (e.g. `resource-exhausted` or `invalid-argument`). Raw backend and Gemini API errors are captured exclusively in Google Cloud server logs and are never transmitted to the Android client.
