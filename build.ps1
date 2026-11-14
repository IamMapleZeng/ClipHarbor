param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$PlatformJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [Parameter(Mandatory=$true)][string]$Keystore,
    [Parameter(Mandatory=$true)][string]$Output,
    [string]$RuntimeCache = (Join-Path ([IO.Path]::GetTempPath()) 'clipharbor-dependencies')
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = (Resolve-Path -LiteralPath $Jdk).Path
function Run([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Failed: $Program (exit $LASTEXITCODE)" }
}
$source = $PSScriptRoot
$stage = Join-Path ([IO.Path]::GetTempPath()) ('xsave-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $stage,"$stage/classes","$stage/dex" | Out-Null
Run "$BuildTools/aapt2.exe" @('compile','--dir',"$source/res",'-o',"$stage/res.zip")
Run "$BuildTools/aapt2.exe" @('link','-I',$PlatformJar,'--manifest',"$source/AndroidManifest.xml",'-o',"$stage/base.apk","$stage/res.zip")
$inputs = @(Get-ChildItem -LiteralPath "$source/src" -Filter '*.java' | ForEach-Object FullName)
Run "$env:JAVA_HOME/bin/javac.exe" (@('--release','8','-encoding','UTF-8','-classpath',$PlatformJar,'-d',"$stage/classes") + $inputs)
Run "$env:JAVA_HOME/bin/jar.exe" @('cf',"$stage/classes.jar",'-C',"$stage/classes",'.')
Run "$BuildTools/d8.bat" @('--release','--lib',$PlatformJar,'--min-api','29','--output',"$stage/dex","$stage/classes.jar")
Run 'python' @("$source/package-runtime.py","$stage/base.apk","$stage/dex/classes.dex",$RuntimeCache)
Run "$BuildTools/zipalign.exe" @('-f','4',"$stage/base.apk","$stage/aligned.apk")
Run "$BuildTools/apksigner.bat" @('sign','--ks',$Keystore,'--ks-key-alias','xsave','--ks-pass','env:XSAVE_KEYPASS','--out',$Output,"$stage/aligned.apk")
Run "$BuildTools/apksigner.bat" @('verify','--verbose',$Output)
