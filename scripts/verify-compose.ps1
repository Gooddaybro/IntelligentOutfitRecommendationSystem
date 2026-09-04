[CmdletBinding()]
param(
    [string]$EnvFile,
    [string]$PythonContext,
    [switch]$BuildImages
)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $PSScriptRoot

if (-not $EnvFile) {
    $localEnv = Join-Path $projectDir ".env"
    $EnvFile = if (Test-Path -LiteralPath $localEnv) {
        $localEnv
    } else {
        Join-Path $projectDir ".env.demo.example"
    }
}

if (-not $PythonContext) {
    $candidates = @(
        (Join-Path (Split-Path -Parent $projectDir) "AI Clothing Shopping Assistant System"),
        (Join-Path (Split-Path -Parent $projectDir) "AI-Clothing-Shopping-Assistant-System")
    )
    $PythonContext = $candidates |
        Where-Object { Test-Path -LiteralPath (Join-Path $_ "Dockerfile") } |
        Select-Object -First 1
}

if (-not $PythonContext -or -not (Test-Path -LiteralPath (Join-Path $PythonContext "Dockerfile"))) {
    throw "Python AI Dockerfile was not found. Pass -PythonContext with the Python repository path."
}

$composeFiles = @(
    "--env-file", $EnvFile,
    "-f", (Join-Path $projectDir "docker-compose.yml"),
    "-f", (Join-Path $projectDir "docker-compose.demo.yml")
)
$previousContext = $env:PYTHON_AI_CONTEXT

try {
    $env:PYTHON_AI_CONTEXT = (Resolve-Path -LiteralPath $PythonContext).Path
    & docker compose @composeFiles config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose config failed with exit code $LASTEXITCODE."
    }

    if ($BuildImages) {
        & docker compose @composeFiles build python-ai backend-web frontend
        if ($LASTEXITCODE -ne 0) {
            throw "Docker image build failed with exit code $LASTEXITCODE."
        }
    }
} finally {
    $env:PYTHON_AI_CONTEXT = $previousContext
}

Write-Host "Compose verification passed."
Write-Host "Python context: $PythonContext"
