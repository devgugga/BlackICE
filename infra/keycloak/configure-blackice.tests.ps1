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
@(
  'QUARKUS_OIDC_SECRET=contract-secret'
  'APP_HOST=contract-host'
) | Set-Content -LiteralPath $envFile

try {
  & "$PSScriptRoot/configure-blackice.ps1" -EnvFile $envFile

  Assert-True ($global:capturedArguments -contains 'compose') 'invoca docker compose'
  Assert-True ($global:capturedArguments -contains 'infra/compose.yml') 'inclui compose base'
  Assert-True ($global:capturedArguments -contains 'infra/dcm4chee/compose.yml') 'inclui compose do archive'
  Assert-True ($global:capturedArguments -contains 'infra/compose.apps.yml') 'inclui compose das aplicações'
  Assert-True ($global:capturedArguments -contains 'QUARKUS_OIDC_SECRET=contract-secret') 'encaminha segredo ao container'
  Assert-True ($global:capturedStdin -match 'blackice-quarkus') 'envia núcleo de configuração ao container'
}
finally {
  Remove-Item -LiteralPath $envFile -Force -ErrorAction SilentlyContinue
  Remove-Item function:global:docker -ErrorAction SilentlyContinue
  Remove-Variable capturedArguments -Scope Global -ErrorAction SilentlyContinue
  Remove-Variable capturedStdin -Scope Global -ErrorAction SilentlyContinue
}
