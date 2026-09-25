<#
.SYNOPSIS
  Deterministic packager for OPlusFluidCompat-System (plan §16).
  Copies verified OEM blobs from ignored vendor/ into a staging tree and
  builds the KernelSU/Magisk ZIP. Refuses on hash mismatch, missing blobs,
  or any *.odex/*.vdex/oat content. Never commits blobs.
#>
param(
  [string]$RepoRoot = (Split-Path $PSScriptRoot -Parent),
  [string]$OutDir = (Join-Path $RepoRoot 'out')
)

$ErrorActionPreference = 'Stop'

function Sha256File([string]$Path) {
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try {
    $bytes = [IO.File]::ReadAllBytes($Path)
    ([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '').ToLower()
  } finally { $sha.Dispose() }
}

$manifestPath = Join-Path $RepoRoot 'system-module\vendor-manifest.json'
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json

$stage = Join-Path ([IO.Path]::GetTempPath()) ('oplusfluid-system-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage | Out-Null
try {
  # Tracked module skeleton (scripts, metadata, README, manifest)
  foreach ($item in @('module.prop', 'customize.sh', 'post-fs-data.sh', 'post-mount.sh', 'README.md', 'vendor-manifest.json')) {
    Copy-Item (Join-Path $RepoRoot "system-module\$item") (Join-Path $stage $item)
  }

  # Verified blobs only
  foreach ($b in $manifest.blobs) {
    $src = Join-Path $RepoRoot ($b.vendor_path -replace '/', '\')
    $dst = Join-Path $stage ($b.module_path -replace '/', '\')
    if (-not (Test-Path $src)) { throw "missing blob: $($b.vendor_path)" }
    $actual = Sha256File $src
    if ($actual -ne $b.sha256.ToLower()) { throw "hash mismatch: $($b.vendor_path)`n  expected $($b.sha256)`n  actual   $actual" }
    New-Item -ItemType Directory -Path (Split-Path $dst -Parent) -Force | Out-Null
    Copy-Item $src $dst
  }

  # Forbidden content guard
  $bad = Get-ChildItem -Recurse -Path $stage -Include *.odex, *.vdex, oat -ErrorAction SilentlyContinue
  if ($bad) { throw "forbidden ART content staged: $($bad[0].FullName)" }

  # META-INF (minimal Magisk/KernelSU installer hooks)
  $metainf = Join-Path $stage 'META-INF\com\google\android'
  New-Item -ItemType Directory -Path $metainf -Force | Out-Null
  Set-Content (Join-Path $metainf 'updater-script') '#MAGISK' -NoNewline
  Set-Content (Join-Path $metainf 'update-binary') '#MAGISK' -NoNewline

  # File manifest inside staging
  $files = Get-ChildItem -Recurse -File -Path $stage | ForEach-Object {
    $rel = $_.FullName.Substring($stage.Length + 1) -replace '\\', '/'
    [pscustomobject]@{ path = $rel; sha256 = (Sha256File $_.FullName); size = $_.Length }
  }
  $files | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $stage 'module-files.json')

  New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
  $moduleProp = Get-Content (Join-Path $RepoRoot 'system-module\module.prop') -Raw
  $version = [regex]::Match($moduleProp, '(?m)^version=(.+)$').Groups[1].Value.Trim()
  $zip = Join-Path $OutDir "OPlusFluidCompat-System-$version.zip"
  if (Test-Path $zip) { Remove-Item $zip }
  Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip
  $zipHash = Sha256File $zip
  "ZIP: $zip"
  "SHA256: $zipHash"
  "FILES: $($files.Count)"
}
finally {
  Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
}
