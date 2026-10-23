[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)]
    [int]$Port = 5433
)

$ErrorActionPreference = 'Stop'
$previousEnvironment = @{}
$environmentNames = @(
    'OPTISERVE_DB_URL',
    'OPTISERVE_DB_USERNAME',
    'OPTISERVE_DB_PASSWORD',
    'OPTISERVE_JWT_SECRET',
    'JAVA_TOOL_OPTIONS'
)

foreach ($name in $environmentNames) {
    $item = Get-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    $previousEnvironment[$name] = @{
        Exists = $null -ne $item
        Value = if ($null -ne $item) { $item.Value } else { $null }
    }
}

$containerId = $null
$environmentFile = $null
$exitCode = 1
$runId = [Guid]::NewGuid().ToString('N')
$containerName = "optiserve-test-$runId"
$database = 'optiserve_test'
$username = 'optiserve_test'

function New-RandomHex([int]$ByteCount) {
    $bytes = New-Object byte[] $ByteCount
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

try {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($null -eq $docker) {
        throw 'Docker CLI was not found. Install Docker Desktop and make sure it is on PATH.'
    }

    & $docker.Source info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker is installed but the Docker daemon is unavailable. Start Docker Desktop and retry.'
    }

    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
        $listener.Start()
    }
    catch [System.Net.Sockets.SocketException] {
        throw "Local TCP port $Port is already in use. Stop the process using it or retry with -Port <free-port>. No container was started."
    }
    finally {
        if ($null -ne $listener) {
            $listener.Stop()
        }
    }

    $databasePassword = New-RandomHex 24
    $jwtSecret = New-RandomHex 32
    $environmentFile = Join-Path ([System.IO.Path]::GetTempPath()) "optiserve-test-$runId.env"
    @(
        "POSTGRES_DB=$database",
        "POSTGRES_USER=$username",
        "POSTGRES_PASSWORD=$databasePassword"
    ) | Set-Content -LiteralPath $environmentFile -Encoding ascii

    $createdId = & $docker.Source run --detach `
        --name $containerName `
        --label 'optiserve.local-test-runner=true' `
        --label "optiserve.run-id=$runId" `
        --publish "127.0.0.1:${Port}:5432" `
        --env-file $environmentFile `
        postgres:16
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($createdId | Out-String).Trim())) {
        throw 'Docker could not create the dedicated PostgreSQL 16 test container. Check Docker Desktop and image-pull access.'
    }
    $containerId = ($createdId | Out-String).Trim()

    Remove-Item -LiteralPath $environmentFile -Force -ErrorAction SilentlyContinue
    $environmentFile = $null

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & $docker.Source exec $containerId pg_isready -U $username -d $database *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        throw 'The dedicated PostgreSQL container did not become ready within 60 seconds.'
    }

    $env:OPTISERVE_DB_URL = "jdbc:postgresql://localhost:$Port/$database"
    $env:OPTISERVE_DB_USERNAME = $username
    $env:OPTISERVE_DB_PASSWORD = $databasePassword
    $env:OPTISERVE_JWT_SECRET = $jwtSecret
    $otherJavaOptions = [string]$previousEnvironment['JAVA_TOOL_OPTIONS'].Value
    $otherJavaOptions = $otherJavaOptions -replace '(?:^|\s)-Duser\.timezone=\S+', ''
    $env:JAVA_TOOL_OPTIONS = (($otherJavaOptions.Trim() + ' -Duser.timezone=Asia/Kolkata').Trim())

    Push-Location $PSScriptRoot\..
    try {
        & .\mvnw.cmd -B clean verify
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}
catch {
    Write-Error $_
    $exitCode = 1
}
finally {
    if ($null -ne $containerId) {
        & docker rm --force $containerId *> $null
    }
    if ($null -ne $environmentFile) {
        Remove-Item -LiteralPath $environmentFile -Force -ErrorAction SilentlyContinue
    }
    foreach ($name in $environmentNames) {
        if ($previousEnvironment[$name].Exists) {
            Set-Item -Path "Env:$name" -Value $previousEnvironment[$name].Value
        }
        else {
            Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
        }
    }
}

exit $exitCode
