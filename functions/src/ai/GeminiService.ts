import { GoogleGenAI } from "@google/genai";
import { HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

const MODEL_NAME = "gemini-2.5-flash";
const SYSTEM_INSTRUCTION = "You are Cognify AI, a productivity and knowledge assistant. Provide concise and accurate responses to assist the user with tasks, knowledge, and study.";

export class GeminiService {
  /**
   * Securely generates a response using the Gemini API.
   * @param apiKey The GEMINI_API_KEY from Firebase Secrets
   * @param prompt The validated user prompt
   */
  static async generate(apiKey: string, prompt: string): Promise<string> {
    if (!apiKey) {
      logger.error("Gemini API key is missing or undefined.");
      throw new HttpsError("failed-precondition", "AI service is temporarily unavailable. Please try again.");
    }

    try {
      const ai = new GoogleGenAI({ apiKey });
      const response = await ai.models.generateContent({
        model: MODEL_NAME,
        contents: prompt,
        config: {
          systemInstruction: SYSTEM_INSTRUCTION
        }
      });
      
      if (!response || !response.text) {
        throw new Error("Empty response from Gemini");
      }

      return response.text;
    } catch (error: any) {
      logger.error("Gemini API Error", error.message || error);
      
      if (error?.status === 429) {
        throw new HttpsError("resource-exhausted", "AI quota exceeded. Please try again later.");
      }
      
      if (error?.status === 400) {
        throw new HttpsError("invalid-argument", "The request could not be processed by the AI.");
      }

      throw new HttpsError("internal", "AI service is temporarily unavailable. Please try again.");
    }
  }
}
