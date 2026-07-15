#!/usr/bin/env bash

set -euo pipefail

usage() {
  echo "usage: $0 <config|start|stop|status> <project-dir>" >&2
}

if [ "$#" -ne 2 ]; then
  usage
  exit 1
fi

command="$1"
project_dir="$2"
port="${METALS_MCP_PORT:-39000}"
config_dir="${project_dir}/.codex"
config_file="${config_dir}/config.toml"
binary_file="${project_dir}/.coursier/bin/metals-mcp"
log_dir="${project_dir}/logs"
log_file="${log_dir}/metals-mcp.log"
pid_file="${log_dir}/metals-mcp.pid"

ensure_project_dir() {
  if [ ! -d "${project_dir}" ]; then
    echo "project directory not found: ${project_dir}" >&2
    exit 1
  fi
}

read_pid_file() {
  cat "${pid_file}"
}

is_positive_pid() {
  local pid="$1"

  case "${pid}" in
    "" | *[!0-9]* | 0 | 0*)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

process_cmdline_matches() {
  local pid="$1"
  local cmdline_file="/proc/${pid}/cmdline"

  if [ -r "${cmdline_file}" ]; then
    local arg
    local previous_arg=""
    local has_binary=0
    local has_workspace=0

    while IFS= read -r -d '' arg; do
      if [ "${arg}" = "${binary_file}" ]; then
        has_binary=1
      fi
      if [ "${previous_arg}" = "--workspace" ] && [ "${arg}" = "${project_dir}" ]; then
        has_workspace=1
      fi
      previous_arg="${arg}"
    done <"${cmdline_file}"

    [ "${has_binary}" -eq 1 ] && [ "${has_workspace}" -eq 1 ]
    return
  fi

  local args
  args="$(ps -p "${pid}" -o args= 2>/dev/null || true)"
  case "${args}" in
    *"${binary_file}"*"--workspace ${project_dir}"*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_expected_metals_mcp_process() {
  local pid="$1"

  is_positive_pid "${pid}" &&
    kill -0 "${pid}" 2>/dev/null &&
    process_cmdline_matches "${pid}"
}

find_expected_metals_mcp_pid() {
  local pid

  while read -r pid; do
    if is_expected_metals_mcp_process "${pid}"; then
      echo "${pid}"
      return 0
    fi
  done < <(ps -eo pid= 2>/dev/null || true)

  return 1
}

write_tool_approval_config() {
  local tool
  local auto_approved_tools=(
    compile_module
    compile-module
    compile_file
    compile-file
    compile_full
    compile-full
    list_modules
    list-modules
    get_docs
    get-docs
    get_source
    get-source
    get_usages
    get-usages
    glob_search
    glob-search
    typed_glob_search
    typed-glob-search
    inspect
    list_scalafix_rules
    list-scalafix-rules
  )

  for tool in "${auto_approved_tools[@]}"; do
    cat >>"${config_file}" <<EOF

[mcp_servers.metals_mcp.tools.${tool}]
approval_mode = "approve"
EOF
  done
}

write_config() {
  ensure_project_dir
  mkdir -p "${config_dir}"
  cat >"${config_file}" <<EOF
[sandbox_workspace_write]
network_access = false

[mcp_servers.metals_mcp]
url = "http://127.0.0.1:${port}/mcp"
enabled = true
startup_timeout_sec = 30
tool_timeout_sec = 120
EOF
  write_tool_approval_config
  echo "generated: ${config_file} (metals_mcp: http://127.0.0.1:${port}/mcp)"
}

start_server() {
  ensure_project_dir

  mkdir -p "${log_dir}"

  if [ -f "${pid_file}" ]; then
    pid="$(read_pid_file)"
    if is_expected_metals_mcp_process "${pid}"; then
      echo "Metals MCP is already running (pid: ${pid}, port: ${port})"
      exit 0
    fi
    rm -f "${pid_file}"
  fi

  if pid="$(find_expected_metals_mcp_pid)"; then
    echo "${pid}" >"${pid_file}"
    echo "Metals MCP is already running (pid: ${pid}, port: ${port})"
    exit 0
  fi

  write_config

  if [ ! -x "${binary_file}" ]; then
    echo "metals-mcp が見つかりません。mise metals-mcp-install を実行してください。" >&2
    exit 1
  fi

  PATH="${project_dir}/.coursier/bin:${PATH}" nohup "${binary_file}" \
    --workspace "${project_dir}" \
    --transport http \
    --port "${port}" \
    --default-bsp-to-build-tool \
    >"${log_file}" 2>&1 &

  pid="$!"
  echo "${pid}" >"${pid_file}"
  sleep 2

  if is_expected_metals_mcp_process "${pid}"; then
    echo "Metals MCP started (pid: ${pid}, port: ${port})"
    echo "log: ${log_file}"
  else
    rm -f "${pid_file}"
    echo "Metals MCP failed to start. See ${log_file}" >&2
    exit 1
  fi
}

stop_server() {
  if [ ! -f "${pid_file}" ]; then
    echo "Metals MCP is not running"
    exit 0
  fi

  pid="$(read_pid_file)"
  if ! is_positive_pid "${pid}"; then
    rm -f "${pid_file}"
    echo "Removed invalid Metals MCP pid file"
    exit 0
  fi

  if ! kill -0 "${pid}" 2>/dev/null; then
    rm -f "${pid_file}"
    echo "Removed stale Metals MCP pid file (pid: ${pid})"
    exit 0
  fi

  if ! process_cmdline_matches "${pid}"; then
    rm -f "${pid_file}"
    echo "Removed stale Metals MCP pid file (pid belongs to another process: ${pid})"
    exit 0
  fi

  kill "${pid}"
  for _ in 1 2 3 4 5; do
    if ! is_expected_metals_mcp_process "${pid}"; then
      break
    fi
    sleep 1
  done

  if is_expected_metals_mcp_process "${pid}"; then
    echo "Metals MCP did not stop within 5 seconds (pid: ${pid})" >&2
    exit 1
  fi

  rm -f "${pid_file}"
  echo "Metals MCP stopped (pid: ${pid})"
}

status_server() {
  ensure_project_dir
  echo "Metals MCP"
  echo "  port: ${port}"

  if [ -x "${binary_file}" ]; then
    echo "  binary: ${binary_file}"
  else
    echo "  binary: missing (${binary_file})"
  fi

  if [ -f "${config_file}" ]; then
    if grep -Fq "http://127.0.0.1:${port}/mcp" "${config_file}"; then
      echo "  config: ${config_file}"
    else
      echo "  config: ${config_file} (port differs; rerun gen-codex-config)"
    fi
  else
    echo "  config: missing (${config_file})"
  fi

  if [ -f "${pid_file}" ]; then
    pid="$(read_pid_file)"
    if is_expected_metals_mcp_process "${pid}"; then
      echo "  process: running (pid: ${pid})"
    elif ! is_positive_pid "${pid}"; then
      echo "  process: not running (invalid pid file: ${pid_file})"
    else
      echo "  process: not running (stale pid file: ${pid_file})"
    fi
  else
    echo "  process: not running"
  fi
}

case "${command}" in
  config)
    write_config
    ;;
  start)
    start_server
    ;;
  stop)
    stop_server
    ;;
  status)
    status_server
    ;;
  *)
    usage
    exit 1
    ;;
esac
