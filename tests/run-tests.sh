#!/usr/bin/env bash
set -eEuo pipefail
cd "$(dirname "$0")"
set -x

cd repo

# main cases
../../git-parse-commits --help > ../results/X-help
../../git-parse-commits version > ../results/X-version
../../git-parse-commits -j version | jq . > ../results/X-version.json
../../git-parse-commits currentVersion > ../results/X-currentVersion
../../git-parse-commits -j currentVersion | jq . > ../results/X-currentVersion.json
../../git-parse-commits lastReleaseVersion > ../results/X-lastReleaseVersion
../../git-parse-commits -j lastReleaseVersion | jq . > ../results/X-lastReleaseVersion.json
../../git-parse-commits releaseVersion > ../results/X-releaseVersion
../../git-parse-commits -j releaseVersion | jq . > ../results/X-releaseVersion.json
../../git-parse-commits releaseNotes > ../results/X-releaseNotes.md
../../git-parse-commits -j releaseNotes | jq . > ../results/X-releaseNotes.json

# additional
set +o pipefail
REV_1="$(git log --oneline |head -n 3|tail -n 1|awk '{print $1;}')"
REV_2="$(git log --oneline |head -n 16|tail -n 1|awk '{print $1;}')"
set -o pipefail
../../git-parse-commits --json --initial-revision $REV_1 --last-revision $REV_2 releaseNotes | jq . > ../results/X-releaseNotes-range.json
../../git-parse-commits --initial-revision $REV_1 --last-revision $REV_2 releaseNotes > ../results/X-releaseNotes-range.md
../../git-parse-commits -l $REV_1 releaseNotes > ../results/X-l-releaseNotes.md

# all other cases
../../git-parse-commits -l 0.2.0 currentVersion  > ../results/0.2.0-currentVersion
../../git-parse-commits -l 0.2.0 releaseVersion > ../results/0.2.0-releaseVersion
../../git-parse-commits -l 0.2.0 releaseNotes > ../results/0.2.0-releaseNotes.md

../../git-parse-commits -l 0.2.1 releaseVersion > ../results/0.2.1-releaseVersion
../../git-parse-commits -l 0.2.1 releaseNotes > ../results/0.2.1-releaseNotes.md

../../git-parse-commits -l 0.3.0 releaseVersion > ../results/0.3.0-releaseVersion
../../git-parse-commits -l 0.3.0 releaseNotes > ../results/0.3.0-releaseNotes.md

../../git-parse-commits -l 0.4.0 releaseVersion > ../results/0.4.0-releaseVersion
../../git-parse-commits -l 0.4.0 releaseNotes > ../results/0.4.0-releaseNotes.md

../../git-parse-commits -l 0.5.0 releaseVersion > ../results/0.5.0-releaseVersion
../../git-parse-commits -l 0.5.0 releaseNotes > ../results/0.5.0-releaseNotes.md

../../git-parse-commits -l 0.6.0 releaseVersion > ../results/0.6.0-releaseVersion
../../git-parse-commits -l 0.6.0 releaseNotes > ../results/0.6.0-releaseNotes.md

../../git-parse-commits -l 0.7.0 releaseVersion > ../results/0.7.0-releaseVersion
../../git-parse-commits -l 0.7.0 releaseNotes > ../results/0.7.0-releaseNotes.md

../../git-parse-commits -j -l 1.8.0 releaseVersion > ../results/1.8.0-releaseVersion.json
../../git-parse-commits -l 1.8.0 releaseNotes > ../results/1.8.0-releaseNotes.md

../../git-parse-commits -j -l 2.9.0 releaseVersion > ../results/2.9.0-releaseVersion.json
../../git-parse-commits -l 2.9.0 releaseNotes > ../results/2.9.0-releaseNotes.md

../../git-parse-commits -j -l 2.10.0 releaseVersion > ../results/2.10.0-releaseVersion.json
../../git-parse-commits -l 2.10.0 releaseNotes > ../results/2.10.0-releaseNotes.md

../../git-parse-commits -j -l 2.11.0 releaseVersion > ../results/2.11.0-releaseVersion.json
../../git-parse-commits -l 2.11.0 releaseNotes > ../results/2.11.0-releaseNotes.md


# v1.2.3 cases (simple prefix for tags)
cd ../repo3
../../git-parse-commits --tag-prefix v currentVersion > ../results/v-currentVersion
../../git-parse-commits --tag-prefix v --tag currentVersion > ../results/v-currentVersionTag
../../git-parse-commits --tag-prefix v lastReleaseVersion > ../results/v-lastReleaseVersion
../../git-parse-commits --tag-prefix v --tag lastReleaseVersion > ../results/v-lastReleaseVersionTag
../../git-parse-commits --tag-prefix v releaseVersion > ../results/v-releaseVersion
../../git-parse-commits --tag-prefix v --tag releaseVersion > ../results/v-releaseVersionTag
../../git-parse-commits --tag-prefix v releaseNotes > ../results/v-releaseNotes.md
../../git-parse-commits --tag-prefix v -j releaseNotes | jq . > ../results/v-releaseNotes.json
../../git-parse-commits --tag-prefix v -j --tag releaseNotes | jq . > ../results/v-releaseNotesTag.json


# monorepo cases
cd ../repo2
../../git-parse-commits -s component1 --tag-prefix component1- currentVersion > ../results/mono1-currentVersion
../../git-parse-commits -s component1 --tag-prefix component1- lastReleaseVersion > ../results/mono1-lastReleaseVersion
../../git-parse-commits -s component1 --tag-prefix component1- releaseVersion > ../results/mono1-releaseVersion
../../git-parse-commits -s component1 --tag-prefix component1- releaseNotes > ../results/mono1-releaseNotes.md
../../git-parse-commits -s component2 --tag-prefix component2- --tag currentVersion > ../results/mono2-currentVersion
../../git-parse-commits -s component2 --tag-prefix component2- --tag lastReleaseVersion > ../results/mono2-lastReleaseVersion
../../git-parse-commits -s component2 --tag-prefix component2- --tag releaseVersion > ../results/mono2-releaseVersion
../../git-parse-commits -s component2 --tag-prefix component2- --tag releaseNotes > ../results/mono2-releaseNotes.md

# no tags case
cd ../repo4
../../git-parse-commits currentVersion > ../results/4-currentVersion
../../git-parse-commits lastReleaseVersion > ../results/4-lastReleaseVersion
../../git-parse-commits releaseVersion > ../results/4-releaseVersion
../../git-parse-commits -j releaseNotes | jq . > ../results/4-releaseNotes.json


#
# Diff should contain only dates/sha changes (any other difference means fails):
cd ../results
git diff .
