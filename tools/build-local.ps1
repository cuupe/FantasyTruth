param(
    [Parameter(Mandatory)][string]$Instance,
    [string]$Jdk = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$instanceDir = (Resolve-Path -LiteralPath $Instance).Path
$game = Split-Path -Parent (Split-Path -Parent $instanceDir)
$libraries = Join-Path $game 'libraries'
$instanceId = Split-Path -Leaf $instanceDir
$profile = Get-Content -Raw -LiteralPath (Join-Path $instanceDir "$instanceId.json") | ConvertFrom-Json
$launch = $profile.arguments.game
if ($launch[$launch.IndexOf('--fml.mcVersion') + 1] -ne '1.20.1' -or
        $launch[$launch.IndexOf('--fml.forgeVersion') + 1] -ne '47.1.106') {
    throw 'Expected the NeoForge 47.1.106 Minecraft 1.20.1 profile.'
}

function Run-Jdk([string]$Tool, [string[]]$Arguments) {
    $executable = Join-Path $Jdk "bin\$Tool.exe"
    & $executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}

$build = Join-Path $project 'build'
$stage = Join-Path $build ('local-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $stage 'classes'
$out = Join-Path $build 'libs'
New-Item -ItemType Directory -Force $classes,$out | Out-Null

$mcp = '1.20.1-20230612.114412'
$client = Join-Path $libraries "net/minecraft/client/$mcp/client-$mcp"
$forge = Join-Path $libraries 'net/neoforged/forge/1.20.1-47.1.106/forge-1.20.1-47.1.106-universal.jar'
$mappingLib = Join-Path $libraries 'net/neoforged/srgutils/1.0.0/srgutils-1.0.0.jar'
$art = Join-Path $libraries 'net/neoforged/AutoRenamingTool/2.0.3/AutoRenamingTool-2.0.3-all.jar'
$merged = Join-Path $libraries "de/oceanlabs/mcp/mcp_config/$mcp/mcp_config-$mcp-mappings-merged.txt"
$toOfficial = Join-Path $stage 'srg-to-official.tsrg'
$toSrg = Join-Path $stage 'official-to-srg.tsrg'
$named = Join-Path $stage 'minecraft-official.jar'
$devJar = Join-Path $stage 'FantasyTruth-dev.jar'
$modJar = Join-Path $out 'FantasyTruth-0.11.0-alpha.1.jar'

Run-Jdk 'java' @('-cp', $mappingLib, (Join-Path $PSScriptRoot 'LocalMappings.java'),
    $merged, "$client-mappings.txt", $toOfficial, $toSrg)
Run-Jdk 'java' @('-Xmx2G', '-jar', $art, '--input', "$client-srg.jar", '--output', $named,
    '--map', $toOfficial, '--threads', '4') *> (Join-Path $stage 'remap-minecraft.log')

$deps = @($named, $forge) + @($profile.libraries | ForEach-Object {
    $artifact = $_.downloads.artifact.path
    if ($artifact) {
        $file = Join-Path $libraries $artifact
        if (Test-Path -LiteralPath $file -PathType Leaf) { $file }
    }
})
$sources = @(Get-ChildItem -Path (Join-Path $project 'src/main/java') -Recurse -Filter '*.java' -File |
    ForEach-Object FullName)
Run-Jdk 'javac' (@('--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', ($deps -join ';'), '-d', $classes) + $sources)
Run-Jdk 'jar' @('--create', '--file', $devJar, '--manifest', (Join-Path $project 'src/main/manifest.mf'),
    '-C', $classes, '.', '-C', (Join-Path $project 'src/main/resources'), '.')
Run-Jdk 'java' @('-jar', $art, '--input', $devJar, '--output', $modJar, '--map', $toSrg,
    '--lib', $named, '--threads', '2') *> (Join-Path $stage 'remap-mod.log')

Write-Output $modJar
