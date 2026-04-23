param(
  [Parameter(Mandatory = $false)]
  [string]$Root = (Join-Path $PSScriptRoot ".."),
  [Parameter(Mandatory = $false)]
  [string[]]$CliArgs = @()
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

$cpFile = Join-Path $env:TEMP ("access-unpack-cli-cp-" + [Guid]::NewGuid().ToString("N") + ".txt")
$argFile = Join-Path $env:TEMP ("access-unpack-cli-java-" + [Guid]::NewGuid().ToString("N") + ".args")
$exitCode = 1

try {
  & $mvnCmd -q -f (Join-Path $Root "pom.xml") -pl access-unpack-cli -am -DskipTests package dependency:build-classpath `
    "-Dmdep.includeScope=runtime" `
    "-Dmdep.outputFile=$cpFile"
  if ($LASTEXITCODE -ne 0) {
    throw "Maven failed while building CLI runtime classpath."
  }

  if (-not (Test-Path -LiteralPath $cpFile)) {
    throw "Classpath file was not generated: $cpFile"
  }

  $dependencyClasspath = (Get-Content -Raw -LiteralPath $cpFile).Trim()
  if ([string]::IsNullOrWhiteSpace($dependencyClasspath)) {
    throw "Generated classpath file is empty: $cpFile"
  }

  $classes = (Join-Path $Root "access-unpack-cli\target\classes") + ";" + (Join-Path $Root "access-unpack-core\target\classes")
  Set-Content -LiteralPath $argFile -Value @(
    "-cp"
    ($classes + ";" + $dependencyClasspath)
    "com.access.unpack.cli.AccessUnpackCli"
  ) -Encoding ASCII

  & $javaCmd "@$argFile" @CliArgs
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
