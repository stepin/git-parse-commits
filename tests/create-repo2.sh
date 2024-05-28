#!/usr/bin/env bash
set -eEuo pipefail
cd "$(dirname "$0")"

function commit() {
    touch $1
    git add --all
    git commit -a -m "$(echo -e "$2")"
}

rm -rf repo2 || true
mkdir repo2
cd repo2

git init .
git config user.name "Author One"
git config user.email "AuthorOne@example.com"

commit "1-1" "initial"
git tag component1-0.1.0 -m"One commit case c1"
git tag component2-0.1.0 -m"One commit case c2"

commit "2-1" "- fix: now it's better for sure"
commit "2-2" "feat(component1): best feature ever"
commit "2-3" "chore(component1): even better"
git tag component1-0.2.0 -m"Several commits case"

commit "2.1-1" "- fix: now it's better for sure"
commit "2.1-2" "- feat(component2): best feature ever"
commit "2.1-3" "- chore(component2): even better"
git tag component2-0.2.1 -m"Several commits as list case"

commit "3-1" "feat: feature1\nfix: fix1\nrefactor:refactor1\ndocs: docs1\nperf: perf1\nchore: chore1\nci: ci1\nbuild: build1\nstyle: style1\ntest: test1\nskip: skip1\nwip: wip1\nminor: minor1\ncustom: custom1"
git tag component1-0.3.0 -m"All types case"

git checkout -b dev/0.4.0-branch
commit "4-1" "- fix: now it's better for sure"
commit "4-2" "feat(component1): best feature ever"
commit "4-3" "chore(component1): even better"
git checkout main
git merge --no-ff --no-edit dev/0.4.0-branch
git tag component2-0.4.0 -m"Several commits with merge commit"

git co -b dev/current-ticket
commit "12-1" "feat(component1): something commited"
