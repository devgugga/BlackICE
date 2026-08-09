[CmdletBinding()]
param(
  [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\\..')).Path

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
  $EnvFile = Join-Path $root 'infra/.env'
}

if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
  throw 'Arquivo de ambiente não encontrado.'
}

$environment = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile) {
  $trimmed = $line.Trim()
  if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
    continue
  }

  $parts = $trimmed -split '=', 2
  if ($parts.Count -ne 2 -or $parts[0].Trim().Length -eq 0) {
    throw 'Linha de ambiente inválida.'
  }

  $environment[$parts[0].Trim()] = $parts[1]
}

foreach ($required in 'QUARKUS_OIDC_SECRET', 'APP_HOST') {
  if ([string]::IsNullOrWhiteSpace($environment[$required])) {
    throw "Variável de ambiente obrigatória ausente: $required"
  }
}

$containerScript = Join-Path $PSScriptRoot 'configure-blackice-container.sh'
if (-not (Test-Path -LiteralPath $containerScript -PathType Leaf)) {
  throw 'Núcleo de configuração não encontrado.'
}

Get-Content -LiteralPath $containerScript -Raw |
  & docker compose `
    -f (Join-Path $root 'infra/compose.yml') `
    -f (Join-Path $root 'infra/dcm4chee/compose.yml') `
    -f (Join-Path $root 'infra/compose.apps.yml') `
    exec -T `
    -e "QUARKUS_OIDC_SECRET=$($environment['QUARKUS_OIDC_SECRET'])" `
    -e "APP_ORIGIN=http://$($environment['APP_HOST'])" `
    -e 'ARC_CLIENT=dcm4chee-arc-rs' `
    -e 'REALM=blackice' `
    keycloak sh -s

if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
