param([switch]$ModelsOnly, [switch]$Samples)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$modelRoot = Join-Path $projectRoot 'data/models/clip-vit-base-patch32'
New-Item -ItemType Directory -Force -Path $modelRoot | Out-Null
$revision = '6b7c523703ae26e39be4b03774966f8571c7f523'
$baseUrl = "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/$revision"

function Get-VerifiedAsset([string]$Url, [string]$Destination, [string]$Sha256) {
    if ((Test-Path -LiteralPath $Destination) -and (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash.ToLowerInvariant() -eq $Sha256) {
        Write-Output "Already verified: $([System.IO.Path]::GetFileName($Destination))"
        return
    }
    $partial = "$Destination.part"
    Write-Output "Downloading $([System.IO.Path]::GetFileName($Destination))..."
    curl.exe --fail --location --silent --show-error --retry 3 --output $partial $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Sha256) { throw "Checksum mismatch: $Destination" }
    Move-Item -LiteralPath $partial -Destination $Destination -Force
}

Get-VerifiedAsset "$baseUrl/onnx/vision_model_quantized.onnx" (Join-Path $modelRoot 'vision_model_quantized.onnx') '583fd1110a514667812fee7d684952aaf82a99b959760c8d7dca7e0ab9839299'
Get-VerifiedAsset "$baseUrl/onnx/text_model_quantized.onnx" (Join-Path $modelRoot 'text_model_quantized.onnx') '73baab855d406190da9faa498cfedf65f15cf309f4cc7385b7b032e6d08e5c3a'
Get-VerifiedAsset "$baseUrl/tokenizer.json" (Join-Path $modelRoot 'tokenizer.json') 'f7f3b7af117d467b58374797691a6438d3e6b9e9cef800dfd5dced7f697a90cd'

if (-not $ModelsOnly) {
    if ($env:OS -ne 'Windows_NT') { throw 'Models are installed. Install ffmpeg and ffprobe with your OS package manager, then set VIDEO_FFMPEG and VIDEO_FFPROBE if they are not on PATH.' }
    $toolRoot = Join-Path $projectRoot 'data/tools/ffmpeg'
    New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
    $archive = Join-Path $toolRoot 'ffmpeg-9.0.2-essentials_build.zip'
    Get-VerifiedAsset 'https://github.com/GyanD/codexffmpeg/releases/download/9.0.2/ffmpeg-9.0.2-essentials_build.zip' $archive '60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba'
    if (-not (Test-Path -LiteralPath (Join-Path $toolRoot 'ffmpeg-9.0.2-essentials_build/bin/ffmpeg.exe'))) {
        Expand-Archive -LiteralPath $archive -DestinationPath $toolRoot
    }
}
if ($Samples) {
    if ($ModelsOnly -or $env:OS -ne 'Windows_NT') { throw '-Samples requires the Windows FFmpeg installation; do not combine it with -ModelsOnly.' }
    $sampleRoot = Join-Path $projectRoot 'data/video-demo'
    New-Item -ItemType Directory -Force -Path $sampleRoot | Out-Null
    Get-VerifiedAsset 'https://images.unsplash.com/photo-1539008835657-9e8e9680c956?auto=format&fit=crop&w=640&q=85' (Join-Path $sampleRoot 'dress.jpg') 'f3801a3dd1a23770bef1755ac74d31ee5e5463abe57d83587504ff91c5a0003b'
    Get-VerifiedAsset 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?auto=format&fit=crop&w=640&q=85' (Join-Path $sampleRoot 'red-dress.jpg') 'd0d7063fe9f59c1fd028632a7c68866b978aff7c28b99cb7eca5a43a2a5d5b7f'
    $encoder = Join-Path $toolRoot 'ffmpeg-9.0.2-essentials_build/bin/ffmpeg.exe'
    foreach ($name in @('dress', 'red-dress')) {
        & $encoder -nostdin -v error -y -loop 1 -i (Join-Path $sampleRoot "$name.jpg") -t 3 -vf 'scale=640:640:force_original_aspect_ratio=decrease,pad=ceil(iw/2)*2:ceil(ih/2)*2' -c:v libx264 -threads 1 -pix_fmt yuv420p -movflags +faststart (Join-Path $sampleRoot "$name.mp4")
        if ($LASTEXITCODE -ne 0) { throw 'Sample video encoding failed.' }
    }
    Write-Output 'Created blue and red dress sample clips in data/video-demo. These are test images under the Unsplash License, not actual seller inventory.'
}
Write-Output 'Offline video tagging assets are ready. No API key is required; inference runs locally. See docs/VIDEO-TAGGING.md for model licensing and limitations.'
