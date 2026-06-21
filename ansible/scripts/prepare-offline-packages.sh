#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANSIBLE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_DIR="${ANSIBLE_DIR}/files/offline"

NODE_VERSION="${NODE_VERSION:-24.14.0}"
NVM_VERSION="${NVM_VERSION:-v0.40.3}"
NODE_DIST_BASE="${NODE_DIST_BASE:-https://npmmirror.com/mirrors/node}"

mkdir -p "${OUT_DIR}"

download() {
  local url="$1"
  local dest="$2"

  if [ -s "${dest}" ]; then
    echo "exists ${dest}"
    return
  fi

  echo "download ${url}"
  curl -fL --retry 3 --connect-timeout 15 -o "${dest}.tmp" "${url}"
  mv "${dest}.tmp" "${dest}"
}

download \
  "${NODE_DIST_BASE}/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
  "${OUT_DIR}/node-v${NODE_VERSION}-linux-x64.tar.xz"

download \
  "https://github.com/nvm-sh/nvm/archive/refs/tags/${NVM_VERSION}.tar.gz" \
  "${OUT_DIR}/nvm-${NVM_VERSION}.tar.gz"

download \
  "https://github.com/ohmyzsh/ohmyzsh/archive/refs/heads/master.tar.gz" \
  "${OUT_DIR}/oh-my-zsh.tar.gz"

download \
  "https://github.com/zsh-users/zsh-autosuggestions/archive/refs/heads/master.tar.gz" \
  "${OUT_DIR}/zsh-autosuggestions.tar.gz"

download \
  "https://github.com/zsh-users/zsh-syntax-highlighting/archive/refs/heads/master.tar.gz" \
  "${OUT_DIR}/zsh-syntax-highlighting.tar.gz"

(
  cd "${OUT_DIR}"
  shasum -a 256 \
    "node-v${NODE_VERSION}-linux-x64.tar.xz" \
    "nvm-${NVM_VERSION}.tar.gz" \
    oh-my-zsh.tar.gz \
    zsh-autosuggestions.tar.gz \
    zsh-syntax-highlighting.tar.gz \
    > SHA256SUMS
)

echo "offline packages ready in ${OUT_DIR}"
