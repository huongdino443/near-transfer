$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$project = Join-Path $root "src\NearTransfer.Windows\NearTransfer.Windows.csproj"
$publishPath = Join-Path $root "publish\legacy-anycpu"
$installerScript = Join-Path $PSScriptRoot "NearTransfer.iss"
$releasePath = Join-Path $root "release"

New-Item -ItemType Directory -Path $publishPath -Force | Out-Null
New-Item -ItemType Directory -Path $releasePath -Force | Out-Null

dotnet publish $project `
    --configuration Release `
    --output $publishPath `
    -p:Platform=AnyCPU `
    -p:EnableWindowsTargeting=true `
    -p:DebugType=None `
    -p:DebugSymbols=false
if ($LASTEXITCODE -ne 0) {
    throw "dotnet publish failed with exit code $LASTEXITCODE"
}

$compiler = Get-Command "ISCC.exe" -ErrorAction SilentlyContinue
if ($null -eq $compiler) {
    $programFilesX86 = [Environment]::GetFolderPath("ProgramFilesX86")
    $programFiles = [Environment]::GetFolderPath("ProgramFiles")
    $candidates = @(
        (Join-Path $programFilesX86 "Inno Setup 6\ISCC.exe"),
        (Join-Path $programFiles "Inno Setup 6\ISCC.exe")
    )
    $compilerPath = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($null -eq $compilerPath) {
        throw "The Windows app was published to $publishPath, but Inno Setup 6 was not found. Install it and rerun this script."
    }
} else {
    $compilerPath = $compiler.Source
}

$publishArgument = "/DPublishDir=`"$publishPath`""
& $compilerPath $publishArgument $installerScript
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup failed with exit code $LASTEXITCODE"
}

Write-Host "Installer created in $releasePath"