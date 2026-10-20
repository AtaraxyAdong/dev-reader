[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$properties = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'gradle.properties') -Raw
function Read-Property([string]$Name) {
    $match = [regex]::Match($properties, '(?m)^' + [regex]::Escape($Name) + '\s*=\s*(\S+)\s*$')
    if (!$match.Success) { throw "Missing property: $Name" }
    return $match.Groups[1].Value
}
$pluginName = Read-Property 'pluginName'
$version = Read-Property 'pluginVersion'
$distributionDir = Join-Path $RepositoryRoot 'build/distributions'
$packages = @(Get-ChildItem -LiteralPath $distributionDir -Filter '*.zip' -File)
$packageName = "$pluginName-$version.zip"
$currentPackages = @($packages | Where-Object Name -eq $packageName)
if ($currentPackages.Count -ne 1) {
    throw "Missing current plugin package: $packageName"
}
$package = $currentPackages[0]
$archive = [IO.Compression.ZipFile]::OpenRead($package.FullName)
try {
    $files = @($archive.Entries | Where-Object { $_.Name })
    if (!$files.Count -or @($files | Where-Object { !$_.FullName.StartsWith("$pluginName/") }).Count) {
        throw 'Invalid plugin ZIP root. Install the inner Gradle distribution ZIP, not a GitHub artifact wrapper.'
    }
    $descriptors = @()
    foreach ($entry in $files) {
        if ($entry.FullName -notmatch ('^' + [regex]::Escape($pluginName) + '/lib/[^/]+\.jar$')) { continue }
        $jarStream = [IO.MemoryStream]::new()
        $entryStream = $entry.Open()
        try { $entryStream.CopyTo($jarStream) } finally { $entryStream.Dispose() }
        $jarStream.Position = 0
        $jar = [IO.Compression.ZipArchive]::new($jarStream, [IO.Compression.ZipArchiveMode]::Read)
        try {
            $descriptor = $jar.GetEntry('META-INF/plugin.xml')
            if ($descriptor) {
                $reader = [IO.StreamReader]::new($descriptor.Open())
                try { $descriptors += ,([xml]$reader.ReadToEnd()) } finally { $reader.Dispose() }
            }
        } finally { $jar.Dispose(); $jarStream.Dispose() }
    }
    if ($descriptors.Count -ne 1) { throw 'Expected one plugin descriptor inside lib/*.jar.' }
    $metadata = $descriptors[0].'idea-plugin'
    if ([string]$metadata.id -ne 'com.github.yuyuanweb.mianshiyaplugin') { throw 'Unexpected plugin ID.' }
    if ([string]$metadata.version -ne $version) { throw 'Plugin descriptor version does not match gradle.properties.' }
    if ([string]$metadata.'idea-version'.'since-build' -ne (Read-Property 'pluginSinceBuild') -or
        [string]$metadata.'idea-version'.'until-build' -ne (Read-Property 'pluginUntilBuild')) {
        throw 'Plugin compatibility range does not match gradle.properties.'
    }
} finally { $archive.Dispose() }
$checksum = (Get-FileHash -LiteralPath $package.FullName -Algorithm SHA256).Hash
Write-Output "Verified installable plugin: $packageName ($($package.Length) bytes)"
Write-Output "SHA-256: $checksum"
if ($env:GITHUB_OUTPUT) {
    "plugin_zip=build/distributions/$packageName" >> $env:GITHUB_OUTPUT
    "package_file=$packageName" >> $env:GITHUB_OUTPUT
    "sha256=$checksum" >> $env:GITHUB_OUTPUT
}