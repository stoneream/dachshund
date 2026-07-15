#!/usr/bin/env bash
set -euo pipefail

required_mise_version="2026.6.2"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/../.." && pwd)"

cd "$repo_dir"
export PATH="${HOME}/.local/bin:${HOME}/.local/share/mise/shims:${PATH}"

current_mise_version="$(mise --version 2>/dev/null | awk '{print $1}' | sed 's/^v//' || true)"
if [[ "$current_mise_version" != "$required_mise_version" ]]; then
  if ! command -v curl >/dev/null 2>&1; then
    echo "mise $required_mise_version is not installed and curl is unavailable. Install curl or preinstall mise during the Codex Cloud setup phase." >&2
    exit 1
  fi

  echo "Installing mise $required_mise_version"
  curl -fsSL https://mise.run | MISE_VERSION="$required_mise_version" MISE_INSTALL_HELP=0 sh
  export PATH="${HOME}/.local/bin:${PATH}"
  current_mise_version="$(mise --version 2>/dev/null | awk '{print $1}' | sed 's/^v//' || true)"
fi

if [[ "$current_mise_version" != "$required_mise_version" ]]; then
  echo "mise $required_mise_version installation failed or installed a different version." >&2
  exit 1
fi

if [[ "${CODEX_CLOUD_PERSIST_BASHRC:-1}" == "1" ]]; then
  bashrc="${HOME}/.bashrc"
  marker="# iskw-boiler Codex Cloud mise path"

  touch "$bashrc"
  if ! grep -Fq "$marker" "$bashrc"; then
    {
      printf '\n%s\n' "$marker"
      printf 'export PATH="$HOME/.local/bin:$HOME/.local/share/mise/shims:$PATH"\n'
    } >> "$bashrc"
  fi
fi

mise trust --yes "$repo_dir/.mise.toml"

mise install --yes --cd "$repo_dir"
mise reshim --yes --cd "$repo_dir"

mise exec --yes --cd "$repo_dir" -- bash -eu -o pipefail -c 'cd frontend && pnpm install --frozen-lockfile'

mise exec --yes --cd "$repo_dir" -- bash -eu -o pipefail -c '
mise --version
java -version
node --version
pnpm --version
just --version
gh --version | head -n 1
sbt --script-version
'

echo "Codex Cloud setup completed"
