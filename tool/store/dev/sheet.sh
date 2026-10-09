#!/bin/sh
# dev/sheet.sh <dir> <cols> <width> — tile the PNG stills in <dir> into <dir>/sheet.jpg
dir=$1; cols=${2:-3}; w=${3:-640}
cd "$dir" || exit 1
set -- t*.png
n=$#
inputs=""; scale=""; chain=""; layout=""
i=0
for f in "$@"; do
  inputs="$inputs -i $f"
  scale="$scale[$i]scale=$w:-2[v$i];"
  chain="$chain[v$i]"
  i=$((i+1))
done
h=$(ffprobe -v error -select_streams v -show_entries stream=width,height -of csv=p=0 "$1" | awk -F, -v w=$w '{printf "%d", int(w*$2/$1/2)*2}')
i=0
for f in "$@"; do
  x=$(( (i % cols) * w )); y=$(( (i / cols) * h ))
  layout="$layout${layout:+|}${x}_${y}"
  i=$((i+1))
done
if [ $n -eq 1 ]; then cp "$1" sheet.jpg; exit 0; fi
ffmpeg -hide_banner -loglevel error -y $inputs -filter_complex "${scale}${chain}xstack=inputs=$n:layout=$layout:fill=black" sheet.jpg
