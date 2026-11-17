import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { GeminiService } from "./ai/GeminiService";

// Initialize Firebase Admin safely
admin.initializeApp();

// Define the Gemini API key secret
const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");

const RATE_LIMIT_MS = 5000; // 5 seconds between requests
const AI_DAILY_LIMIT = 20;
const DOCUMENT_DAILY_LIMIT = 3;

/**
 * Basic health check to verify backend infrastructure is active.
 * Marked as unauthenticated for basic infrastructure validation.
 */
export const healthCheck = onCall(
  { enforceAppCheck: false }, // Placeholder config
  (request) => {
    return {
      success: true,
      service: "Cognify Backend"
    };
  }
);

function getUtcDateString(): string {
  const now = new Date();
  const year = now.getUTCFullYear();
  const month = String(now.getUTCMonth() + 1).padStart(2, "0");
  const day = String(now.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Secure AI Response generation using Gemini.
 * Expects { prompt: string, requestType?: string } and returns { response: string }.
 */
export const generateAiResponse = onCall(
  { 
    enforceAppCheck: false,
    maxInstances: 10,
    secrets: [GEMINI_API_KEY]
  },
  async (request) => {
    // 1. Authenticate caller using strictly the authenticated context
    if (!request.auth || !request.auth.uid) {
      logger.warn("Unauthenticated AI request attempted");
      throw new HttpsError("unauthenticated", "User must be authenticated to use the AI Assistant.");
    }

    const uid = request.auth.uid;

    // 2. Validate payload presence and requestType
    const data = request.data as any;
    if (!data || !data.prompt) {
      logger.warn(`Missing prompt from user: ${uid}`);
      throw new HttpsError("invalid-argument", "The 'prompt' field is required.");
    }

    const prompt = data.prompt;
    if (typeof prompt !== "string") {
      logger.warn(`Invalid prompt type from user: ${uid}`);
      throw new HttpsError("invalid-argument", "The 'prompt' field must be a string.");
    }
    
    if (prompt.trim() === "") {
      logger.warn(`Empty prompt from user: ${uid}`);
      throw new HttpsError("invalid-argument", "The prompt cannot be empty.");
    }

    if (prompt.length > 200000) {
      logger.warn(`Oversized prompt (${prompt.length} chars) from user: ${uid}`);
      throw new HttpsError("resource-exhausted", "The prompt exceeds the maximum allowed length.");
    }

    const requestType = data.requestType || "AI_CHAT";
    if (requestType !== "AI_CHAT" && requestType !== "DOCUMENT_ANALYSIS") {
      logger.warn(`Invalid requestType: ${requestType} from user: ${uid}`);
      throw new HttpsError("invalid-argument", "Invalid request type.");
    }

    // 3. Persistent Rate Limiting and Quota Enforcement
    const dateStr = getUtcDateString();
    const usageRef = admin.firestore().collection("users").doc(uid).collection("usage").doc(dateStr);
    const db = admin.firestore();

    await db.runTransaction(async (transaction) => {
      const doc = await transaction.get(usageRef);
      const now = admin.firestore.Timestamp.now();
      
      let aiChatCount = 0;
      let documentAnalysisCount = 0;
      let lastAiRequestAt = admin.firestore.Timestamp.fromMillis(0);

      if (doc.exists) {
        const docData = doc.data()!;
        aiChatCount = docData.aiChatCount || 0;
        documentAnalysisCount = docData.documentAnalysisCount || 0;
        lastAiRequestAt = docData.lastAiRequestAt || admin.firestore.Timestamp.fromMillis(0);
      }

      // Check cooldown
      const msSinceLastRequest = now.toMillis() - lastAiRequestAt.toMillis();
      if (msSinceLastRequest < RATE_LIMIT_MS) {
        logger.warn(`Rate limit exceeded for user: ${uid}`);
        throw new HttpsError("resource-exhausted", "You are sending requests too quickly. Please wait a moment.", { reason: "AI_COOLDOWN" });
      }

      // Check quota
      if (requestType === "AI_CHAT") {
        if (aiChatCount >= AI_DAILY_LIMIT) {
          logger.warn(`AI Chat daily limit reached for user: ${uid}`);
          throw new HttpsError("resource-exhausted", "Daily AI chat limit reached.", { reason: "AI_DAILY_LIMIT" });
        }
        aiChatCount++;
      } else if (requestType === "DOCUMENT_ANALYSIS") {
        if (documentAnalysisCount >= DOCUMENT_DAILY_LIMIT) {
          logger.warn(`Document Analysis daily limit reached for user: ${uid}`);
          throw new HttpsError("resource-exhausted", "Daily document analysis limit reached.", { reason: "DOCUMENT_DAILY_LIMIT" });
        }
        documentAnalysisCount++;
      }

      // Update quota in transaction
      transaction.set(usageRef, {
        aiChatCount,
        documentAnalysisCount,
        lastAiRequestAt: now,
        updatedAt: now
      }, { merge: true });
    });

    // 4. Connect to Gemini
    try {
      const apiKey = GEMINI_API_KEY.value();
      const aiResponse = await GeminiService.generate(apiKey, prompt);
      
      logger.info(`Successfully generated AI response for user: ${uid}`);
      
      return {
        response: aiResponse
      };
    } catch (error: any) {
      // Re-throw HttpsErrors intentionally thrown by GeminiService
      if (error instanceof HttpsError) {
        throw error;
      }
      
      logger.error(`Unexpected error processing AI request for user: ${uid}`, error);
      throw new HttpsError("internal", "An internal error occurred while processing the request.");
    }
  }
);

/**
 * Fetch AI usage for the current UTC day.
 */
export const getAiUsage = onCall(
  { enforceAppCheck: false },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "User must be authenticated.");
    }

    const uid = request.auth.uid;
    const dateStr = getUtcDateString();
    const doc = await admin.firestore().collection("users").doc(uid).collection("usage").doc(dateStr).get();

    if (!doc.exists) {
      return {
        aiChatUsed: 0,
        aiChatLimit: AI_DAILY_LIMIT,
        documentAnalysisUsed: 0,
        documentAnalysisLimit: DOCUMENT_DAILY_LIMIT
      };
    }

    const docData = doc.data()!;
    return {
      aiChatUsed: docData.aiChatCount || 0,
      aiChatLimit: AI_DAILY_LIMIT,
      documentAnalysisUsed: docData.documentAnalysisCount || 0,
      documentAnalysisLimit: DOCUMENT_DAILY_LIMIT
    };
  }
);
