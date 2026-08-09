$ErrorActionPreference = 'Stop'

function Assert-True {
  param(
    [bool]$Condition,
    [string]$Message
  )

  if (-not $Condition) {
    throw "Assertion failed: $Message"
  }
}

$global:capturedArguments = @()
$global:capturedStdin = ''

function global:docker {
  $global:capturedArguments = @($args)
  $global:capturedStdin = @($input) -join [Environment]::NewLine
}

$envFile = Join-Path $env:TEMP 'blackice-keycloak-contract.env'
$temporaryCwd = Join-Path $env:TEMP "blackice-keycloak-contract-cwd-$PID"
$originalLocation = Get-Location
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\\..')).Path
@(
  'QUARKUS_OIDC_SECRET=contract-secret'
  'APP_HOST=contract-host'
) | Set-Content -LiteralPath $envFile

try {
  New-Item -ItemType Directory -Path $temporaryCwd -Force | Out-Null
  Set-Location -LiteralPath $temporaryCwd
  & "$PSScriptRoot/configure-blackice.ps1" -EnvFile $envFile

  Assert-True ($global:capturedArguments -contains 'compose') 'invoca docker compose'
  Assert-True ($global:capturedArguments -contains (Join-Path $root 'infra/compose.yml')) 'ancora compose base no repositório'
  Assert-True ($global:capturedArguments -contains (Join-Path $root 'infra/dcm4chee/compose.yml')) 'ancora compose do archive no repositório'
  Assert-True ($global:capturedArguments -contains (Join-Path $root 'infra/compose.apps.yml')) 'ancora compose das aplicações no repositório'
  Assert-True ($global:capturedArguments -contains 'QUARKUS_OIDC_SECRET=contract-secret') 'encaminha segredo ao container'
  Assert-True ($global:capturedStdin -match 'blackice-quarkus') 'envia núcleo de configuração ao container'
}
finally {
  Set-Location -LiteralPath $originalLocation
  Remove-Item -LiteralPath $envFile -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $temporaryCwd -Force -ErrorAction SilentlyContinue
  Remove-Item function:global:docker -ErrorAction SilentlyContinue
  Remove-Variable capturedArguments -Scope Global -ErrorAction SilentlyContinue
  Remove-Variable capturedStdin -Scope Global -ErrorAction SilentlyContinue
}
