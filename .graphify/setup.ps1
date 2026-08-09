param(
    [switch]$VerifyOnly
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ExpectedVersion = "0.9.32"
$CanonicalHashes = [ordered]@{
    ".agents/skills/graphify/SKILL.md" = "edf8e4bfd1fe644142a0af06495bf6a52a6b9c714c2d74f757fc784d1f53aa4f"
    ".agents/skills/graphify/references/update.md" = "e2eb607e8112206209974353c458f850dcbcaca86a1f2d94adc10e205f77607c"
    ".agents/skills/graphify/references/extraction-spec.md" = "0571ff1ee58ede5290816dbcbb8691e2fc811cbaa3732cc4f410fc1a4a5077b2"
    ".claude/skills/graphify/SKILL.md" = "8579a03227c91e9533b66e6322825b96bfad7d1e5ea12a2f02741153b73c4ee3"
    ".claude/skills/graphify/references/update.md" = "e2eb607e8112206209974353c458f850dcbcaca86a1f2d94adc10e205f77607c"
    ".claude/skills/graphify/references/extraction-spec.md" = "0571ff1ee58ede5290816dbcbb8691e2fc811cbaa3732cc4f410fc1a4a5077b2"
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $Command $($Arguments -join ' ')"
    }
}

# The recorded hashes are of the Git blob content (LF). This repo is checked out
# with core.autocrlf=true, so the working tree has CRLF and a raw Get-FileHash
# never matches — which used to abort setup before `graphify hook install` ever
# ran. Strip CR before LF so the guard checks content, not line endings, and
# stays valid on a Linux checkout too.
function Get-NormalizedHash {
    param([Parameter(Mandatory = $true)][string]$Path)

    $Bytes = [System.IO.File]::ReadAllBytes($Path)
    $Normalized = [System.Collections.Generic.List[byte]]::new($Bytes.Length)
    for ($i = 0; $i -lt $Bytes.Length; $i++) {
        if ($Bytes[$i] -eq 13 -and ($i + 1) -lt $Bytes.Length -and $Bytes[$i + 1] -eq 10) {
            continue
        }
        $Normalized.Add($Bytes[$i])
    }

    $Sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return (($Sha.ComputeHash($Normalized.ToArray()) | ForEach-Object { $_.ToString("x2") }) -join "")
    } finally {
        $Sha.Dispose()
    }
}

function Assert-CanonicalSkills {
    param([Parameter(Mandatory = $true)][string]$Root)

    foreach ($Entry in $CanonicalHashes.GetEnumerator()) {
        $Path = Join-Path $Root $Entry.Key
        if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
            throw "Missing canonical Graphify file: $($Entry.Key)"
        }
        $Actual = Get-NormalizedHash -Path $Path
        if ($Actual -ne $Entry.Value) {
            throw "Checksum mismatch for $($Entry.Key). Restore the repository version."
        }
    }
}

$Root = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or -not $Root) {
    throw "Run this script from inside the BlackICE Git repository."
}
$Root = (Resolve-Path -LiteralPath $Root).Path
Set-Location -LiteralPath $Root

if ($VerifyOnly) {
    Assert-CanonicalSkills -Root $Root
    Write-Host "Graphify project overrides verified for $ExpectedVersion."
    exit 0
}

$CanonicalPaths = @($CanonicalHashes.Keys)
$Dirty = & git status --porcelain -- @CanonicalPaths
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect canonical Graphify files."
}
if ($Dirty) {
    throw "Canonical Graphify files have local changes. Commit or stash them before setup."
}

$Uv = (Get-Command uv -ErrorAction Stop).Source
$GraphifyCommand = Get-Command graphify -ErrorAction SilentlyContinue
$InstalledVersion = if ($GraphifyCommand) {
    ((& $GraphifyCommand.Source --version) -join "").Trim()
} else {
    ""
}
if ($InstalledVersion -ne "graphify $ExpectedVersion") {
    Invoke-Checked $Uv tool install --force "graphifyy==$ExpectedVersion"
}

$ToolBin = ((& $Uv tool dir --bin) -join "").Trim()
$Graphify = Join-Path $ToolBin "graphify.exe"
if (-not (Test-Path -LiteralPath $Graphify -PathType Leaf)) {
    $Graphify = Join-Path $ToolBin "graphify"
}
if (-not (Test-Path -LiteralPath $Graphify -PathType Leaf)) {
    throw "graphify executable was not found in uv's tool bin directory."
}

Invoke-Checked $Graphify install --project
Invoke-Checked $Graphify install --project --platform agents
Invoke-Checked $Graphify claude install --project
Invoke-Checked $Graphify codex install --project

# The installer replaces SKILL.md and references/. Restore the reviewed,
# versioned BlackICE copies immediately, then prove that the overlay is intact.
# Verified for the 0.9.28 -> 0.9.32 bump: the stock skill files are byte-identical
# across the two releases (only .graphify_version changes), so the overlay applies
# unchanged and $CanonicalHashes did not need recomputing.
Invoke-Checked git restore --source=HEAD -- @CanonicalPaths
Assert-CanonicalSkills -Root $Root

# These hooks contain machine-local executable paths and are intentionally
# ignored by Git. Recreate them on every workstation.
Invoke-Checked $Graphify hook install

$GitDir = (& git rev-parse --git-dir).Trim()
$GitCommonDir = (& git rev-parse --git-common-dir).Trim()
if ($GitDir -ne $GitCommonDir) {
    Write-Warning "Linked worktree detected. Graphify $ExpectedVersion Git hooks still skip linked worktrees (hooks.py compares --git-dir with --git-common-dir); run the project skill with --update before every task commit."
}

Write-Host "Graphify $ExpectedVersion installed; project overrides and local hooks verified."
