export interface Env {
	GEMINI_API_KEY: string;
	OCR_SPACE_API_KEY: string;
	NVIDIA_NEMOTRON_OCR_KEY?: string;
	NVIDIA_NEMOTRON_LIGHTNING_KEY?: string;
	NVIDIA_NEMOTRON_ULTRA_KEY?: string;
	NVIDIA_KIMI_K3_KEY?: string;
}

export interface AIProviderResponse {
	success: boolean;
	provider: string;
	model: string;
	content?: string;
	error?: string;
	statusCode: number;
}

export class NvidiaAIService {
	private env: Env;
	private baseUrl = 'https://integrate.api.nvidia.com/v1/chat/completions';

	constructor(env: Env) {
		this.env = env;
	}

	private getApiKeyForModel(model: string): string | undefined {
		switch (model) {
			case 'nvidia/nemotron-ocr-v2':
				return this.env.NVIDIA_NEMOTRON_OCR_KEY;
			case 'nvidia/nemotron-3.5-lightning-30b-a3b':
				return this.env.NVIDIA_NEMOTRON_LIGHTNING_KEY;
			case 'nvidia/nemotron-3-ultra-550b-a55b':
				return this.env.NVIDIA_NEMOTRON_ULTRA_KEY;
			case 'moonshotai/kimi-k3':
				return this.env.NVIDIA_KIMI_K3_KEY;
			default:
				return undefined;
		}
	}

	async callModel(model: string, messages: any[], maxTokens = 1024, signal?: AbortSignal): Promise<AIProviderResponse> {
		const startTime = Date.now();
		const apiKey = this.getApiKeyForModel(model);

		if (!apiKey) {
			return {
				success: false,
				provider: 'nvidia',
				model: model,
				error: `Server configuration error: Missing API key for ${model}`,
				statusCode: 500,
			};
		}

		try {
			const response = await fetch(this.baseUrl, {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
					'Authorization': `Bearer ${apiKey}`,
				},
				body: JSON.stringify({
					model: model,
					messages: messages,
					max_tokens: maxTokens,
				}),
				signal: signal,
			});
			
			if (!response.ok) {
				return {
					success: false,
					provider: 'nvidia',
					model: model,
					error: `API error: ${response.status}`,
					statusCode: response.status,
				};
			}

			const data: any = await response.json();
			const content = data?.choices?.[0]?.message?.content;
			
			if (typeof content !== 'string' || content.trim() === '') {
				return {
					success: false,
					provider: 'nvidia',
					model: model,
					error: 'Malformed response',
					statusCode: 502,
				};
			}

			return {
				success: true,
				provider: 'nvidia',
				model: model,
				content: content,
				statusCode: 200,
			};
		} catch (error: any) {
			if (error.name === 'AbortError' || (error.message && error.message.includes('abort'))) {
				throw error;
			}
			return {
				success: false,
				provider: 'nvidia',
				model: model,
				error: 'Network failure or timeout',
				statusCode: 500,
			};
		}
	}
}

const CORS_HEADERS = {
	'Access-Control-Allow-Origin': '*',
	'Access-Control-Allow-Methods': 'POST, OPTIONS',
	'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

export default {
	async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
		if (request.method === 'OPTIONS') {
			return new Response(null, { headers: CORS_HEADERS });
		}

		const url = new URL(request.url);

		if (url.pathname === '/document') {
			const requestId = 'doc-' + Math.random().toString(36).substring(2, 10);
			let currentStage = 'REQUEST_RECEIVED';
            const startTime = Date.now();

            const safeLog = (stage: string, extra: any = {}) => {
                currentStage = stage;
                const durationMs = Date.now() - startTime;
                console.log(`requestId=${requestId} stage=${stage} durationMs=${durationMs} ${Object.entries(extra).map(([k,v])=>`${k}=${v}`).join(' ')}`.trim());
            };
            
			const jsonError = (message: string, code: string, status: number = 400) => {
                safeLog('ERROR', { code, status });
				return new Response(JSON.stringify({ 
                    success: false, 
                    error: message,
                    code: code,
                    stage: currentStage,
                    statusCode: status,
                    requestId: requestId
                }), {
					status,
					headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
				});
			};

            safeLog(currentStage);

			if (request.method !== 'POST') {
				return jsonError('Method Not Allowed', 'INVALID_REQUEST', 405);
			}

			if (!env.OCR_SPACE_API_KEY) {
				return jsonError('Internal server configuration error.', 'INTERNAL_ERROR', 500);
			}
			if (!env.NVIDIA_NEMOTRON_ULTRA_KEY) {
				return jsonError('Internal server configuration error.', 'INTERNAL_ERROR', 500);
			}

			let formData: FormData;
			try {
				formData = await request.formData();
			} catch (e) {
				return jsonError('Invalid form data provided.', 'INVALID_REQUEST');
			}

			const file = formData.get('file');
			const action = formData.get('action') || 'Summarize';

			if (!file || !(file instanceof File)) {
				return jsonError('Valid file is required.', 'INVALID_REQUEST');
			}

			if (file.type !== 'application/pdf') {
				return jsonError('Only PDF files are supported.', 'INVALID_PDF');
			}
            
			if (file.size > 5 * 1024 * 1024) {
				return jsonError('The document is too large for the current free analysis limit.', 'FILE_TOO_LARGE', 413);
			}
            
            safeLog('PDF_VALIDATED', { size: file.size });

			// Call OCR.space
			const ocrFormData = new FormData();
			ocrFormData.append('file', file, file.name || 'document.pdf');
			ocrFormData.append('OCREngine', '2');
			ocrFormData.append('scale', 'true');
			ocrFormData.append('isTable', 'true');
			ocrFormData.append('filetype', 'PDF');

            safeLog('OCR_STARTED');

			try {
				const ocrResponse = await fetch('https://api.ocr.space/parse/image', {
					method: 'POST',
					headers: {
						'apikey': env.OCR_SPACE_API_KEY,
					},
					body: ocrFormData,
				});

				if (!ocrResponse.ok) {
                    const text = await ocrResponse.text().catch(() => '');
					return jsonError(`OCR API Error: ${ocrResponse.status} ${text}`, 'OCR_REQUEST_FAILED', 502);
				}

				const ocrData: any = await ocrResponse.json();
                
                safeLog('OCR_COMPLETED', { ocrStatus: ocrResponse.status });

				if (ocrData.IsErroredOnProcessing || ocrData.ErrorMessage) {
					return jsonError(`OCR Processing Error: ${ocrData.ErrorMessage || 'Unknown Error'}`, 'OCR_REQUEST_FAILED', 502);
				}

				if (!ocrData?.ParsedResults || !Array.isArray(ocrData.ParsedResults)) {
					return jsonError('Unable to extract readable text from this document.', 'OCR_EMPTY_RESULT');
				}

				let extractedText = ocrData.ParsedResults
					.map((r: any) => r.ParsedText)
					.filter((t: any) => typeof t === 'string')
					.join('\n')
					.trim();

				if (!extractedText) {
					return jsonError('Unable to extract readable text from this document.', 'OCR_EMPTY_RESULT');
				}
                
                safeLog('TEXT_EXTRACTED', { chars: extractedText.length });
                
                // Chunking implementation
                const MAX_CHARS_PER_CHUNK = 8000;
                let finalContext = extractedText;
                
                const nvidiaService = new NvidiaAIService(env);
                
                if (extractedText.length > MAX_CHARS_PER_CHUNK) {
                    safeLog('CHUNKING_STARTED');
                    const chunks = [];
                    for (let i = 0; i < extractedText.length; i += MAX_CHARS_PER_CHUNK) {
                        chunks.push(extractedText.substring(i, i + MAX_CHARS_PER_CHUNK));
                    }
                    
                    const chunkSummaries = [];
                    for (let i = 0; i < chunks.length; i++) {
                        const chunkPrompt = `You are analyzing part ${i + 1} of a document.\n\nUser request:\n"${action}"\n\nDocument section:\n${chunks[i]}\n\nProduce the important facts from this section relevant to the user request. Do not invent information.`;
                        
                        const controller = new AbortController();
                        const timeoutId = setTimeout(() => controller.abort(), 30000);
                        
                        try {
                            const modelResponse = await nvidiaService.callModel(
                                'nvidia/nemotron-3-ultra-550b-a55b',
                                [{ role: 'user', content: chunkPrompt }],
                                512,
                                controller.signal
                            );
                            clearTimeout(timeoutId);
                            
                            if (modelResponse.success && modelResponse.content) {
                                chunkSummaries.push(`Section ${i + 1}:\n${modelResponse.content}`);
                            }
                        } catch (e) {
                            clearTimeout(timeoutId);
                            // Best effort for chunks
                        }
                    }
                    finalContext = chunkSummaries.join('\n\n');
                    safeLog('CHUNKING_COMPLETED', { chunks: chunks.length });
                }

                safeLog('TEXT_VALIDATED');
                safeLog('NVIDIA_REQUEST_STARTED');

				const prompt = finalContext !== extractedText 
                    ? `You are given extracted information from multiple sections of a document.\n\nUser request:\n${action}\n\nSection analyses:\n${finalContext}\n\nProvide the final answer to the user's request.\nUse only the supplied document information. Do not invent facts.`
                    : `DOCUMENT CONTENT:\n${finalContext}\n\nUSER REQUEST:\n${action}\n\nProvide a useful response based only on the supplied document content. Do not invent facts.`;

				const controller = new AbortController();
				const timeoutId = setTimeout(() => controller.abort(), 45000);

				try {
					const modelResponse = await nvidiaService.callModel(
						'nvidia/nemotron-3-ultra-550b-a55b',
						[{ role: 'user', content: prompt }],
						1024,
						controller.signal
					);
					clearTimeout(timeoutId);
                    
                    safeLog('NVIDIA_RESPONSE_RECEIVED', { status: modelResponse.statusCode });

					if (!modelResponse.success) {
                        let code = 'NVIDIA_UPSTREAM_ERROR';
                        if (modelResponse.statusCode === 401 || modelResponse.statusCode === 403) code = 'NVIDIA_AUTH_ERROR';
                        if (modelResponse.statusCode === 429) code = 'NVIDIA_RATE_LIMIT';
                        
						if (modelResponse.statusCode === 429) {
							return jsonError('The document analysis service is temporarily busy. Please try again.', code, 429);
						}
						const safeStatus = [400, 401, 403, 404, 429, 500, 502, 503, 504].includes(modelResponse.statusCode) ? modelResponse.statusCode : 502;
						return jsonError('Document AI analysis failed.', code, safeStatus);
					}

                    safeLog('RESPONSE_PARSED');
                    safeLog('SUCCESS');
					return new Response(JSON.stringify({ 
						success: true, 
						response: modelResponse.content
					}), {
						status: 200,
						headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
					});
				} catch (error: any) {
					clearTimeout(timeoutId);
					if (error.name === 'AbortError' || (error.message && error.message.includes('abort'))) {
						return jsonError('Document AI analysis timed out. Please try again.', 'NVIDIA_TIMEOUT', 504);
					}
					return jsonError('Document AI analysis failed.', 'NVIDIA_UPSTREAM_ERROR', 502);
				}
			} catch (error) {
				return jsonError('The document analysis service is temporarily unavailable.', 'DOCUMENT_ANALYSIS_FAILED', 502);
			}
		}

		if (url.pathname === '/ai') {
            const CORS_HEADERS = {
                'Access-Control-Allow-Origin': '*',
                'Access-Control-Allow-Methods': 'POST, OPTIONS',
                'Access-Control-Allow-Headers': 'Content-Type, Authorization',
            };
            
            function jsonErrorGemini(message: string, status: number = 400) {
                return new Response(JSON.stringify({ success: false, error: message }), {
                    status,
                    headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
                });
            }

			if (request.method !== 'POST') {
				return jsonErrorGemini('Method Not Allowed', 405);
			}

			if (!env.GEMINI_API_KEY) {
				return jsonErrorGemini('Internal server configuration error.', 500);
			}

			let body: any;
			try {
				body = await request.json();
			} catch (e) {
				return jsonErrorGemini('Invalid JSON payload provided.');
			}

			if (!body || typeof body !== 'object') {
				return jsonErrorGemini('Invalid request format.');
			}

			const prompt = body.prompt;
			if (typeof prompt !== 'string' || prompt.trim() === '') {
				return jsonErrorGemini('The prompt cannot be empty.');
			}

			const requestType = body.requestType;
			if (requestType !== 'AI_CHAT' && requestType !== 'DOCUMENT_ANALYSIS' && requestType !== 'EMAIL_ANALYSIS') {
				return jsonErrorGemini('Unsupported request type.');
			}

			async function callGemini(modelName: string): Promise<Response> {
				const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${env.GEMINI_API_KEY}`;
				return await fetch(geminiUrl, {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }] }),
				});
			}

			function createSafeErrorResponse(status: number, errData: any): Response {
				let errorCode = 'GEMINI_UPSTREAM_ERROR';
				let errorMsg = 'The AI service is temporarily unavailable. Please try again later.';
				let debugGeminiMessage = errData?.error?.message || '';
				
				if (status === 400) {
					errorMsg = 'Could not process the request. The data may be invalid.';
					errorCode = 'GEMINI_BAD_REQUEST';
				} else if (status === 401 || status === 403) {
					errorMsg = 'The AI service is temporarily unavailable. Please try again later.';
					errorCode = 'GEMINI_AUTH_ERROR';
				} else if (status === 404) {
					errorMsg = 'The AI service configuration is invalid (model not found). Please try again later.';
					errorCode = 'GEMINI_MODEL_NOT_FOUND';
				} else if (status === 429) {
					errorMsg = 'AI usage limit reached. Please try again later.';
					errorCode = 'GEMINI_RATE_LIMIT';
				} else if (status >= 500) {
					errorMsg = 'The AI service is temporarily unavailable. Please try again later.';
					errorCode = 'GEMINI_SERVER_ERROR';
				}
				
				console.log(`[Gemini Error] status=${status} code=${errorCode} message=${debugGeminiMessage}`);
				
				return new Response(JSON.stringify({ 
					success: false, 
					error: errorMsg, 
					code: errorCode,
					geminiStatus: status,
					geminiError: debugGeminiMessage
				}), {
					status: 502,
					headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
				});
			}

			try {
				let finalResponse = await callGemini('gemini-3.6-flash');
				let finalStatus = finalResponse.status;
				let finalModel = 'gemini-3.6-flash';
				
				const forcePrimary = request.headers.get('x-force-primary');
				if (forcePrimary) {
					finalStatus = parseInt(forcePrimary);
					finalResponse = new Response("Forced Primary", {status: finalStatus});
				}
				
				console.log(`PRIMARY_MODEL=gemini-3.6-flash PRIMARY_STATUS=${finalStatus}`);

				if (finalStatus === 503 || finalStatus === 429) {
					let fallback1Response = await callGemini('gemini-3.7-flash');
					let fallback1Status = fallback1Response.status;
					
					const forceFallback1 = request.headers.get('x-force-fallback-1');
					if (forceFallback1) {
						fallback1Status = parseInt(forceFallback1);
						fallback1Response = new Response("Forced Fallback 1", {status: fallback1Status});
					}
					
					console.log(`FALLBACK_1_MODEL=gemini-3.7-flash FALLBACK_1_STATUS=${fallback1Status}`);
					
					if (fallback1Response.ok) {
						finalResponse = fallback1Response;
						finalStatus = fallback1Status;
						finalModel = 'gemini-3.7-flash';
					} else if (fallback1Status === 503 || fallback1Status === 429) {
						let fallback2Response = await callGemini('gemini-3.5-flash-lite');
						let fallback2Status = fallback2Response.status;
						
						const forceFallback2 = request.headers.get('x-force-fallback-2');
						if (forceFallback2) {
							fallback2Status = parseInt(forceFallback2);
							fallback2Response = new Response("Forced Fallback 2", {status: fallback2Status});
						}
						
						console.log(`FALLBACK_2_MODEL=gemini-3.5-flash-lite FALLBACK_2_STATUS=${fallback2Status}`);
						
						finalResponse = fallback2Response;
						finalStatus = fallback2Status;
						finalModel = 'gemini-3.5-flash-lite';
					} else {
						finalResponse = fallback1Response;
						finalStatus = fallback1Status;
						finalModel = 'gemini-3.7-flash';
					}
				}

				console.log(`FINAL_MODEL=${finalModel} FINAL_STATUS=${finalStatus}`);

				if (!finalResponse.ok) {
					let errData: any = {};
					try {
						errData = await finalResponse.json();
					} catch (e) {
						// ignore
					}
					return createSafeErrorResponse(finalStatus, errData);
				}

				const geminiData: any = await finalResponse.json();
				
				if (!geminiData?.candidates?.[0]?.content?.parts?.[0]) {
					console.log('[Gemini Error] Malformed response received.');
					return new Response(JSON.stringify({ success: false, error: 'Could not process the AI analysis response.', code: 'GEMINI_MALFORMED_RESPONSE' }), {
						status: 502,
						headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
					});
				}

				const aiText = geminiData.candidates[0].content.parts[0].text;

				return new Response(JSON.stringify({ success: true, response: aiText }), {
					status: 200,
					headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
				});
			} catch (error) {
				return jsonErrorGemini('An unexpected error occurred while communicating with the AI service.', 500);
			}
		}

		if (url.pathname === '/list-models') {
			try {
				const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${env.GEMINI_API_KEY}`);
				const data = await response.json();
				return new Response(JSON.stringify(data), {
					status: 200,
					headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
				});
			} catch (e) {
				return new Response('Error', { status: 500 });
			}
		}

		if (url.pathname === '/test-model') {
			try {
				let body: any = await request.json();
				const testModel = body.model || 'gemini-3.8-flash';
				const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${testModel}:generateContent?key=${env.GEMINI_API_KEY}`;
				const geminiResponse = await fetch(geminiUrl, {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({ contents: [{ parts: [{ text: body.prompt || 'Say test' }] }] }),
				});
				const data = await geminiResponse.json();
				return new Response(JSON.stringify({ status: geminiResponse.status, data }), {
					status: 200,
					headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
				});
			} catch (e) {
				return new Response('Error', { status: 500 });
			}
		}

		return new Response(JSON.stringify({ success: false, error: 'Not Found' }), {
            status: 404,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });
	},
};
