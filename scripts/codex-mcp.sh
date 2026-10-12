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
if ! project_dir="$(cd "$2" 2>/dev/null && pwd -P)"; then
  echo "project directory not found: $2" >&2
  exit 1
fi

port="${METALS_MCP_PORT:-39000}"
config_dir="${project_dir}/.codex"
config_file="${config_dir}/config.toml"
binary_file="${project_dir}/.coursier/bin/metals-mcp"
log_dir="${project_dir}/logs"
log_file="${log_dir}/metals-mcp.log"
pid_file="${log_dir}/metals-mcp.pid"
sbt_bsp_file="${project_dir}/.bsp/sbt.json"
metals_db_file="${project_dir}/.metals/metals.mv.db"
current_uid="$(id -u)"

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

process_metadata() {
  local pid="$1"
  local cmdline_file="/proc/${pid}/cmdline"
  local process_uid
  local arg
  local previous_arg=""
  local workspace=""
  local has_binary=0
  local has_http_transport=0
  local has_expected_port=0

  is_positive_pid "${pid}" || return 1
  [ -r "${cmdline_file}" ] || return 1

  process_uid="$(stat -c %u "/proc/${pid}" 2>/dev/null)" || return 1
  [ "${process_uid}" = "${current_uid}" ] || return 1

  while IFS= read -r -d '' arg; do
    if [ "${arg}" = "${binary_file}" ]; then
      has_binary=1
    fi

    if [ "${previous_arg}" = "--workspace" ]; then
      workspace="${arg}"
    elif [ "${previous_arg}" = "--transport" ] && [ "${arg}" = "http" ]; then
      has_http_transport=1
    elif [ "${previous_arg}" = "--port" ] && [ "${arg}" = "${port}" ]; then
      has_expected_port=1
    fi

    previous_arg="${arg}"
  done <"${cmdline_file}"

  [ "${has_binary}" -eq 1 ] || return 1
  [ "${has_http_transport}" -eq 1 ] || return 1
  [ "${has_expected_port}" -eq 1 ] || return 1
  [ "${workspace}" = "${project_dir}" ] || return 1

  printf '%s\n' "${pid}"
}

find_expected_server() {
  local pid

  while read -r pid; do
    if process_metadata "${pid}" >/dev/null; then
      printf '%s\n' "${pid}"
      return 0
    fi
  done < <(pgrep -u "${current_uid}" -f metals-mcp 2>/dev/null || true)

  return 1
}

port_is_listening() {
  [ -n "$(ss -H -ltn "sport = :${port}" 2>/dev/null)" ]
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

prepare_bloop() {
  if [ ! -f "${sbt_bsp_file}" ]; then
    return 0
  fi

  rm -f "${sbt_bsp_file}" "${metals_db_file}"
  echo "Removed generated sbt BSP selection; Metals will import the build for Bloop."
}

stop_managed_process() {
  local pid="$1"

  if ! process_metadata "${pid}" >/dev/null; then
    echo "Metals MCP process changed before it could be stopped (pid: ${pid})" >&2
    return 1
  fi

  kill "${pid}"
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      return 0
    fi
    sleep 1
  done

  echo "Metals MCP did not stop within 10 seconds (pid: ${pid})" >&2
  return 1
}

start_server() {
  local pid

  mkdir -p "${log_dir}"

  if pid="$(find_expected_server)"; then
    printf '%s\n' "${pid}" >"${pid_file}"
    echo "Metals MCP is already running (pid: ${pid}, workspace: ${project_dir}, port: ${port})"
    return 0
  fi

  if port_is_listening; then
    echo "Port ${port} is already in use; no process was stopped." >&2
    return 1
  fi

  rm -f "${pid_file}"
  write_config

  if [ ! -x "${binary_file}" ]; then
    echo "metals-mcp が見つかりません。mise metals-mcp-install を実行してください。" >&2
    return 1
  fi

  if ! command -v setsid >/dev/null 2>&1; then
    echo "setsid が見つかりません。バックグラウンド起動を安全に分離できません。" >&2
    return 1
  fi

  prepare_bloop

  PATH="${project_dir}/.coursier/bin:${PATH}" nohup setsid -f "${binary_file}" \
    --workspace "${project_dir}" \
    --transport http \
    --port "${port}" \
    </dev/null \
    >"${log_file}" 2>&1 &

  printf '%s\n' "$!" >"${pid_file}"
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if pid="$(find_expected_server)" && port_is_listening; then
      printf '%s\n' "${pid}" >"${pid_file}"
      echo "Metals MCP started (pid: ${pid}, workspace: ${project_dir}, port: ${port})"
      echo "log: ${log_file}"
      return 0
    fi

    sleep 1
  done

  if pid="$(find_expected_server)"; then
    stop_managed_process "${pid}" || true
  fi
  rm -f "${pid_file}"
  echo "Metals MCP did not listen on port ${port} within 10 seconds. See ${log_file}" >&2
  return 1
}

stop_server() {
  local pid

  if ! pid="$(find_expected_server)"; then
    rm -f "${pid_file}"
    echo "Metals MCP is not running for workspace: ${project_dir}"
    return 0
  fi

  stop_managed_process "${pid}"
  rm -f "${pid_file}"
  echo "Metals MCP stopped (pid: ${pid}, workspace: ${project_dir})"
}

status_server() {
  local pid

  echo "Metals MCP"
  echo "  port: ${port}"
  echo "  workspace: ${project_dir}"
  echo "  build server: Bloop"

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

  if pid="$(find_expected_server)"; then
    echo "  process: running (pid: ${pid})"
  elif port_is_listening; then
    echo "  process: port ${port} is used by another process"
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
