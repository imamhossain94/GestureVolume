#!/bin/bash
# Renders every screenshot in both styles into $1, then a contact sheet.
OUT=$1; mkdir -p $OUT
for id in swipe deck rotate fills search appearance menu visibility coin; do
  for st in card bleed; do node render.mjs still shot $OUT/${id}_${st}.png --query "id=$id&style=$st" 2>&1 | grep -v "^$" | head -3; done
done
