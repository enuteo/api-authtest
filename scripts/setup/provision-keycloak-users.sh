#!/usr/bin/env bash
set -Eeuo pipefail

KEYCLOAK_SERVER="${KEYCLOAK_SERVER:-http://keycloak:8080}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-authtest}"
KEYCLOAK_ADMIN_USERNAME="${KEYCLOAK_ADMIN_USERNAME:-master}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:?Set KEYCLOAK_ADMIN_PASSWORD}"
KEYCLOAK_USER_USERNAME="${KEYCLOAK_USER_USERNAME:-user}"
KEYCLOAK_USER_PASSWORD="${KEYCLOAK_USER_PASSWORD:?Set KEYCLOAK_USER_PASSWORD}"
KEYCLOAK_TEST_ADMIN_USERNAME="${KEYCLOAK_TEST_ADMIN_USERNAME:-admin}"
KEYCLOAK_TEST_ADMIN_PASSWORD="${KEYCLOAK_TEST_ADMIN_PASSWORD:?Set KEYCLOAK_TEST_ADMIN_PASSWORD}"
KEYCLOAK_NOTUSER_USERNAME="${KEYCLOAK_NOTUSER_USERNAME:-notuser}"
KEYCLOAK_NOTUSER_PASSWORD="${KEYCLOAK_NOTUSER_PASSWORD:-notuserpass}"

authenticated=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if /opt/keycloak/bin/kcadm.sh config credentials \
    --server "$KEYCLOAK_SERVER" \
    --realm master \
    --user "$KEYCLOAK_ADMIN_USERNAME" \
    --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1; then
    authenticated=true
    break
  fi
  sleep 2
done

if [[ "$authenticated" != "true" ]]; then
  echo "Could not authenticate to Keycloak to provision test-user passwords." >&2
  exit 1
fi

set_test_user_password() {
  local username="$1"
  local password="$2"
  local email="$3"
  local first_name="$4"
  local last_name="$5"
  local user_id

  user_id=$(/opt/keycloak/bin/kcadm.sh get users \
    -r "$KEYCLOAK_REALM" \
    -q "username=$username" \
    -q exact=true \
    --fields id \
    --format csv \
    --noquotes)
  if [[ -z "$user_id" ]]; then
    /opt/keycloak/bin/kcadm.sh create users \
      -r "$KEYCLOAK_REALM" \
      -s "username=$username" \
      -s enabled=true >/dev/null
    user_id=$(/opt/keycloak/bin/kcadm.sh get users \
      -r "$KEYCLOAK_REALM" \
      -q "username=$username" \
      -q exact=true \
      --fields id \
      --format csv \
      --noquotes)
  fi
  if [[ -z "$user_id" ]]; then
    echo "Could not create or find Keycloak test user '$username'." >&2
    exit 1
  fi

  /opt/keycloak/bin/kcadm.sh update "users/$user_id" \
    -r "$KEYCLOAK_REALM" \
    -s "email=$email" \
    -s "firstName=$first_name" \
    -s "lastName=$last_name" \
    -s emailVerified=true \
    -s enabled=true >/dev/null

  /opt/keycloak/bin/kcadm.sh set-password \
    -r "$KEYCLOAK_REALM" \
    --username "$username" \
    --new-password "$password" >/dev/null
}

set_test_user_password "$KEYCLOAK_USER_USERNAME" "$KEYCLOAK_USER_PASSWORD" \
  user@example.test Test User
set_test_user_password "$KEYCLOAK_TEST_ADMIN_USERNAME" "$KEYCLOAK_TEST_ADMIN_PASSWORD" \
  admin@example.test Test Admin
set_test_user_password "$KEYCLOAK_NOTUSER_USERNAME" "$KEYCLOAK_NOTUSER_PASSWORD" \
  notuser@example.test Test "No Roles"

user_id=$(/opt/keycloak/bin/kcadm.sh get users \
  -r "$KEYCLOAK_REALM" \
  -q "username=$KEYCLOAK_NOTUSER_USERNAME" \
  -q exact=true \
  --fields id \
  --format csv \
  --noquotes)
realm_roles=$(/opt/keycloak/bin/kcadm.sh get "users/$user_id/role-mappings/realm" \
  -r "$KEYCLOAK_REALM" \
  --fields name \
  --format csv \
  --noquotes)
default_role="default-roles-$KEYCLOAK_REALM"
case $'\n'"$realm_roles"$'\n' in
  *$'\n'"$default_role"$'\n'*)
    /opt/keycloak/bin/kcadm.sh remove-roles \
      -r "$KEYCLOAK_REALM" \
      --uid "$user_id" \
      --rolename "$default_role" >/dev/null
    ;;
esac

echo "Keycloak test-user credentials provisioned; '$KEYCLOAK_NOTUSER_USERNAME' has no application roles."
