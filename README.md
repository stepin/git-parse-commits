# Git Better Changelog
[![GitHub release](https://img.shields.io/github/release/stepin/git-parse-commits.svg)](https://github.com/stepin/git-parse-commits/releases) [![Build status](https://img.shields.io/github/release/stepin/git-parse-commits/actions/workflows/main.yml)](https://github.com/stepin/git-parse-commits/actions/workflows/main.yml) [![github license badge](https://img.shields.io/github/license/stepin/git-parse-commits)](https://github.com/stepin/git-parse-commits)

This script is to be used in CICD pipelines to provide new version number
(bases on commit messages) and releses notes (also bases on commit messages).

Docker image: `stepin/git-parse-commits:1.0.0`

Example usage for Gitlab:

```yaml
rel_notes:
  stage: "build"
  image:
      name: "git-parse-commits:1.0.0"
      entrypoint: [""]
  variables:
      GIT_DEPTH: "0"
  script:
  - git-parse-commits -v
  - CURRENT_VERSION="$(git describe --tags --always)"
  - RELEASE_VERSION="$(git-parse-commits --tag-prefix 'v' nextVersion)"
  - echo "RELEASE_VERSION=$RELEASE_VERSION\nCURRENT_VERSION=$CURRENT_VERSION" > relNotes.env
  - git-parse-commits --tag-prefix 'v' releaseNotes > releaseNotes.md
  artifacts:
      reports:
          dotenv: relNotes.env
      paths:
      - releaseNotes.md
      expires_in: 1 day

release:
  stage: "release"
  image:
      name: "registry.gitlab.com/gitlab-org/release-cli:latest"
      entrypoint: [""]
  script:
  - echo "Release $RELEASE_VERSION"
  release:
      tag_name: "$RELEASE_VERSION"
      tag_message: "Release $RELEASE_VERSION"
      description: "releaseNotes.md"
  needs:
  - "rel_notes"
  rules:
  - if: $CI_COMMIT_REF_NAME == "main" && $CI_PIPELINE_SOURCE != "schedule"
    when: manual
    allow_failure: true
  - if: $CI_COMMIT_REF_NAME == "release/*" && $CI_PIPELINE_SOURCE != "schedule"
    when: manual
    allow_failure: true
    assets:
  links:
    - name: "Container Image $CI_COMMIT_TAG"
      url: "https://$CI_REGISTRY_IMAGE/$CI_COMMIT_REF_SLUG:$CI_COMMIT_SHA"
```
(CURRENT_VERSION can be used for non-release builds)


## Help

```
docker run --rm -it stepin/git-parse-commits --help

usage: git-parse-commits [-h] [-j] [-t [TAG_PREFIX]] [-s [SCOPE]] [-i [INITIAL_REVISION]] [-l [LAST_REVISION]] [--tag]
                         {version,currentVersion,lastReleaseVersion,releaseVersion,releaseNotes} ...

Provides next release version and release notes from git commit messages.

positional arguments:
  {version,currentVersion,lastReleaseVersion,releaseVersion,releaseNotes}
    version             Prints version of this tool
    currentVersion      Prints current version (useful for non-release builds)
    lastReleaseVersion  Prints version of last release
    releaseVersion      Prints version of next release from git commit messages
    releaseNotes        Prints release notes from git commit messages

options:
  -h, --help            show this help message and exit
  -j, --json            Output in json format
  -t [TAG_PREFIX], --tag-prefix [TAG_PREFIX]
                        prefix for tags (optional)
  -s [SCOPE], --scope [SCOPE]
                        scope to filter release note items
  -i [INITIAL_REVISION], --initial-revision [INITIAL_REVISION]
                        start range from next revision
  -l [LAST_REVISION], --last-revision [LAST_REVISION]
                        stop on this revision
  --tag                 add tag prefix to version (only if tag prefix is defined)
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

- Python 3
- git
- bash
- jq https://jqlang.github.io/jq/
- jc https://github.com/kellyjonbrazil/jc

```bash
brew install jq jc
```
