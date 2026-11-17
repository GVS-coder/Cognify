@echo off
echo TEST 3: Empty prompt
curl -s -X POST http://127.0.0.1:8787/ai -H "Content-Type: application/json" -d "{\"prompt\": \"\", \"requestType\": \"AI_CHAT\"}"
echo.
echo TEST 4: Invalid requestType
curl -s -X POST http://127.0.0.1:8787/ai -H "Content-Type: application/json" -d "{\"prompt\": \"Hello\", \"requestType\": \"UNKNOWN\"}"
echo.
echo TEST 5: GET request
curl -s -X GET http://127.0.0.1:8787/ai
echo.
