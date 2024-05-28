#!/usr/bin/env bash
set -eEuo pipefail
cd "$(dirname "$0")"

function commit() {
    touch $1
    git add --all
    git commit -a -m "$(echo -e "$2")"
}

rm -rf repo || true
mkdir repo
cd repo

git init .
git config user.name "Author One"
git config user.email "AuthorOne@example.com"

commit "1-1" "initial"
git tag 0.1.0 -m"One commit case"

commit "2-1" "- fix: now it's better for sure"
commit "2-2" "feat(compontent1): best feature ever"
commit "2-3" "chore(compontent1): even better"
git tag 0.2.0 -m"Several commits case"

commit "2.1-1" "- fix: now it's better for sure"
commit "2.1-2" "- feat(compontent1): best feature ever"
commit "2.1-3" "- chore(compontent1): even better"
git tag 0.2.1 -m"Several commits as list case"

commit "3-1" "feat: feature1\nfix: fix1\nrefactor:refactor1\ndocs: docs1\nperf: perf1\nchore: chore1\nci: ci1\nbuild: build1\nstyle: style1\ntest: test1\nskip: skip1\nwip: wip1\nminor: minor1\ncustom: custom1"
git tag 0.3.0 -m"All types case"

git checkout -b dev/0.4.0-branch
commit "4-1" "- fix: now it's better for sure"
commit "4-2" "feat(compontent1): best feature ever"
commit "4-3" "chore(compontent1): even better"
git checkout main
git merge --no-ff --no-edit dev/0.4.0-branch
git tag 0.4.0 -m"Several commits with merge commit"

git checkout -b dev/0.5.0-branch
commit "5-1" "- fix: now it's better for sure\nfeat(compontent1): best feature ever\nchore(compontent1): even better"
git checkout main
git merge --no-ff --no-edit dev/0.5.0-branch
git tag 0.5.0 -m"Like squash with merge commit"

commit "6-1" "light tag case"
git tag 0.6.0

commit "7-1" "something, WIP"
commit "7-2" "something 2"
git tag 0.7.0

commit "8-1" "something, WIP\n\nBREAKING CHANGE: something1"
git tag 1.8.0

commit "9-1" "fix!: major change"
git tag 2.9.0

commit "10-1" "fix(component1)!: major change, MY-123"
git tag 2.10.0

commit "11-1" "- fix: major change\nfeat(component1):feature1\n\nsome description\n- still description\n\nnote1: value1"
git tag 2.11.0

git co -b dev/current-ticket
commit "12-1" "something commited"
