param([string]$Udid = '', [string]$Server = 'http://127.0.0.1:4723')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & node "$PSScriptRoot/smoke.mjs" --server $Server --udid $Udid
    if ($LASTEXITCODE -ne 0) { throw "Smoke test failed (exit $LASTEXITCODE). See automation-tests/artifacts." }
} finally { Pop-Location }
