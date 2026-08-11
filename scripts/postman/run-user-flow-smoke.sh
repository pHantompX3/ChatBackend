#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${WL_CHAT_FLOW_BASE_URL:-${1:-http://localhost:8080}}"
TMP_ROOT="${WL_CHAT_FLOW_TMPDIR:-$(mktemp -d "${TMPDIR:-/tmp}/wl-chat-flow-smoke.XXXXXX")}"
KEEP_TMP="${WL_CHAT_KEEP_FLOW_TMP:-false}"
TMP_CREATED_BY_SCRIPT=false

if [[ -z "${WL_CHAT_FLOW_TMPDIR:-}" ]]; then
  TMP_CREATED_BY_SCRIPT=true
elif [[ ! -d "${TMP_ROOT}" ]]; then
  mkdir -p "${TMP_ROOT}"
  TMP_CREATED_BY_SCRIPT=true
fi

mkdir -p "${TMP_ROOT}"

success=false
cleanup() {
  if [[ "${success}" == "true" && "${KEEP_TMP}" != "true" && "${TMP_CREATED_BY_SCRIPT}" == "true" ]]; then
    rm -rf "${TMP_ROOT}"
  else
    echo "Flow artifacts kept at: ${TMP_ROOT}"
  fi
}
trap cleanup EXIT

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq

log() {
  echo "[flow-smoke] $*"
}

fail() {
  echo "[flow-smoke] ERROR: $*" >&2
  exit 1
}

json_get() {
  local file="$1"
  local jq_expr="$2"
  jq -er "${jq_expr}" "${file}" 2>/dev/null
}

request_json() {
  local step="$1"
  local method="$2"
  local path="$3"
  local expected_status="$4"
  local payload="$5"
  local bearer_token="${6:-}"

  local prefix="${TMP_ROOT}/${step}"
  local body_file="${prefix}.body.json"
  local headers_file="${prefix}.headers.txt"
  local status_file="${prefix}.status.txt"

  local url="${BASE_URL}${path}"
  local curl_args=(
    -sS
    -X "${method}"
    -D "${headers_file}"
    -o "${body_file}"
    -H "Accept: application/json"
    "${url}"
  )

  if [[ -n "${payload}" ]]; then
    curl_args+=(
      -H "Content-Type: application/json"
      --data "${payload}"
    )
  fi

  if [[ -n "${bearer_token}" ]]; then
    curl_args+=(-H "Authorization: Bearer ${bearer_token}")
  fi

  local status
  status="$(curl "${curl_args[@]}" -w "%{http_code}")"
  printf '%s\n' "${status}" >"${status_file}"

  log "${step}: ${method} ${path} -> ${status}"

  if [[ "${status}" != "${expected_status}" ]]; then
    echo "--- ${step} request ---" >&2
    echo "${method} ${url}" >&2
    if [[ -n "${payload}" ]]; then
      echo "payload=${payload}" >&2
    fi
    echo "--- ${step} response headers ---" >&2
    cat "${headers_file}" >&2 || true
    echo "--- ${step} response body ---" >&2
    cat "${body_file}" >&2 || true
    fail "${step} expected status ${expected_status} but got ${status}"
  fi
}

admin_username="runall-admin-$(date +%s)"
admin_password="${WL_CHAT_FLOW_ADMIN_PASSWORD:-AdminPassw0rd!}"
member_username="runall-member-$(date +%s)"
member_password="${WL_CHAT_FLOW_MEMBER_PASSWORD:-MemberPassw0rd!}"
conversation_id="runall-conv-$(date +%s)"
expires_at="2035-01-01T00:00:00Z"
followup_expires_at="2035-01-02T00:00:00Z"

log "Running flow against ${BASE_URL}"
log "Temp artifact directory: ${TMP_ROOT}"

request_json "01_bootstrap_admin" "POST" "/api/v1/bootstrap/admin" "200" "{\"username\":\"${admin_username}\",\"password\":\"${admin_password}\"}"
admin_user_id="$(json_get "${TMP_ROOT}/01_bootstrap_admin.body.json" '.userId')"

request_json "02_login_admin" "POST" "/api/v1/sessions" "200" "{\"username\":\"${admin_username}\",\"password\":\"${admin_password}\"}"
admin_session_token="$(json_get "${TMP_ROOT}/02_login_admin.body.json" '.token')"

request_json "03_create_invitation" "POST" "/api/v1/invitations" "200" "{\"actorUserId\":\"${admin_user_id}\",\"expiresAt\":\"${expires_at}\"}" "${admin_session_token}"
invitation_id="$(json_get "${TMP_ROOT}/03_create_invitation.body.json" '.invitationId')"
invitation_token="$(json_get "${TMP_ROOT}/03_create_invitation.body.json" '.invitationToken')"

request_json "04_redeem_invitation" "POST" "/api/v1/invitations/redeem" "200" "{\"invitationToken\":\"${invitation_token}\",\"username\":\"${member_username}\",\"password\":\"${member_password}\"}"
member_user_id="$(json_get "${TMP_ROOT}/04_redeem_invitation.body.json" '.userId')"

request_json "05_login_member" "POST" "/api/v1/sessions" "200" "{\"username\":\"${member_username}\",\"password\":\"${member_password}\"}"
session_id="$(json_get "${TMP_ROOT}/05_login_member.body.json" '.sessionId')"
session_token="$(json_get "${TMP_ROOT}/05_login_member.body.json" '.token')"

request_json "06_send_message" "POST" "/api/v1/messages" "501" "{\"conversationId\":\"${conversation_id}\",\"senderUserId\":\"${member_user_id}\",\"content\":\"Run-all smoke message ${session_id}\"}" "${session_token}"

request_json "07_list_messages" "GET" "/api/v1/conversations/${conversation_id}/messages?limit=10" "501" "" "${session_token}"

request_json "08_create_followup_invitation" "POST" "/api/v1/invitations" "200" "{\"actorUserId\":\"${admin_user_id}\",\"expiresAt\":\"${followup_expires_at}\"}" "${admin_session_token}"
followup_invitation_id="$(json_get "${TMP_ROOT}/08_create_followup_invitation.body.json" '.invitationId')"

request_json "09_revoke_followup_invitation" "POST" "/api/v1/invitations/${followup_invitation_id}/revoke" "204" "{\"actorUserId\":\"${admin_user_id}\"}" "${admin_session_token}"

request_json "10_logout_member" "POST" "/api/v1/sessions/logout" "204" "" "${session_token}"

request_json "11_verify_revoked_session_denied" "GET" "/api/v1/conversations/${conversation_id}/messages?limit=10" "401" "" "${session_token}"

summary_file="${TMP_ROOT}/flow-summary.json"
cat >"${summary_file}" <<JSON
{
  "baseUrl": "${BASE_URL}",
  "adminUsername": "${admin_username}",
  "adminUserId": "${admin_user_id}",
  "memberUsername": "${member_username}",
  "memberUserId": "${member_user_id}",
  "conversationId": "${conversation_id}",
  "sessionId": "${session_id}",
  "invitationId": "${invitation_id}",
  "followupInvitationId": "${followup_invitation_id}"
}
JSON

log "Flow smoke journey completed successfully."
log "Summary: ${summary_file}"

success=true
