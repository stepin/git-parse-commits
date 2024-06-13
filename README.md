# Git Parse Commits
[![GitHub release](https://img.shields.io/github/release/stepin/git-parse-commits.svg)](https://github.com/stepin/git-parse-commits/releases) [![github license badge](https://img.shields.io/github/license/stepin/git-parse-commits)](https://github.com/stepin/git-parse-commits)

## Intro

When we create release in Gitea / Gitlab / Github we need to fill 3 fields:
- Release name
- Tag name
- Release description
- Tag commit message

Yes, it's not that hard to fill it every time automatically but it anyway takes time,
and it's error-prone process.

So, this script is introduced to automate this process. Some key points:
- Human decides what will be in release note but decision point is moved to MR merge time (when
we specify MR commit message)
- Human decides when release should happen (start manual job)
- If something is not working it's still possible to create release from UI without these CI job


## Details

This script is to be used in CICD pipelines to provide new version number
(bases on commit messages) and releases notes (also bases on commit messages).

Docker image: [stepin/git-parse-commits:latest](https://hub.docker.com/r/stepin/git-parse-commits)

Example how to use with Docker:

```shell
docker run --rm -it -v "$(PWD):/git" -w /git --user "$(id -u)" stepin/git-parse-commits:2.1.0 releaseVersion
docker run --rm -it -v "$(PWD):/git" -w /git --user "$(id -u)" stepin/git-parse-commits:2.1.0 releaseNotes
docker run --rm -it -v "$(PWD):/git" -w /git --user "$(id -u)" stepin/git-parse-commits:2.1.0 releaseNotes --short
```

Example usage for Gitlab:

```yaml
create_changelog:
  stage: "build"
  image:
      name: "stepin/git-parse-commits:2.1.0"
      entrypoint: [""]
  variables:
      GIT_DEPTH: "0"
  script:
  - git-parse-commits version
  - CURRENT_VERSION="$(git-parse-commits currentVersion)"
  - RELEASE_VERSION="$(git-parse-commits releaseVersion)"
  - echo "RELEASE_VERSION=$RELEASE_VERSION" >> relNotes.env
  - echo "CURRENT_VERSION=$CURRENT_VERSION" >> relNotes.env
  - cat relNotes.env
  - git-parse-commits releaseNotes > releaseNotes.md
  - git-parse-commits releaseNotes --short > gitTagCommitMessage.txt
  artifacts:
      reports:
          dotenv: relNotes.env
      paths:
      - releaseNotes.md
      - gitTagCommitMessage.txt
      expire_in: 1 day
  rules:
  - if: $CI_MERGE_REQUEST_IID
  - if: $CI_COMMIT_REF_NAME == "main" && $CI_PIPELINE_SOURCE != "schedule"
  - if: $CI_COMMIT_REF_NAME == "release/*" && $CI_PIPELINE_SOURCE != "schedule"

release:
  stage: "release"
  image:
      name: "registry.gitlab.com/gitlab-org/release-cli:latest"
      entrypoint: [""]
  script:
  - echo "Release $RELEASE_VERSION"
  release:
      tag_name: "$RELEASE_VERSION"
      tag_message: "gitTagCommitMessage.txt"
      description: "releaseNotes.md"
  assets:
    links:
    - name: "Container Image $CI_COMMIT_TAG"
      url: "https://$CI_REGISTRY_IMAGE/$CI_COMMIT_REF_SLUG:$CI_COMMIT_SHA"
  needs:
  - "create_changelog"
  rules:
  - if: $CI_COMMIT_REF_NAME == "main" && $CI_PIPELINE_SOURCE != "schedule"
    when: manual
    allow_failure: true
  - if: $CI_COMMIT_REF_NAME == "release/*" && $CI_PIPELINE_SOURCE != "schedule"
    when: manual
    allow_failure: true
```
(CURRENT_VERSION can be used for non-release builds)


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

- multiple header lines support
- release version calculation

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

- multiple components with complext dependencies: this tools can be used for version and release notes but it will not release or skip them in some dependency tree. In this case tools like https://lerna.js.org/ can be used.
- 1 MR per task is used. In this case change log can be generated using following command:

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
