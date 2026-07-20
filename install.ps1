# SecAI Installation Script for Windows
# Usage: irm https://raw.githubusercontent.com/<YOUR_GITHUB_USERNAME>/secai/main/install.ps1 | iex

$ErrorActionPreference = "Stop"

$Repo = "SecAI-Cam/SecAI"
$BinaryName = "secai.exe"
$InstallDir = "$env:USERPROFILE\secai\bin"

Write-Host "Fetching latest release for SecAI..."

# Fetch latest release from GitHub
$LatestReleaseUrl = "https://api.github.com/repos/$Repo/releases/latest"
try {
    $ReleaseData = Invoke-RestMethod -Uri $LatestReleaseUrl -UseBasicParsing
    $LatestTag = $ReleaseData.tag_name
} catch {
    Write-Error "Failed to fetch latest release data from GitHub."
    exit 1
}

$DownloadUrl = "https://github.com/$Repo/releases/download/$LatestTag/secai-windows-amd64.exe"

Write-Host "Downloading SecAI $LatestTag for Windows..."
if (-not (Test-Path -Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}

$DestPath = Join-Path $InstallDir $BinaryName
Invoke-WebRequest -Uri $DownloadUrl -OutFile $DestPath -UseBasicParsing

Write-Host "SecAI installed successfully to $DestPath!"

# Check if it's in PATH
$UserPath = [Environment]::GetEnvironmentVariable("PATH", "User")
if ($UserPath -notmatch [regex]::Escape($InstallDir)) {
    Write-Host "Adding $InstallDir to your user PATH..."
    $NewPath = "$UserPath;$InstallDir"
    [Environment]::SetEnvironmentVariable("PATH", $NewPath, "User")
    Write-Host "PATH updated! Please restart your terminal to use 'secai' globally."
} else {
    Write-Host "Run 'secai --help' to get started."
}
