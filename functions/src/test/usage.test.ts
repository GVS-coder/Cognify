
import * as fft from "firebase-functions-test";
import { HttpsError } from "firebase-functions/v2/https";
import { GeminiService } from "../ai/GeminiService";

const testEnv = fft();

// Mock Firestore state
let firestoreData: Record<string, any> = {};

jest.mock("firebase-admin", () => {
  const mockTransaction = {
    get: jest.fn(async (ref: any) => {
      const data = firestoreData[ref.path];
      return {
        exists: !!data,
        data: () => data,
      };
    }),
    set: jest.fn((ref: any, data: any, options: any) => {
      firestoreData[ref.path] = data;
    }),
  };

  const createRef = (path: string) => ({
    path,
    collection: (subPath: string) => createRef(`${path}/${subPath}`),
    doc: (subPath: string) => createRef(`${path}/${subPath}`),
    get: jest.fn(async () => {
      const data = firestoreData[path];
      return {
        exists: !!data,
        data: () => data,
      };
    }),
  });

  const firestoreMock = jest.fn(() => ({
    collection: (path: string) => createRef(path),
    runTransaction: jest.fn(async (cb) => cb(mockTransaction)),
  }));

  Object.assign(firestoreMock, {
    Timestamp: {
      now: jest.fn(() => {
        const current = Date.now();
        return { toMillis: () => current };
      }),
      fromMillis: jest.fn((m) => ({ toMillis: () => m })),
    },
  });

  return {
    initializeApp: jest.fn(),
    firestore: firestoreMock,
  };
});

jest.mock("firebase-functions/params", () => ({
  defineSecret: jest.fn(() => ({ value: () => "fake_key" })),
}));


import { generateAiResponse, getAiUsage } from "../index";

describe("generateAiResponse Usage Limits", () => {
  let wrapped: any;
  const uid = "test_user_123";

  beforeEach(() => {
    firestoreData = {};
    jest.spyOn(GeminiService, "generate").mockImplementation(async () => "Mocked AI Response");
    wrapped = testEnv.wrap(generateAiResponse);
    firestoreData = {}; // reset db
    jest.clearAllMocks();
    jest.useFakeTimers();
    jest.setSystemTime(new Date("2026-08-26T12:00:00Z").getTime());
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  function getDocPath() {
    return `users/${uid}/usage/2026-08-26`;
  }

  it("1. First AI request succeeds", async () => {
    const result = await wrapped({ data: { prompt: "Hi" }, auth: { uid } });
    expect(result.response).toBe("Mocked AI Response");
    
    const doc = firestoreData[getDocPath()];
    expect(doc.aiChatCount).toBe(1);
  });

  it("2. Cooldown blocks rapid requests", async () => {
    await wrapped({ data: { prompt: "Hi" }, auth: { uid } });
    
    // Attempting immediately should fail
    try {
      await wrapped({ data: { prompt: "Hi" }, auth: { uid } });
      fail("Expected error");
    } catch (e: any) {
      expect(e.details.reason).toBe("AI_COOLDOWN");
    }
  });

  it("3. 20 AI requests maximum per day", async () => {
    for (let i = 0; i < 20; i++) {
      await wrapped({ data: { prompt: "Hi" }, auth: { uid } });
      jest.advanceTimersByTime(5001); // bypass cooldown
    }
    const doc = firestoreData[getDocPath()];
    expect(doc.aiChatCount).toBe(20);
  });

  it("4. 21st AI request is rejected", async () => {
    for (let i = 0; i < 20; i++) {
      await wrapped({ data: { prompt: "Hi" }, auth: { uid } });
      jest.advanceTimersByTime(5001); // bypass cooldown
    }
    try {
      await wrapped({ data: { prompt: "Hi" }, auth: { uid } });
      fail("Expected error");
    } catch (e: any) {
      expect(e.details.reason).toBe("AI_DAILY_LIMIT");
    }
  });

  it("5. 3 document-analysis requests maximum per day", async () => {
    for (let i = 0; i < 3; i++) {
      await wrapped({ data: { prompt: "Doc", requestType: "DOCUMENT_ANALYSIS" }, auth: { uid } });
      jest.advanceTimersByTime(5001); // bypass cooldown
    }
    const doc = firestoreData[getDocPath()];
    expect(doc.documentAnalysisCount).toBe(3);
  });

  it("6. 4th document-analysis request is rejected", async () => {
    for (let i = 0; i < 3; i++) {
      await wrapped({ data: { prompt: "Doc", requestType: "DOCUMENT_ANALYSIS" }, auth: { uid } });
      jest.advanceTimersByTime(5001);
    }
    try {
      await wrapped({ data: { prompt: "Doc", requestType: "DOCUMENT_ANALYSIS" }, auth: { uid } });
      fail("Expected error");
    } catch (e: any) {
      expect(e.details.reason).toBe("DOCUMENT_DAILY_LIMIT");
    }
  });

  it("7. Missing requestType defaults to AI_CHAT", async () => {
    await wrapped({ data: { prompt: "Hi" }, auth: { uid } });
    const doc = firestoreData[getDocPath()];
    expect(doc.aiChatCount).toBe(1);
    expect(doc.documentAnalysisCount).toBe(0);
  });

  it("8. Invalid requestType is rejected", async () => {
    await expect(wrapped({ data: { prompt: "Hi", requestType: "INVALID" }, auth: { uid } }))
      .rejects.toThrow(HttpsError);
  });

  it("9. Unauthenticated requests are rejected", async () => {
    await expect(wrapped({ data: { prompt: "Hi" } }))
      .rejects.toThrow(HttpsError);
  });

  it("10. Failed Gemini request behavior (quota consumed)", async () => {
    jest.spyOn(GeminiService, "generate").mockRejectedValueOnce(new Error("Gemini failure"));
    
    await expect(wrapped({ data: { prompt: "Hi" }, auth: { uid } }))
      .rejects.toThrow(HttpsError);
      
    // The quota should still be consumed as per the simplified atomic reservation design
    const doc = firestoreData[getDocPath()];
    expect(doc.aiChatCount).toBe(1);
  });
});

describe("getAiUsage tests", () => {
  let wrappedUsage: any;
  const uid = "test_user_123";

  beforeEach(() => {
    wrappedUsage = testEnv.wrap(getAiUsage);
    firestoreData = {}; // reset db
    jest.clearAllMocks();
    jest.useFakeTimers();
    jest.setSystemTime(new Date("2026-08-26T12:00:00Z").getTime());
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  function getDocPath() {
    return `users/${uid}/usage/2026-08-26`;
  }

  it("11. Returns 0 values if no usage doc exists", async () => {
    const res = await wrappedUsage({ auth: { uid } });
    expect(res.aiChatUsed).toBe(0);
    expect(res.aiChatLimit).toBe(20);
    expect(res.documentAnalysisUsed).toBe(0);
    expect(res.documentAnalysisLimit).toBe(3);
  });

  it("12. Returns correct values if usage doc exists", async () => {
    firestoreData[getDocPath()] = {
      aiChatCount: 15,
      documentAnalysisCount: 2
    };
    const res = await wrappedUsage({ auth: { uid } });
    expect(res.aiChatUsed).toBe(15);
    expect(res.documentAnalysisUsed).toBe(2);
  });

  it("13. Rejects unauthenticated", async () => {
    await expect(wrappedUsage({})).rejects.toThrow(HttpsError);
  });
});
