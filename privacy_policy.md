# Privacy Policy

**Last Updated: October 8, 2026**

## 1. Introduction

Welcome to Cognify. This Privacy Policy explains how Cognify ("we", "our", or "us") collects, uses, stores, and protects your information when you use our mobile application (the "App"). We are committed to protecting your privacy and ensuring you understand exactly how your data is handled.

## 2. Developer Information

If you have any questions or concerns about this Privacy Policy or your data, please contact us at:
**Email:** krctstu@gmail.com

## 3. Information We Collect

### 3.1. Account Information
To use features requiring synchronization, you must create an account. When you register via Firebase Authentication, we collect:
* Your email address
* A display name (if provided)
* A profile photo (if provided)

### 3.2. Profile Information and Profile Photo
You may choose to upload a profile photo and set a display name. Your display name and profile photo reference (URL) are securely stored and managed exclusively through Firebase Authentication. Cognify does not use Firebase Storage or any other cloud file storage system to host image files.

### 3.3. User-Created Content
When you use Cognify, you may generate data through the App's features. This includes:
* **Tasks:** Your to-do lists and project tracking.
* **Knowledge Vault:** Notes and saved knowledge items.
* **Reminders:** Scheduled alarms and alerts.
* **Calculator History:** Your past calculations.
* **Conversations:** Chat histories with the AI Assistant.

### 3.4. AI Usage Information
Cognify stores daily AI usage counts locally on your device to enforce the application's AI usage limits.

## 4. How We Store Your Data

### 4.1. Local Device Storage
By default, Cognify stores your user-created content securely on your device using a local database. This includes tasks, knowledge vault notes, reminders, analytics events, calculator history, AI conversation history, and daily AI usage statistics.

### 4.2. Cloud Synchronization (Firebase)
If you sign in and enable synchronization, your data is securely backed up to Google Firebase Firestore. We store the following information in cloud collections tied specifically to your unique user ID:
* Your Tasks
* Your Knowledge Vault items
* Your Reminders

We only synchronize the data necessary to provide a seamless experience across your devices. You may choose to use local functionality without cloud synchronization where the implementation supports this.

## 5. Third-Party AI Data Processing

When you use Cognify's AI-powered features, relevant content you provide may be transmitted through Cognify's backend infrastructure to third-party AI service providers for processing and generation of the requested result. The handling of this content is subject to the applicable provider's terms and privacy practices.

### 5.1. Cloudflare Worker Processing
Our application communicates with AI service providers through a secure backend hosted on Cloudflare Workers. Cloudflare securely routes your requests to the appropriate AI engine and returns the results to your device.

### 5.2. AI Assistant (Google Gemini API)
When you interact with the AI Assistant, the text prompts and conversational context you provide are securely transmitted via our Cloudflare Worker to the Google Gemini API to generate responses.

### 5.3. Document Analysis
If you choose to submit a document for AI analysis, the document data follows a specific pipeline:
1. The document is securely transmitted from your device to our Cloudflare Worker.
2. The document is processed by **OCR.space** to extract text via Optical Character Recognition (OCR).
3. The extracted text is then transmitted to **NVIDIA AI** for content analysis, summarization, or other AI-driven insights.

## 6. Third-Party Service Providers

In addition to the AI processing providers listed above, we utilize the following third-party services to operate the App:
* **Google Firebase Authentication:** For secure user sign-in and profile management.
* **Google Firebase Firestore:** For secure cloud synchronization of your tasks, notes, and reminders.

## 7. Data Security

We implement reasonable technical and organizational measures to protect your information against unauthorized access, loss, or alteration. Data transmitted between your device, our Cloudflare Workers, Firebase, and our third-party AI providers is encrypted in transit using industry-standard HTTPS/TLS protocols. However, please note that no internet transmission or electronic storage system can be guaranteed to be 100% secure.

## 8. Data Retention

We retain your local data on your device for as long as the App is installed. For synchronized cloud data, we retain your information in our Firebase Firestore database as long as your account remains active.

## 9. Account Deletion

You have the right to request the deletion of your account and associated data. You can delete your account directly within the App's settings or by visiting our public deletion page at:
https://cognify-8ac56.web.app/delete-account.html

When you delete your account:
* **Cloud Data:** We permanently delete your Firebase Authentication account, your root user document, and all synchronized items within your `tasks`, `knowledgeVault`, and `reminders` cloud collections.
* **Security:** For your security, deleting your account may require a recent login. If prompted, you will need to log out and log back in before the deletion process can complete.
* **Local Data:** When account deletion successfully completes, Cognify automatically clears all local data from the device database, including tasks, reminders, calculator history, and AI conversations.

## 10. Children's Privacy

Cognify is not intended for use by children under the age of 13. We do not knowingly collect personal information from children under 13. If you believe we have inadvertently collected such information, please contact us at krctstu@gmail.com so we can take steps to delete it.

## 11. Changes to this Privacy Policy

We may update this Privacy Policy from time to time. If we make significant changes, we will notify you by updating the "Last Updated" date at the top of this policy and, if applicable, providing an in-app notice. We encourage you to review this Privacy Policy periodically to stay informed about how we protect your information.

## 12. Contact Information

If you have any questions, concerns, or requests regarding this Privacy Policy or your personal data, please contact us:
**Email:** krctstu@gmail.com
