$ErrorActionPreference = 'Stop'
$targetDir = Join-Path $PSScriptRoot 'libs'
[IO.Directory]::CreateDirectory($targetDir) | Out-Null
$artifacts = @(
    @('https://cdn.modrinth.com/data/A3oZ8vhW/versions/Nvq2rJvB/tacznpcs-2.1.0-1.20.1.jar', 'tacznpcs-2.1.0-1.20.1.jar', '2593AA66CD56C08538A91ED3D274C054B043F0C6A4C0FF51A37DE310D60734EE'),
    @('https://cdn.modrinth.com/data/SzzJttH8/versions/AzCBJlex/tacz-1.20.1-1.1.8-hotfix2.jar', 'tacz-1.20.1-1.1.8-hotfix2.jar', '2CBB89F44333D2EB4E825D86D789FF119B272FF75184390B55BAFD4F0E7F10C2')
)
foreach ($artifact in $artifacts) {
    $path = Join-Path $targetDir $artifact[1]
    if (!(Test-Path -LiteralPath $path)) { Invoke-WebRequest -Uri $artifact[0] -OutFile $path }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $artifact[2]) {
        throw "SHA-256 mismatch: $path. Remove this file and fetch again."
    }
    Write-Output "Verified $($artifact[1])"
}
