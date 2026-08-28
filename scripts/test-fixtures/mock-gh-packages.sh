#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  *'/packages?package_type=maven'*)
    case "${MOCK_PACKAGE_STATE:-absent}" in
      present|malformed-versions) printf '[[{"name":"com.getair.video"}]]\n' ;;
      malformed-packages) printf 'not-json\n' ;;
      *) printf '[[]]\n' ;;
    esac ;;
  *'/packages/maven/'*'/versions?'*)
    case "${MOCK_PACKAGE_STATE:-absent}" in
      present) printf '[[{"id":42,"name":"1.2.3"}]]\n' ;;
      malformed-versions) printf 'not-json\n' ;;
      *) printf '[[]]\n' ;;
    esac ;;
  *) exit 2 ;;
esac
