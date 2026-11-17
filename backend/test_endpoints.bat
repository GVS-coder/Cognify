@echo off
set BASE_URL=https://backend.cognify-app-2026.workers.dev

echo --- LIGHTNING ---
curl.exe -s -w "\nHTTP_STATUS:%%{http_code}\n" -X POST "%BASE_URL%/internal/test-nvidia" -H "Content-Type: application/json" -d "{\"action\": \"lightning\"}"

echo.
echo --- ULTRA ---
curl.exe -s -w "\nHTTP_STATUS:%%{http_code}\n" -X POST "%BASE_URL%/internal/test-nvidia" -H "Content-Type: application/json" -d "{\"action\": \"ultra\"}"

echo.
echo --- KIMI ---
curl.exe -s -w "\nHTTP_STATUS:%%{http_code}\n" -X POST "%BASE_URL%/internal/test-nvidia" -H "Content-Type: application/json" -d "{\"action\": \"kimi\"}"

echo.
echo --- OCR ---
curl.exe -s -w "\nHTTP_STATUS:%%{http_code}\n" -X POST "%BASE_URL%/internal/test-nvidia" -H "Content-Type: application/json" -d "{\"action\": \"ocr\"}"

echo.
echo --- INVALID KEY/MODEL ---
curl.exe -s -w "\nHTTP_STATUS:%%{http_code}\n" -X POST "%BASE_URL%/internal/test-nvidia" -H "Content-Type: application/json" -d "{\"action\": \"unknown\"}"

echo.
echo --- AI (GEMINI) ---
curl.exe -s -w "\nHTTP_STATUS:%%{http_code}\n" -X POST "%BASE_URL%/ai" -H "Content-Type: application/json" -d "{\"prompt\": \"Say test\", \"requestType\": \"AI_CHAT\"}"
