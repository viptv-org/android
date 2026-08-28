#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export GH_TOKEN=test-token GITHUB_REPOSITORY=air-tv/video GH_BIN="$script_dir/test-fixtures/mock-gh-packages.sh"
MOCK_PACKAGE_STATE=absent "$script_dir/assert-release-package-version-available.sh" 1.2.3 >/dev/null
if MOCK_PACKAGE_STATE=present "$script_dir/assert-release-package-version-available.sh" 1.2.3 >/dev/null 2>&1; then exit 1; fi
if MOCK_PACKAGE_STATE=malformed-packages "$script_dir/assert-release-package-version-available.sh" 1.2.3 >/dev/null 2>&1; then exit 1; fi
if MOCK_PACKAGE_STATE=malformed-versions "$script_dir/assert-release-package-version-available.sh" 1.2.3 >/dev/null 2>&1; then exit 1; fi
MOCK_PACKAGE_STATE=absent "$script_dir/cleanup-maven-release.sh" 1.2.3 >/dev/null
if MOCK_PACKAGE_STATE=present "$script_dir/cleanup-maven-release.sh" 1.2.3 >/dev/null 2>&1; then exit 1; fi
if MOCK_PACKAGE_STATE=malformed-packages "$script_dir/cleanup-maven-release.sh" 1.2.3 >/dev/null 2>&1; then exit 1; fi
if MOCK_PACKAGE_STATE=malformed-versions "$script_dir/cleanup-maven-release.sh" 1.2.3 >/dev/null 2>&1; then exit 1; fi
workflow="$script_dir/../.github/workflows/publish.yml"
for job in publish-linux publish-apple publish-windows root-metadata; do
  grep -Fq "needs.$job.result == 'cancelled'" "$workflow"
done
if grep -Fq 'always() && failure()' "$workflow"; then exit 1; fi
echo "Video release package fixtures passed"
