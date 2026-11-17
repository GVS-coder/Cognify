# Cognify Backend Setup Instructions

## Architecture
- **Environment**: Firebase Cloud Functions 2nd Generation
- **Runtime**: Node.js 20
- **Language**: TypeScript
- **Target Project**: cognify-8ac56

## Gemini Integration
- **Gemini SDK**: `@google/genai`
- **Gemini Model**: `gemini-2.5-flash`

## Infrastructure Commands

### Install Dependencies
To install the necessary Node.js packages:
```bash
cd functions
npm install
```

### Build & Typecheck
To compile TypeScript into standard JavaScript:
```bash
npm run build
```

### Local Testing with Emulator
You can test the Cloud Functions locally prior to deploying them to Firebase:
```bash
npm run serve
```
*Note: Make sure the Java Runtime Environment is installed if Firebase requires it for the Emulator Suite.*

## Security Restrictions
- **Authentication**: `generateAiResponse` strictly requires a valid Firebase Auth context (`request.auth.uid`). Passing a user-supplied ID in the request data is explicitly rejected.
- **Payload Constraints**: Requests must contain a `prompt` string and will be strictly rejected if they exceed predefined length bounds (200,000 characters maximum).
- **Rate Limiting Limitation**: Currently, a non-persistent in-memory rate limiter is implemented. This is a temporary safeguard. **Production rate limiting/quota enforcement must be persistent and per-user.**
- **Response Contract**: The Cloud Function receives `{ prompt: string }` and returns `{ response: string }`.

## Secret Management (Phase 3 Requirement)
The backend does NOT currently contain the Gemini API key. When Phase 3 begins (Deployment), you must securely configure the key using the Firebase Secrets mechanism:
```bash
firebase functions:secrets:set GEMINI_API_KEY
```
Do **not** hardcode secrets in source code files.

## Error Handling
Raw Gemini errors are mapped to safe, client-friendly error messages (e.g. `HttpsError("internal")`, `HttpsError("resource-exhausted")`). Stack traces, API keys, and internal server paths are never returned to the Android client.
