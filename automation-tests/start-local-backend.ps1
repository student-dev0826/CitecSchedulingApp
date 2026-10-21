param(
    [string]$MySqlServer = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe',
    [switch]$SeedAccounts
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $PSScriptRoot '.runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$php = Join-Path $runtime 'php\php.exe'
if (!(Test-Path -LiteralPath $php)) {
    $releases = Invoke-RestMethod 'https://windows.php.net/downloads/releases/releases.json'
    $archive = $releases.'8.4'.'nts-vs17-x64'.zip
    $zip = Join-Path $runtime 'php.zip'
    Invoke-WebRequest ('https://windows.php.net/downloads/releases/' + $archive.path) -OutFile $zip
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $archive.sha256) { throw 'PHP checksum mismatch.' }
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $runtime 'php') -Force
}
if (!(Test-Path -LiteralPath $MySqlServer)) { throw 'Specify the installed MySQL 8 server executable with -MySqlServer.' }
$dataDir = Join-Path $runtime 'mysql-data'
if (!(Test-Path -LiteralPath $dataDir)) {
    & $MySqlServer --no-defaults --initialize-insecure "--datadir=$dataDir" --console
    if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL initialization failed.' }
}
function Ensure-LocalProcess($Name, $Executable, $Arguments, $Port, $ExpectedArgument) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    if ($listener) {
        $existing = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener[0].OwningProcess)"
        if ($existing.ExecutablePath -ne $Executable -or !$existing.CommandLine.Contains($ExpectedArgument)) {
            throw "Port $Port belongs to another process. It will not be changed."
        }
        return
    }
    $process = Start-Process -FilePath $Executable -ArgumentList $Arguments -WorkingDirectory $project -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runtime "$Name.stdout.log") -RedirectStandardError (Join-Path $runtime "$Name.stderr.log")
    $process.Id | Set-Content (Join-Path $runtime "$Name.pid")
    for ($i = 0; $i -lt 50; $i++) {
        if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { return }
        if ($process.HasExited) { throw "$Name exited. See .runtime/$Name.stderr.log." }
        Start-Sleep -Milliseconds 200
    }
    throw "$Name did not start within 10 seconds. See its log."
}
Ensure-LocalProcess 'mysql' $MySqlServer @('--no-defaults', "--datadir=$dataDir", '--port=3307', '--bind-address=127.0.0.1', '--mysqlx=0', '--console') 3307 $dataDir
$savedEnvironment = @{}
foreach ($name in @('DB_HOST', 'DB_PORT', 'DB_NAME')) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:DB_HOST = '127.0.0.1'; $env:DB_PORT = '3307'; $env:DB_NAME = 'citec_recurring_test'
    if ($SeedAccounts) {
        if (!$env:TEST_PASSWORD) { throw 'Set TEST_PASSWORD before using -SeedAccounts.' }
        & $php -d "extension_dir=$runtime\php\ext" -d extension=pdo_mysql -d extension=mbstring "$PSScriptRoot\seed-recurring.php"
        if ($LASTEXITCODE -ne 0) { throw 'Could not seed the isolated test database.' }
    }
    Ensure-LocalProcess 'php' $php @('-d', "extension_dir=$runtime\php\ext", '-d', 'extension=pdo_mysql', '-d', 'extension=mbstring', '-S', '127.0.0.1:8087', '-t', "$project\backend") 8087 "$project\backend"
} finally {
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
}
Write-Host 'Isolated backend is running at http://127.0.0.1:8087/api/ (MySQL 3307).'
