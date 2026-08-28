#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"
repository="${GITHUB_REPOSITORY:-}"
owner="${repository%%/*}"
repo_name="${repository#*/}"
gh_bin="${GH_BIN:-gh}"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ ! "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Cleanup version must be stable MAJOR.MINOR.PATCH" >&2; exit 1
fi
if [[ -z "${GH_TOKEN:-}" || -z "$owner" || "$owner" == "$repository" || -z "$repo_name" ]]; then
  echo "Cleanup requires GH_TOKEN and GITHUB_REPOSITORY" >&2; exit 1
fi
manual_recovery() {
  cat >&2 <<EOF
Automatic package recovery did not complete.
Manually delete every Maven package version $version linked to $repository at:
https://github.com/orgs/$owner/packages?repo_name=$repo_name
Do not rerun the release workflow until all partial versions are gone.
EOF
}
if ! packages_json="$("$gh_bin" api --paginate --slurp "/orgs/$owner/packages?package_type=maven&per_page=100")"; then
  echo "Failed to enumerate Maven packages for $repository" >&2; manual_recovery; exit 1
fi
if ! jq -e '
  type == "array" and
  all(.[]; type == "array") and
  all(.[]; all(.[]; type == "object" and (.name | type == "string")))
' <<<"$packages_json" >/dev/null; then
  echo "GitHub returned malformed Maven package metadata for $repository" >&2; manual_recovery; exit 1
fi
if ! published_text="$(jq -r '.[][] | .name' <<<"$packages_json" | sort -u)"; then
  echo "Failed to parse Maven package names for $repository" >&2; manual_recovery; exit 1
fi
if ! expected_text="$("$script_dir/release-maven-package-names.sh")"; then
  echo "Failed to enumerate expected video Maven packages" >&2; manual_recovery; exit 1
fi
published_packages=()
if [[ -n "$published_text" ]]; then mapfile -t published_packages <<<"$published_text"; fi
mapfile -t expected_packages <<<"$expected_text"
if [[ "${#expected_packages[@]}" -ne 12 ]]; then
  echo "Expected exactly twelve video Maven package names" >&2; manual_recovery; exit 1
fi
packages=()
for package in "${expected_packages[@]}"; do
  if printf '%s\n' "${published_packages[@]}" | grep -Fqx -- "$package"; then packages+=("$package"); fi
done
failed=0
for package in "${packages[@]}"; do
  encoded_package="$(jq -rn --arg value "$package" '$value|@uri')"
  endpoint="/orgs/$owner/packages/maven/$encoded_package/versions?per_page=100"
  if ! versions_json="$("$gh_bin" api --paginate --slurp "$endpoint")"; then
    echo "Failed to enumerate versions for $package" >&2; failed=1; continue
  fi
  if ! jq -e '
    type == "array" and
    all(.[]; type == "array") and
    all(.[]; all(.[]; type == "object" and (.name | type == "string") and (.id | type == "number")))
  ' <<<"$versions_json" >/dev/null; then
    echo "GitHub returned malformed Maven version metadata for $package" >&2; failed=1; continue
  fi
  if ! ids_text="$(jq -r --arg version "$version" '.[][] | select(.name == $version) | .id' <<<"$versions_json")"; then
    echo "Failed to parse Maven version identifiers for $package" >&2; failed=1; continue
  fi
  ids=()
  if [[ -n "$ids_text" ]]; then mapfile -t ids <<<"$ids_text"; fi
  for id in "${ids[@]}"; do
    echo "Deleting $package version $version"
    if ! "$gh_bin" api --method DELETE "/orgs/$owner/packages/maven/$encoded_package/versions/$id"; then failed=1; fi
  done
done
for package in "${packages[@]}"; do
  encoded_package="$(jq -rn --arg value "$package" '$value|@uri')"
  if ! versions_json="$("$gh_bin" api --paginate --slurp "/orgs/$owner/packages/maven/$encoded_package/versions?per_page=100")"; then failed=1; continue; fi
  if ! jq -e '
    type == "array" and
    all(.[]; type == "array") and
    all(.[]; all(.[]; type == "object" and (.name | type == "string")))
  ' <<<"$versions_json" >/dev/null; then
    echo "GitHub returned malformed Maven version metadata while verifying $package" >&2; failed=1; continue
  fi
  if jq -e --arg version "$version" 'any(.[][]; .name == $version)' <<<"$versions_json" >/dev/null; then
    echo "Package cleanup remains incomplete for $package version $version" >&2; failed=1
  fi
done
if (( failed != 0 )); then manual_recovery; exit 1; fi
echo "Package recovery is complete for $repository version $version"
