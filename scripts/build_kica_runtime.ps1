#Requires -Version 5.1
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$jdk = (Get-Command jlink.exe -ErrorAction Stop).Source | Split-Path -Parent | Split-Path -Parent
$output = Join-Path (Split-Path -Parent $PSScriptRoot) 'kica-package\runtime'
if (Test-Path -LiteralPath $output) { throw "Output already exists; refusing to overwrite: $output" }
$modules = 'java.base,java.desktop,java.prefs,java.sql,java.net.http,jdk.net,jdk.security.auth,jdk.charsets,jdk.crypto.ec'
Write-Host "JDK: $jdk"
Write-Host "Runtime: $output"
& "$jdk\bin\jlink.exe" --module-path "$jdk\jmods" --add-modules $modules --strip-debug --no-header-files --no-man-pages --compress=2 --output $output
if ($LASTEXITCODE -ne 0) { throw "jlink failed: $LASTEXITCODE" }
& "$output\bin\java.exe" -version
if ($LASTEXITCODE -ne 0) { throw "Runtime validation failed: $LASTEXITCODE" }
& "$output\bin\java.exe" --list-modules
if ($LASTEXITCODE -ne 0) { throw "Module validation failed: $LASTEXITCODE" }
$files = Get-ChildItem -LiteralPath $output -Recurse -File
Write-Host ('Runtime size: {0:N2} MiB ({1} files)' -f (($files | Measure-Object Length -Sum).Sum / 1MB), $files.Count)
