$ErrorActionPreference = 'Continue'
$url = "http://127.0.0.1:8787/ai"

function Run-Test {
    param([string]$Name, [hashtable]$Params)
    Write-Host "`n--- TEST: $Name ---"
    try {
        $response = Invoke-RestMethod @Params -SkipHttpErrorCheck -Method Post
        Write-Host "Status: $($response.StatusCode ?? '200 OK (implicit)')"
        Write-Host "Response Body:"
        $response | ConvertTo-Json -Depth 5 -Compress | Write-Host
    } catch {
        Write-Host "Exception: $_"
    }
}

# TEST 1: Valid AI_CHAT request with "Hello"
Run-Test "TEST 1: Valid AI_CHAT" @{
    Uri = $url
    ContentType = 'application/json'
    Body = '{"prompt": "Hello", "requestType": "AI_CHAT"}'
}

# TEST 2: Valid DOCUMENT_ANALYSIS request
Run-Test "TEST 2: Valid DOCUMENT_ANALYSIS" @{
    Uri = $url
    ContentType = 'application/json'
    Body = '{"prompt": "Analyze this", "requestType": "DOCUMENT_ANALYSIS"}'
}

# TEST 3: Empty prompt
Run-Test "TEST 3: Empty prompt" @{
    Uri = $url
    ContentType = 'application/json'
    Body = '{"prompt": "", "requestType": "AI_CHAT"}'
}

# TEST 4: Invalid requestType
Run-Test "TEST 4: Invalid requestType" @{
    Uri = $url
    ContentType = 'application/json'
    Body = '{"prompt": "Hello", "requestType": "UNKNOWN"}'
}

# TEST 5: GET request to /ai
Write-Host "`n--- TEST 5: GET request to /ai ---"
try {
    $response = Invoke-RestMethod -Uri $url -Method Get -SkipHttpErrorCheck
    $response | ConvertTo-Json -Depth 5 -Compress | Write-Host
} catch {
    Write-Host "Exception: $_"
}

# TEST 7: Gemini API failure (Should happen automatically because of dummy key for Tests 1 and 2)
