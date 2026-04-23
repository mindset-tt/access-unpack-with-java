param(
  [Parameter(Mandatory = $false)]
  [string]$Root = (Join-Path $PSScriptRoot "..")
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path -LiteralPath $Root).Path

$toolOutput = & (Join-Path $PSScriptRoot "ensure-toolchain.ps1") -Root $Root

$tool = @{}
foreach ($line in $toolOutput) {
  if ($line -match "^([^=]+)=(.*)$") {
    $tool[$Matches[1]] = $Matches[2]
  }
}

$javaCmd = $tool["JAVA_CMD"]
$mvnCmd = $tool["MVN_CMD"]
$javaHome = $tool["JAVA_HOME"]

if ([string]::IsNullOrWhiteSpace($javaCmd) -or [string]::IsNullOrWhiteSpace($mvnCmd)) {
  throw "Failed to resolve Java or Maven command from ensure-toolchain.ps1."
}

if (-not [string]::IsNullOrWhiteSpace($javaHome)) {
  $env:JAVA_HOME = $javaHome
  $env:Path = "$javaHome\bin;$env:Path"
}

Write-Host "Launching access-unpack UI..."

$cpFile = Join-Path $env:TEMP ("access-unpack-ui-cp-" + [Guid]::NewGuid().ToString("N") + ".txt")
$argFile = Join-Path $env:TEMP ("access-unpack-ui-java-" + [Guid]::NewGuid().ToString("N") + ".args")
$exitCode = 1

try {
  & $mvnCmd -q -f (Join-Path $Root "pom.xml") -pl access-unpack-ui -am -DskipTests package dependency:build-classpath `
    "-Dmdep.includeScope=runtime" `
    "-Dmdep.outputFile=$cpFile"
  if ($LASTEXITCODE -ne 0) {
    throw "Maven failed while building UI runtime classpath."
  }

  if (-not (Test-Path -LiteralPath $cpFile)) {
    throw "Classpath file was not generated: $cpFile"
  }

  $dependencyClasspath = (Get-Content -Raw -LiteralPath $cpFile).Trim()
  if ([string]::IsNullOrWhiteSpace($dependencyClasspath)) {
    throw "Generated classpath file is empty: $cpFile"
  }

  $classes = (Join-Path $Root "access-unpack-ui\target\classes") + ";" + (Join-Path $Root "access-unpack-core\target\classes")
  Set-Content -LiteralPath $argFile -Value @(
    "-cp"
    ($classes + ";" + $dependencyClasspath)
    "com.access.unpack.ui.AccessUnpackUi"
  ) -Encoding ASCII

  & $javaCmd "@$argFile"
  $exitCode = $LASTEXITCODE
}
catch {
  Write-Error $_.Exception.Message
  $exitCode = 1
}
finally {
  if (Test-Path -LiteralPath $cpFile) {
    Remove-Item -LiteralPath $cpFile -Force -ErrorAction SilentlyContinue
  }
  if (Test-Path -LiteralPath $argFile) {
    Remove-Item -LiteralPath $argFile -Force -ErrorAction SilentlyContinue
  }
}

exit $exitCode
