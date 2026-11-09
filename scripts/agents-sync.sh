#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 0 ]; then
  echo "usage: $0" >&2
  exit 1
fi

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
source_dir="${project_dir}/ai-dotfiles/skills"
target_dir="${project_dir}/.agents/skills"

if [ ! -d "${source_dir}" ]; then
  echo "ai-dotfiles/skills が見つかりません。git submodule update --init --recursive を実行してください。" >&2
  exit 1
fi

mkdir -p "${project_dir}/.agents"
rm -rf "${target_dir}"
cp -a "${source_dir}" "${target_dir}"
echo "同期しました: ai-dotfiles/skills -> .agents/skills"
