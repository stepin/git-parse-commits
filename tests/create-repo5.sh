#!/usr/bin/env bash
set -eEuo pipefail
cd "$(dirname "$0")"

function commit() {
    touch $1
    git add --all
    git commit -a -m "$(echo -e "$2")"
}

rm -rf repo5 || true
mkdir repo5
cd repo5

git init .
git config user.name "Author One"
git config user.email "AuthorOne@example.com"

commit "1-1" "initial"
commit "1-2" "- fix: now it's better for sure"
git tag test1 -m"Non semver tag"

commit "2-1" "feat(compontent1): best feature ever"
commit "2-2" "chore(compontent1): even better"
git tag 0.2.0 -m"Several commits case"

commit "2.1-1" "- fix: now it's better for sure"
commit "2.1-2" "- feat(compontent1): best feature ever"
commit "2.1-3" "- chore(compontent1): even better"
commit "2.1-4" "Better"
commit "2.1-5" "minor\n"
commit "2.1-6" "fix"
commit "2.1-7" "fixes"
git tag 0.2.0-my-prelease.1 -m"Several commits as list case"

commit "3-1" "My title\n\n- feat:feature1\nhotfix: fix1\nrefactor:refactor1\ndocs: docs1\nperf: perf1\nchore: chore1\nci: ci1\nbuild: build1\nstyle: style1\ntest: test1\nskip: skip1\nwip: wip1\nminor: minor1\ncustom: custom1"
