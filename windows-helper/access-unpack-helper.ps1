param(
  [Parameter(Mandatory = $true)][string]$InputPath,
  [Parameter(Mandatory = $true)][string]$OutputPath,
  [switch]$IncludeSystemObjects
)

$ErrorActionPreference = "Stop"

function Write-Json($Path, $Object) {
  $Object | ConvertTo-Json -Depth 32 | Set-Content -Path $Path -Encoding UTF8
}

New-Item -ItemType Directory -Force -Path $OutputPath | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputPath "forms\\raw") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputPath "reports\\raw") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputPath "macros\\raw") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputPath "vba\\raw") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputPath "startup") | Out-Null

$result = [ordered]@{
  status = "partial"
  extracted = @()
  warnings = @()
}

try {
  $access = New-Object -ComObject Access.Application
  $access.Visible = $false
  $access.OpenCurrentDatabase($InputPath)

  foreach ($item in $access.CurrentProject.AllForms) {
    $target = Join-Path $OutputPath ("forms\\raw\\{0}.txt" -f $item.Name)
    $access.SaveAsText(2, $item.Name, $target)
    $result.extracted += @{ type = "form"; name = $item.Name; path = $target }
  }
  foreach ($item in $access.CurrentProject.AllReports) {
    $target = Join-Path $OutputPath ("reports\\raw\\{0}.txt" -f $item.Name)
    $access.SaveAsText(3, $item.Name, $target)
    $result.extracted += @{ type = "report"; name = $item.Name; path = $target }
  }
  foreach ($item in $access.CurrentProject.AllMacros) {
    $target = Join-Path $OutputPath ("macros\\raw\\{0}.txt" -f $item.Name)
    $access.SaveAsText(4, $item.Name, $target)
    $result.extracted += @{ type = "macro"; name = $item.Name; path = $target }
  }
  foreach ($item in $access.CurrentProject.AllModules) {
    $target = Join-Path $OutputPath ("vba\\raw\\{0}.txt" -f $item.Name)
    $access.SaveAsText(5, $item.Name, $target)
    $result.extracted += @{ type = "module"; name = $item.Name; path = $target }
  }

  $startup = [ordered]@{
    startupForm = $access.GetOption("Display Form/Page")
    allowFullMenus = $access.GetOption("Allow Full Menus")
    allowBypassKey = $access.GetOption("AllowBypassKey")
  }
  Write-Json -Path (Join-Path $OutputPath "startup\\startup.json") -Object $startup

  $access.CloseCurrentDatabase()
  $access.Quit()
  [System.Runtime.InteropServices.Marshal]::ReleaseComObject($access) | Out-Null
  $result.status = "success"
}
catch {
  $result.warnings += $_.Exception.Message
}

Write-Json -Path (Join-Path $OutputPath "helper-result.json") -Object $result
