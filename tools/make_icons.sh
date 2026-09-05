#!/bin/sh
# Generate launcher PNGs in all densities from the SVG icon
set -e
cd "$(dirname "$0")/.."
TMP=/tmp/icon-gen
rm -rf $TMP && mkdir -p $TMP

# Build SVG equivalent of our vector
cat > $TMP/icon.svg <<'SVG'
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 192 192" width="192" height="192">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0E1014"/>
      <stop offset="50%" stop-color="#1A2A4A"/>
      <stop offset="100%" stop-color="#0E1014"/>
    </linearGradient>
  </defs>
  <rect width="192" height="192" fill="url(#bg)"/>
  <g transform="translate(96,108)">
    <!-- Globe -->
    <circle r="40" fill="#4DD0E1"/>
    <!-- Africa silhouette -->
    <path d="M-12,-25 Q-3,-18 6,-20 Q12,-12 8,-3 Q4,5 9,12 Q15,16 13,23 Q5,28 -3,24 Q-13,21 -17,12 Q-21,2 -18,-8 Q-16,-19 -12,-25 Z" fill="#0E1014"/>
    <!-- Signal waves left -->
    <path d="M-44,-25 Q-48,-12 -44,2 L-36,-1 Q-40,-12 -36,-22 Z" fill="#FFB74D"/>
    <path d="M-54,-30 Q-60,-12 -54,8 L-46,5 Q-52,-12 -46,-26 Z" fill="#FFB74D"/>
    <!-- Signal waves right -->
    <path d="M44,-25 Q48,-12 44,2 L36,-1 Q40,-12 36,-22 Z" fill="#FFB74D"/>
    <path d="M54,-30 Q60,-12 54,8 L46,5 Q52,-12 46,-26 Z" fill="#FFB74D"/>
    <!-- Antenna -->
    <rect x="-4" y="-58" width="8" height="12" fill="#FFB74D"/>
    <circle cx="0" cy="-62" r="4" fill="#FFB74D"/>
  </g>
</svg>
SVG

for spec in "mdpi:48" "hdpi:72" "xhdpi:96" "xxhdpi:144" "xxxhdpi:192"; do
  density="${spec%:*}"
  size="${spec#*:}"
  out_dir="app/src/main/res/mipmap-${density}"
  mkdir -p "$out_dir"
  magick $TMP/icon.svg -background none -resize ${size}x${size} "$out_dir/ic_launcher.png"
  magick $TMP/icon.svg -background none -resize ${size}x${size} "$out_dir/ic_launcher_round.png"
  echo "  $out_dir: $size x $size"
done
ls -la app/src/main/res/mipmap-*/ | grep -E "\.png$" | head -20
echo "OK"
