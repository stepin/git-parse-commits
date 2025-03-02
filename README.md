# Git Parse Commits
[![GitHub release](https://img.shields.io/github/release/stepin/git-parse-commits.svg)](https://github.com/stepin/git-parse-commits/releases) [![github license badge](https://img.shields.io/github/license/stepin/git-parse-commits)](https://github.com/stepin/git-parse-commits)

## Intro

When we create release in Gitea / Gitlab / Github we need to fill 4 fields:
- Release name
- Tag name
- Release description
- Tag commit message

For releases there are 2 main cases: pre-releases and releases.

Yes, it's not that hard to fill it every time automatically but it anyway takes time,
and it's an error-prone process.

So, this script is introduced to automate this task. Some key points:
- Human decides what will be in release note but decision point is moved to MR merge time (when
we specify MR commit message)
- Human decides when release should happen (start manual job)
- If something is not working it's still possible to create release from UI without these CI jobs


## Features

- Detects version of current (old) release, next release or pre-release from tags and tag descriptions.
- Creates release notes in Markdown and plain-text formats
- Supports multiline conventional commits (multiple lines to changelog from 1 commit)
- Supports semver tags (like `1.2.3` or `1.2.3-my-pre-release.1`)
- Supports monorepo tags (tags with prefixes like `componentX-1.2.3`)
- Supports tag prefixes like `v`
- JSON output for easier automation


## Details

This script is to be used in CICD pipelines to provide new version number
(bases on commit messages) and releases notes (also bases on commit messages).

Docker image: [stepin/git-parse-commits:latest](https://hub.docker.com/r/stepin/git-parse-commits)

### Example how to use with Docker

```shell
# it can be used as version for pre-preleases
docker run --rm -it -v "${PWD}:/git" -w /git --user "$(id -u)" stepin/git-parse-commits:2.3.0 currentVersion
# it can be used as version for releases
docker run --rm -it -v "${PWD}:/git" -w /git --user "$(id -u)" stepin/git-parse-commits:2.3.0 releaseVersion
# it's for plain-text release notes
docker run --rm -it -v "${PWD}:/git" -w /git --user "$(id -u)" stepin/git-parse-commits:2.3.0 releaseNotes --short
# it's for Markdown release notes
docker run --rm -it -v "${PWD}:/git" -w /git --user "$(id -u)" stepin/git-parse-commits:2.3.0 releaseNotes
```

### Example usage for Gitlab

```yaml
create_changelog:
  stage: "build"
  image:
      name: "stepin/git-parse-commits:2.3.0"
      entrypoint: [ "" ]
  variables:
      GIT_DEPTH: "0"
  script:
  - if $(git rev-parse --is-shallow-repository); then git fetch --unshallow ; fi
  - CURRENT_VERSION="$(git-parse-commits currentVersion)"
  - RELEASE_VERSION="$(git-parse-commits releaseVersion)"
  - echo "RELEASE_VERSION=$RELEASE_VERSION" >> relNotes.env
  - echo "CURRENT_VERSION=$CURRENT_VERSION" >> relNotes.env
  - cat relNotes.env
  - git-parse-commits releaseNotes | tee releaseNotes.md
  - git-parse-commits releaseNotes --short | tee releaseNotes.txt
  artifacts:
      reports:
          dotenv: relNotes.env
      paths:
      - releaseNotes.md
      - releaseNotes.txt
      expire_in: 1 day
  rules:
  - if: $CI_MERGE_REQUEST_IID
  - if: $CI_COMMIT_REF_NAME == "main" && $CI_PIPELINE_SOURCE != "schedule"
  - if: $CI_COMMIT_REF_NAME == "release/*" && $CI_PIPELINE_SOURCE != "schedule"
  - if: $CI_COMMIT_TAG =~ /^\d+\.\d+\.\d+$/
  needs: [ ]

create_changelog_prerelease:
  stage: "build"
  image:
    name: "stepin/git-parse-commits:2.3.0"
    entrypoint: [ "" ]
  variables:
    GIT_DEPTH: "0"
  script:
    - if $(git rev-parse --is-shallow-repository); then git fetch --unshallow ; fi
    - CURRENT_VERSION="$(git-parse-commits -pre currentVersion)"
    - RELEASE_VERSION="$(git-parse-commits -pre releaseVersion)"
    - echo "RELEASE_VERSION=$RELEASE_VERSION" >> relNotes.env
    - echo "CURRENT_VERSION=$CURRENT_VERSION" >> relNotes.env
    - cat relNotes.env
    - git-parse-commits -pre releaseNotes | tee releaseNotes.md
    - git-parse-commits -pre releaseNotes --short | tee releaseNotes.txt
  artifacts:
    reports:
      dotenv: relNotes.env
    paths:
      - releaseNotes.md
      - releaseNotes.txt
    expire_in: 1 day
  rules:
    - if: $CI_COMMIT_TAG =~ /^\d+\.\d+\.\d+-.*/
  needs: [ ]

release:
  stage: "release"
  image:
    name: registry.gitlab.com/gitlab-org/cli:latest
    entrypoint: [ "" ]
  before_script:
    - git config --global user.email "${GITLAB_USER_EMAIL}"
    - git config --global user.name "${GITLAB_USER_NAME}"
    - git remote remove tag-origin || true
    - git remote add tag-origin "https://MY_TOKEN_NAME:$MY_TAGS_PUSH_TOKEN@${CI_SERVER_HOST}/${CI_PROJECT_PATH}.git"
    - glab auth login --job-token $CI_JOB_TOKEN --hostname $CI_SERVER_HOST --api-protocol $CI_SERVER_PROTOCOL
  script:
    - echo "Release $RELEASE_VERSION"
    - git tag -F releaseNotes.txt "$RELEASE_VERSION"
    - git push tag-origin tag "$RELEASE_VERSION"
    - |
      glab release create "$RELEASE_VERSION" --name "Pre-release $RELEASE_VERSION" \
        --ref "$CI_COMMIT_SHA" \
        --notes-file "releaseNotes.md"
  rules:
    -   if: $CI_COMMIT_REF_NAME == "main" && $CI_PIPELINE_SOURCE != "schedule"
        when: manual
        allow_failure: true
    -   if: $CI_COMMIT_REF_NAME == "release/*" && $CI_PIPELINE_SOURCE != "schedule"
        when: manual
        allow_failure: true
    -   if: $CI_MERGE_REQUEST_IID
        when: manual
        allow_failure: true
  needs:
    - "create_changelog"

pre_release:
  stage: "release"
  image:
    name: registry.gitlab.com/gitlab-org/cli:latest
    entrypoint: [ "" ]
  before_script:
    - git config --global user.email "${GITLAB_USER_EMAIL}"
    - git config --global user.name "${GITLAB_USER_NAME}"
    - git remote remove tag-origin || true
    - git remote add tag-origin "https://MY_TOKEN_NAME:$MY_TAGS_PUSH_TOKEN@${CI_SERVER_HOST}/${CI_PROJECT_PATH}.git"
    - glab auth login --job-token $CI_JOB_TOKEN --hostname $CI_SERVER_HOST --api-protocol $CI_SERVER_PROTOCOL
  script:
    - echo "Release $CURRENT_VERSION"
    - git tag -F releaseNotes.txt "$CURRENT_VERSION"
    - git push tag-origin tag "$CURRENT_VERSION"
    - |
      glab release create "$CURRENT_VERSION" --name "Pre-release $CURRENT_VERSION" \
        --ref "$CI_COMMIT_SHA" \
        --notes-file "releaseNotes.md"
  rules:
    -   if: $CI_MERGE_REQUEST_IID
        when: manual
        allow_failure: true
  needs:
    - "create_changelog"
```
(CURRENT_VERSION can be used for non-release builds)

Gitlab don't support multiline variables in dotenv. Also, `release` keyword and `release-cli`
doesn't support file as input for `tag_message` (only as input for `description`). So, it means
that `release` keyword can't be used in practice for our case. As workaround, there is
`releaseNotes --one-line` that produces only 1 line and can be used for `tag_message` but in this
case release notes will be only in release page in Gitlab but not in tag message in Git.


## Help

```
$ docker run --rm -it stepin/git-parse-commits --help

Usage: git-parse-commits [<options>] <command> [<args>]...

  Provides next release version and release notes from git commit messages.

Options:
  -j, --json                     Output in json format
  -t, --tag-prefix=<text>        Prefix for tags (optional)
  --tag                          Add tag prefix to versions (only if tag prefix is defined)
  -s, --scope=<text>             Scope to filter release note items
  -i, --initial-revision=<text>  Start range from next revision
  -l, --last-revision=<text>     Stop on this revision
  -pre, --allow-pre-releases     Don't drop pre-release tags
  -h, --help                     Show this message and exit

Commands:
  version             Prints version of this tool
  currentVersion      Prints current version (useful for non-release builds)
  lastReleaseVersion  Prints version of last release
  releaseVersion      Prints version of next release from git commit messages
  releaseNotes        Prints release notes from git commit messages

$ docker run --rm -it stepin/git-parse-commits releaseNotes -h
Usage: git-parse-commits releaseNotes [<options>]

  Prints release notes from git commit messages

Options:
  -s, --short     Switch output to short format to be used as description of git tag
  -l, --one-line  Switch output to one-line format to be used as description of git tag
  -h, --help      Show this message and exit
```


## FAQ

### How it differs from https://www.conventionalcommits.org/en/v1.0.0/ ?

Several header lines are possible. We are living in a real world when sometimes several tasks
are combined in one MR (like feature + several 1 line fixes or just several fixes).
If you can create several test environments in parallel for QAs and other developers
it's better to use individual MRs. If not -- you are in a good company, this repo is to help you.

### How it differs from https://github.com/git-chglog/git-chglog ?

- multiple header lines support for single commit
- release version calculation

### How it differs from https://github.com/choffmeister/git-describe-semver ?

- release notes are produced, not only versions

### How to get release notes between 2 commits?

Just use following options:

```
--initial-revision commit1 --last-revision commit2
```

### What to do if my commits are not parsible?

Start to write compatible commit messages from now but full usages of this tool will be possible
only since next release.

### How to use with monorepos?

Simple monorepos are supported. Like if you have `client`, `api`, and `worker` components.

In this case use `--scope=client` and `--tag-prefix=client` options to get release notes
and version for client component. If next version is empty it means that according to git
commit messages nothing is changes for client and it should not be released.

### When this tool is not suitable?

- multiple components with complex dependencies: this tools can be used for version and release notes, but it will not release or skip them in some dependency tree. In this case tools like https://lerna.js.org/ can be used.
- 1 MR per task is always used. In this case change log can be generated using following command:

```bash
git log --oneline --pretty="- %s" --no-merges
```

## Commit message format

It's like https://www.conventionalcommits.org/en/v1.0.0/ but several header lines are supported.
It's case for backend development when there is no possibility to create several test envs in parallel.

```
[header(s)]

[optional body]

[optional footer(s)]
```

### Headers(s)
There are 3 cases (first one is full, other 2 is with defaults):
- `type(scope): description`
- `type: description`
- `description`

default scope it `*`: it means that this is change for all scopes when filter by scope is applied.

default type is `feat`

also `- ` in the beginning of line is acceptable.

if description has "WIP" that line is skipped.

Valid examples:

- "Initial" -> "feat(*): Initial"
- "- feat(component1): feature1\n- chore: minor refactor" -> "feat(component1): feature1\nchore(*): minor refactor"
- "- feat(component1): feature1, WIP\n- chore: minor refactor" -> "chore(*): minor refactor"

### Body
anything, it will not be parsed

### Footer(s)
it's https://git-scm.com/docs/git-interpret-trailers format. I.e. `key: value`.
Special key is "BREAKING CHANGE": it will lease to increase of major digit in version.

### Scopes
Scope is any string with letters and numbers.

Special scope is '*': it means that change is applied to the full repo, not to particular scope. For example, license change or build system change.

### Types
Types allow us to group changes in release notes and understand how to increase version numbers.

Script has following configuration:
```
types:
- types: ["*"]
  increase: minor
  group: Features
- types: ["fix", "refactor", "docs", "perf"]
  increase: patch
  group: Fixes
- types: ["chore", "ci", "build", "style", "test"]
  increase: patch
  group: Other
- types: ["skip", "wip", "minor"]
  increase: none
  group: null
- types: ["BREAKING CHANGE"]
  increase: major
  group: Features
```

`*` type means default.

Recommendations:
- Use `feat`, `fix`, `chore`, and `skip` types
- Skip is useful for bugfixes of unreleased (untagged) features: we don't want such fix in release notes. It's possible to use `WIP` in description if you will add more commits for this topic for sure but I prefer `skip` approach
- For breaking changes use `!` mark. Like `feat!: new feature`

Currently there is no way to change it using command line options. Just change it in source code.


## Support

It's non-commercial project. I will try to fix all reported bug (that I can reproduce) but without
any short-time commitments.

It's better to provide MR/patch for new features. Most probably new features
without code will not be implemented as for me this repo is feature complete.


## Known issues

`lastReleaseVersion` don't work properly if repo has only 1 commit. As it's a rare case
it's not a priority to fix.

## Development

Feel free to send MRs/patches.

Following components should be installed locally (or in Docker):

- Kotlin
- git
- bash
- jq https://jqlang.github.io/jq/
- jc https://github.com/kellyjonbrazil/jc

```bash
brew install jq jc kotlin
```

Currently, it's not possible to split single script to several files. So, it will be when it will
be possible (like around Kotlin 2.0.20).

Also, it's not clear how to write unit tests for this script. Ping me if you know some example / article.
