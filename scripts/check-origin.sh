#!/usr/bin/env bash
# Origin conformance (roadmap M1-4c, design ADR-11): checks that a host serving the app's
# content behaves as the app relies on. Run against every configured origin, nightly.
#
#   scripts/check-origin.sh packs   https://packs.tamilaudiobible.com/
#   scripts/check-origin.sh content https://www.tamilscripture.com/
#
# packs:   catalogue.json and its signature served as JSON/text; the smallest pack supports
#          Range (206 with Content-Range), its length and SHA-256 match the catalogue, and it
#          is cached for long (immutable, versioned path).
# content: manifest.json revalidates (short cache); a chapter of the current build is JSON
#          and cached for long (build-keyed path).
set -euo pipefail
kind="${1:?packs or content}"
base="${2:?base URL ending in /}"
fail=0
py=$(command -v python3 >/dev/null && python3 -c 1 2>/dev/null && echo python3 || echo python)
ok()   { echo "ok   $*"; }
bad()  { echo "FAIL $*"; fail=1; }
header() { { grep -i "^$2:" <<<"$1" || true; } | head -1 | cut -d' ' -f2- | tr -d '\r'; }

case "$kind" in
packs)
  h=$(curl -sSI "${base}packs/catalogue.json")
  [[ $(header "$h" content-type) == application/json* ]] && ok "catalogue is JSON" || bad "catalogue content-type: $(header "$h" content-type)"
  curl -sSf -o /dev/null "${base}packs/catalogue.json.sig" && ok "catalogue signature present" || bad "no catalogue.json.sig"
  read -r path size sha < <(curl -sSf "${base}packs/catalogue.json" | $py -c '
import json, sys
p = min(json.load(sys.stdin)["packs"], key=lambda p: p["size"])
print(p["path"], p["size"], p["sha256"])' | tr -d '\r')
  url="${base}${path}"
  r=$(curl -sS -D - -o /dev/null -r 0-99 "$url")
  [[ $(head -1 <<<"$r") == *206* ]] && ok "Range answered with 206" || bad "Range: $(head -1 <<<"$r")"
  [[ $(header "$r" content-range) == "bytes 0-99/$size" ]] && ok "Content-Range total matches the catalogue ($size)" || bad "Content-Range: $(header "$r" content-range), catalogue size $size"
  cc=$(header "$(curl -sSI "$url")" cache-control)
  [[ "$cc" == *immutable* || "$cc" =~ max-age=([0-9]{6,}) ]] && ok "pack cached for long ($cc)" || bad "pack cache-control: $cc"
  got=$(curl -sSf "$url" | sha256sum | cut -d' ' -f1)
  [[ "$got" == "$sha" ]] && ok "SHA-256 matches the catalogue" || bad "SHA-256 $got, catalogue $sha"
  ;;
content)
  h=$(curl -sSI "${base}content/manifest.json")
  cc=$(header "$h" cache-control)
  [[ "$cc" == *must-revalidate* || "$cc" =~ max-age=([0-9]{1,3})([^0-9]|$) ]] && ok "manifest revalidates ($cc)" || bad "manifest cache-control: $cc"
  build=$(curl -sSf "${base}content/manifest.json" | $py -c 'import json,sys; print(json.load(sys.stdin)["build"])' | tr -d '\r')
  url="${base}content/${build}/IRVTAM/JHN/3.json"
  c=$(curl -sSI "$url")
  [[ $(head -1 <<<"$c") == *200* ]] && ok "chapter of build $build served" || bad "chapter: $(head -1 <<<"$c")"
  [[ $(header "$c" content-type) == application/json* ]] && ok "chapter is JSON" || bad "chapter content-type: $(header "$c" content-type)"
  # Cached for long by the browser, or at least at the edge: the app keeps its own build-keyed
  # cache, so what matters is that the CDN does not go to the origin for every reader.
  cc=$(header "$c" cache-control)
  edge=$(header "$(curl -sSI "$url")" x-vercel-cache)$(header "$(curl -sSI "$url")" cf-cache-status)
  if [[ "$cc" == *immutable* || "$cc" =~ max-age=([0-9]{5,}) || "$cc" =~ s-maxage=([0-9]{5,}) ]]; then ok "chapter cached for long ($cc)"
  elif [[ "$edge" == *HIT* ]]; then ok "chapter cached at the edge ($edge; cache-control $cc)"
  else bad "chapter neither long-cached nor an edge hit (cache-control $cc, edge $edge)"; fi
  ;;
*) echo "kind must be packs or content" >&2; exit 2 ;;
esac
exit $fail
