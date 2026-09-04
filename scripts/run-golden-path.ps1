[CmdletBinding()]
param(
    [string]$EnvFile,
    [string]$PythonContext,
    [switch]$KeepStack,
    [switch]$SkipBuild,
    [int]$WaitSeconds = 600
)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $PSScriptRoot
$artifactDir = Join-Path $projectDir "artifacts\golden-path"

if (-not $EnvFile) {
    $EnvFile = Join-Path $projectDir ".env.demo.example"
}
if (-not $PythonContext) {
    $PythonContext = @(
        (Join-Path (Split-Path -Parent $projectDir) "AI Clothing Shopping Assistant System"),
        (Join-Path (Split-Path -Parent $projectDir) "AI-Clothing-Shopping-Assistant-System")
    ) | Where-Object { Test-Path -LiteralPath (Join-Path $_ "Dockerfile") } | Select-Object -First 1
}
if (-not $PythonContext -or -not (Test-Path -LiteralPath (Join-Path $PythonContext "Dockerfile"))) {
    throw "Python AI Dockerfile was not found. Pass -PythonContext with the Python repository path."
}

$env:PYTHON_AI_CONTEXT = (Resolve-Path -LiteralPath $PythonContext).Path
$env:AI_RUNTIME_ENV = "integration"
$env:AI_DETERMINISTIC_PROVIDER = "true"
$randomHostPorts = @(
    "MYSQL_HOST_PORT",
    "REDIS_HOST_PORT",
    "RABBITMQ_AMQP_HOST_PORT",
    "RABBITMQ_MANAGEMENT_HOST_PORT",
    "RABBITMQ_PROMETHEUS_HOST_PORT",
    "LANGGRAPH_POSTGRES_HOST_PORT",
    "ELASTICSEARCH_HOST_PORT",
    "PYTHON_AI_HOST_PORT",
    "JAVA_BACKEND_HOST_PORT"
)
foreach ($name in $randomHostPorts) {
    if (-not [Environment]::GetEnvironmentVariable($name)) {
        [Environment]::SetEnvironmentVariable($name, "0")
    }
}
$composeArgs = @(
    "--env-file", $EnvFile,
    "-f", (Join-Path $projectDir "docker-compose.yml"),
    "-f", (Join-Path $projectDir "docker-compose.demo.yml")
)

function Invoke-Compose {
    & docker compose @composeArgs @args
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code $LASTEXITCODE"
    }
}

function Save-FailureEvidence {
    New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
    & docker compose @composeArgs ps --all *> (Join-Path $artifactDir "compose-ps.txt")
    $logs = (& docker compose @composeArgs logs --no-color 2>&1 | Out-String)
    $logs | python (Join-Path $PSScriptRoot "sanitize_logs.py") | Set-Content -Encoding utf8 (Join-Path $artifactDir "compose.log")
}

$succeeded = $false
try {
    if (Test-Path -LiteralPath $artifactDir) {
        Remove-Item -LiteralPath $artifactDir -Recurse -Force
    }
    Invoke-Compose down --volumes --remove-orphans
    Invoke-Compose config --quiet
    $upArgs = @("up", "-d")
    if (-not $SkipBuild) { $upArgs += "--build" }
    $upArgs += @("--wait", "--wait-timeout", $WaitSeconds)
    Invoke-Compose @upArgs

    Push-Location (Join-Path $projectDir "frontend")
    try {
        & npm run test:e2e:integration
        if ($LASTEXITCODE -ne 0) { throw "Playwright integration test failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
    $succeeded = $true
} finally {
    if (-not $succeeded) { Save-FailureEvidence }
    if (-not $KeepStack) {
        try { Invoke-Compose down --volumes --remove-orphans } catch { Write-Warning $_ }
    }
}
