param(
  [Parameter(Mandatory = $true)]
  [string]$Root
)

$ErrorActionPreference = "Stop"

$RequiredJavaMajor = 21
$RequiredMavenMajor = 3
$RequiredMavenMinor = 9
$MavenVersion = "3.9.9"

$Root = (Resolve-Path -LiteralPath $Root).Path
$ToolsDir = Join-Path $Root "tools"
$DownloadsDir = Join-Path $ToolsDir ".downloads"

New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
New-Item -ItemType Directory -Force -Path $DownloadsDir | Out-Null

function Write-Info {
  param([string]$Message)
  [Console]::Error.WriteLine($Message)
}

function Get-JavaMajor {
  param([string]$JavaCommand)

  try {
    $quotedCommand = '"' + $JavaCommand + '"'
    $line = (& cmd.exe /d /c "$quotedCommand -version 2>&1" | Select-Object -First 1 | ForEach-Object { $_.ToString() })
  }
  catch {
    return 0
  }

  if ($line -match '"(\d+)') {
    return [int]$Matches[1]
  }

  return 0
}

function Get-MavenVersion {
  param([string]$MavenCommand)

  try {
    $quotedCommand = '"' + $MavenCommand + '"'
    $line = (& cmd.exe /d /c "$quotedCommand -v 2>&1" | Select-Object -First 1 | ForEach-Object { $_.ToString() })
  }
  catch {
    return $null
  }

  if ($line -match 'Apache Maven\s+(\d+)\.(\d+)(?:\.(\d+))?') {
    return "$($Matches[1]).$($Matches[2]).$($Matches[3])"
  }

  return $null
}

function Test-MavenSupported {
  param([string]$Version)

  if ([string]::IsNullOrWhiteSpace($Version)) {
    return $false
  }

  $parts = $Version.Split(".")
  if ($parts.Count -lt 2) {
    return $false
  }

  $major = 0
  $minor = 0
  if (-not [int]::TryParse($parts[0], [ref]$major)) {
    return $false
  }
  if (-not [int]::TryParse($parts[1], [ref]$minor)) {
    return $false
  }

  if ($major -gt $RequiredMavenMajor) {
    return $true
  }
  if ($major -lt $RequiredMavenMajor) {
    return $false
  }

  return $minor -ge $RequiredMavenMinor
}

function Get-WindowsArchToken {
  $arch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
  switch ($arch) {
    "x64" { return "x64" }
    "arm64" { return "aarch64" }
    default { throw "Unsupported CPU architecture for automatic Java installation: $arch" }
  }
}

function Find-LocalJava {
  $jdkDirs = Get-ChildItem -Path $ToolsDir -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending

  foreach ($jdkDir in $jdkDirs) {
    $javaCandidates = @(
      (Join-Path $jdkDir.FullName "bin\java.exe"),
      (Join-Path $jdkDir.FullName "bin\java")
    )
    $javaCmd = $javaCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if ($null -ne $javaCmd) {
      $major = Get-JavaMajor $javaCmd
      if ($major -ge $RequiredJavaMajor) {
        return @{
          JavaHome = $jdkDir.FullName
          JavaCmd  = $javaCmd
        }
      }
    }
  }

  return $null
}

function Find-LocalMaven {
  $mavenDirs = Get-ChildItem -Path $ToolsDir -Directory -Filter "apache-maven-*" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending

  foreach ($mavenDir in $mavenDirs) {
    $mvnCmd = Join-Path $mavenDir.FullName "bin\mvn.cmd"
    if (Test-Path -LiteralPath $mvnCmd) {
      $version = Get-MavenVersion $mvnCmd
      if (Test-MavenSupported $version) {
        return $mvnCmd
      }
    }
  }

  return $null
}

function Install-LocalJava {
  $archToken = Get-WindowsArchToken
  $url = "https://api.adoptium.net/v3/binary/latest/$RequiredJavaMajor/ga/windows/$archToken/jdk/hotspot/normal/eclipse?project=jdk"
  $zipPath = Join-Path $DownloadsDir "temurin-jdk$RequiredJavaMajor-windows-$archToken.zip"
  $extractDir = Join-Path $DownloadsDir ("extract-jdk-" + [Guid]::NewGuid().ToString("N"))

  Write-Info "Java 21+ not found. Downloading Temurin JDK $RequiredJavaMajor..."
  Invoke-WebRequest -Uri $url -OutFile $zipPath
  New-Item -ItemType Directory -Force -Path $extractDir | Out-Null
  Expand-Archive -LiteralPath $zipPath -DestinationPath $extractDir -Force

  $extractedJdk = Get-ChildItem -Path $extractDir -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if ($null -eq $extractedJdk) {
    throw "Downloaded Java archive did not contain a jdk-21* directory."
  }

  $targetDir = Join-Path $ToolsDir $extractedJdk.Name
  if (Test-Path -LiteralPath $targetDir) {
    $targetDir = Join-Path $ToolsDir ($extractedJdk.Name + "-local-" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
  }

  Move-Item -LiteralPath $extractedJdk.FullName -Destination $targetDir

  Remove-Item -LiteralPath $extractDir -Recurse -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
}

function Install-LocalMaven {
  $zipName = "apache-maven-$MavenVersion-bin.zip"
  $url = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/$zipName"
  $zipPath = Join-Path $DownloadsDir $zipName
  $extractDir = Join-Path $DownloadsDir ("extract-maven-" + [Guid]::NewGuid().ToString("N"))

  Write-Info "Maven 3.9+ not found. Downloading Apache Maven $MavenVersion..."
  Invoke-WebRequest -Uri $url -OutFile $zipPath
  New-Item -ItemType Directory -Force -Path $extractDir | Out-Null
  Expand-Archive -LiteralPath $zipPath -DestinationPath $extractDir -Force

  $extractedMaven = Get-ChildItem -Path $extractDir -Directory -Filter "apache-maven-*" -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if ($null -eq $extractedMaven) {
    throw "Downloaded Maven archive did not contain an apache-maven-* directory."
  }

  $targetDir = Join-Path $ToolsDir $extractedMaven.Name
  if (Test-Path -LiteralPath $targetDir) {
    $targetDir = Join-Path $ToolsDir ($extractedMaven.Name + "-local-" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
  }

  Move-Item -LiteralPath $extractedMaven.FullName -Destination $targetDir

  Remove-Item -LiteralPath $extractDir -Recurse -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
}

$javaCmd = $null
$javaHome = $null

$systemJava = Get-Command java -ErrorAction SilentlyContinue | Select-Object -First 1
$systemJavaMajor = 0
if ($null -ne $systemJava) {
  $systemJavaMajor = Get-JavaMajor $systemJava.Source
  if ($systemJavaMajor -ge $RequiredJavaMajor) {
    $javaCmd = $systemJava.Source
  }
}

if ([string]::IsNullOrWhiteSpace($javaCmd)) {
  $localJava = Find-LocalJava
  if ($null -eq $localJava) {
    Install-LocalJava
    $localJava = Find-LocalJava
  }

  if ($null -eq $localJava) {
    if ($null -ne $systemJava) {
      throw "Java 21+ required. Found Java $systemJavaMajor at $($systemJava.Source) but could not install a local JDK to $ToolsDir."
    }
    throw "Java 21+ is required and local installation to $ToolsDir failed."
  }

  $javaCmd = $localJava.JavaCmd
  $javaHome = $localJava.JavaHome
  $env:JAVA_HOME = $javaHome
  $env:Path = "$javaHome\bin;$env:Path"
}

$mvnCmd = $null

$systemMaven = Get-Command mvn -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -ne $systemMaven) {
  $systemMavenVersion = Get-MavenVersion $systemMaven.Source
  if (Test-MavenSupported $systemMavenVersion) {
    $mvnCmd = $systemMaven.Source
  }
}

if ([string]::IsNullOrWhiteSpace($mvnCmd)) {
  $localMaven = Find-LocalMaven
  if ([string]::IsNullOrWhiteSpace($localMaven)) {
    Install-LocalMaven
    $localMaven = Find-LocalMaven
  }

  if ([string]::IsNullOrWhiteSpace($localMaven)) {
    throw "Maven 3.9+ is required and local installation to $ToolsDir failed."
  }

  $mvnCmd = $localMaven
}

Write-Output "JAVA_CMD=$javaCmd"
Write-Output "MVN_CMD=$mvnCmd"
Write-Output "JAVA_HOME=$javaHome"
Write-Output "TOOLS_DIR=$ToolsDir"
