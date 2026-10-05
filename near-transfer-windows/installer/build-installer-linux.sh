#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project="$root/src/NearTransfer.Windows/NearTransfer.Windows.csproj"
publish_dir="$root/publish/legacy-anycpu"
icon_file="$root/src/NearTransfer.Windows/Assets/NearTransfer.ico"
output_file="$root/release/v1.0.0-NearTransfer-Windows-Setup.exe"

dotnet publish "$project" \
  --configuration Release \
  --output "$publish_dir" \
  -p:Platform=AnyCPU \
  -p:EnableWindowsTargeting=true \
  -p:DebugType=None \
  -p:DebugSymbols=false

mkdir -p "$(dirname "$output_file")"
makensis -V3 \
  "-DOUTPUT_FILE=$output_file" \
  "-DPUBLISH_DIR=$publish_dir" \
  "-DICON_FILE=$icon_file" \
  "$root/installer/NearTransfer.nsi"

printf '\nCreated installer: %s\n' "$output_file"
du -h "$output_file"