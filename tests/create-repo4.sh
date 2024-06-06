#!/usr/bin/env bash
set -eEuo pipefail
cd "$(dirname "$0")"

function commit() {
    touch $1
    git add --all
    git commit -a -m "$(echo -e "$2")"
}

rm -rf repo4 || true
mkdir repo4
cd repo4

git init .
git config user.name "Author One"
git config user.email "AuthorOne@example.com"

commit "1-1" "- fix: now it's better for sure"
commit "1-2" "feat(compontent1): best feature ever"
commit "1-3" "chore(compontent1): even better"
