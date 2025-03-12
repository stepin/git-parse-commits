#!/bin/sh
///bin/echo >/dev/null <<EOC
/*
EOC
kotlinc -script -Xplugin="${KOTLIN_HOME}/lib/kotlinx-serialization-compiler-plugin.jar" -- "$0" "$@"
exit $?
*/
// NOTE: this script is for Kotlin 2 as kotlinx-serialization-json require it
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException


fun fatal(
    exitCode: Int = 0,
    vararg messages: String,
) {
    for (message in messages) {
        println(message)
    }
    Runtime.getRuntime().exit(exitCode)
}

fun run(cmd: List<String>): String {
    try {
        val process = Runtime.getRuntime().exec(cmd.toTypedArray())

        val result = process.waitFor()
        val stdout = process.inputStream.bufferedReader().readText()
        if (result != 0) {
            val stderr = process.errorStream.bufferedReader().readText()
            fatal(
                exitCode = 2,
                "fatal: failed command $cmd",
                "output: $stdout",
                "error output: $stderr",
                "tip: most common reason: incorrect git commit SHA",
            )
        }
        return stdout.trim()
    } catch (e: IOException) {
        println("fatal: failed command $cmd")
        throw e
    }
}

val SHA_RE = "^[0-9a-f]{5,40}$".toRegex()

data class AutomaticVersion(
    val version: String,
    val notFound: Boolean,
)

fun gitDescribe(
    tagPrefix: String,
    allowPreReleases: Boolean,
    abbrev: Int,
    commitish: String,
): AutomaticVersion {
    val cmd =
        mutableListOf(
            "git",
            "describe",
            "--tags",
            "--always",
            "--abbrev=$abbrev",
            "--candidates=50",
            "--match",
            "$tagPrefix*.*.*",
        )
    if (!allowPreReleases) {
        cmd.add("--exclude")
        cmd.add("$tagPrefix*[-+]*")
    }
    cmd.add(commitish)
    val version = run(cmd)
    if (SHA_RE.matches(version)) {
        return AutomaticVersion("0.1.0-${version.take(8)}", true)
    }
    return AutomaticVersion(version, false)
}

fun lastReleaseVersion(
    tagPrefix: String,
    asTag: Boolean,
    lastRevision: String,
    allowPreReleases: Boolean,
): AutomaticVersion {
    val last = lastRevision.ifEmpty { "HEAD" }

    val automaticVersion = gitDescribe(tagPrefix, allowPreReleases, 0, "$last^")
    if (asTag) {
        return automaticVersion
    }
    return automaticVersion.copy(version = automaticVersion.version.substring(tagPrefix.length))
}

class GitCommitsParser {
    @Serializable
    enum class IncrementType(
        val sort: Int,
    ) {
        @SerialName("none")
        NONE(0),

        @SerialName("patch")
        PATCH(1),

        @SerialName("minor")
        MINOR(2),

        @SerialName("major")
        MAJOR(3),
    }

    @Serializable
    data class VersionInfo(
        val last: String?,
        val increment: IncrementType,
        val current: String,
    )

    @Serializable
    data class RawCommit(
        val commit: String,
        val author: String?,
        @SerialName("author_email")
        val authorEmail: String?,
        val date: String,
        val message: String,
        val epoch: Int,
        @SerialName("epoch_utc")
        val epochUtc: Int?,
    )

    @Serializable
    data class ChangeLogLine(
        val type: String,
        val scope: String?,
        val description: String,
        val group: String?,
        var increment: IncrementType,
    )

    @Serializable
    data class CommitInfo(
        val commit: String,
        val author: String,
        @SerialName("author_email")
        val authorEmail: String?,
        val date: String,
        val raw: String,
        var headers: List<ChangeLogLine>,
        val notes: Map<String, String>,
        val title: String?,
    )

    @Serializable
    data class ParsedInfo(
        val version: VersionInfo,
        val commits: List<CommitInfo>,
    )

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun parse(
        tagPrefix: String,
        asTag: Boolean,
        scope: String?,
        initialRevision: String,
        lastRevision: String,
        allowPreReleases: Boolean,
    ): ParsedInfo {
        val lastReleaseInfo = lastReleaseVersion(tagPrefix, true, lastRevision, allowPreReleases)
        val initialWithDefault =
            initialRevision.ifEmpty {
                if (lastReleaseInfo.notFound) {
                    ""
                } else {
                    lastReleaseInfo.version
                }
            }
        val lastWithDefault = lastRevision.ifEmpty { "HEAD" }
        val range = prepareRange(initialWithDefault, lastWithDefault)
        var lastRelease =
            if (lastReleaseInfo.notFound && asTag && tagPrefix.isNotEmpty()) {
                "${tagPrefix}${lastReleaseInfo.version}"
            } else {
                lastReleaseInfo.version
            }

        val commitsJson = run(listOf("jc", "git", "log", "--no-merges", range))
        val rawCommits = json.decodeFromString<List<RawCommit>>(commitsJson)

        var parsedCommits = rawCommits.map { parseCommit(it) }
        if (!scope.isNullOrEmpty()) {
            parsedCommits =
                parsedCommits.filter { commit ->
                    commit.headers =
                        commit.headers.filter { it.scope.isNullOrEmpty() || it.scope == scope }
                    commit.headers.isNotEmpty()
                }
        }

        val allHeaders =
            if (parsedCommits.isEmpty()) {
                emptyList()
            } else {
                parsedCommits.flatMap { commit -> commit.headers }
            }
        val increment =
            if (allHeaders.isEmpty()) {
                IncrementType.NONE
            } else {
                allHeaders.map { it.increment }.maxBy { it.sort }
            }

        if (tagPrefix.isNotEmpty() && lastRelease.startsWith(tagPrefix)) {
            lastRelease = lastRelease.substring(tagPrefix.length)
        }

        var currentVersion = incrementVersion(lastRelease, increment)

        if (asTag && tagPrefix.isNotEmpty()) {
            currentVersion = tagPrefix + currentVersion
            lastRelease = tagPrefix + lastRelease
        }

        return ParsedInfo(
            version =
                VersionInfo(
                    last = lastRelease,
                    increment = increment,
                    current = currentVersion,
                ),
            commits = parsedCommits,
        )
    }

    private fun prepareRange(
        initial: String,
        last: String,
    ): String {
        if (initial.isEmpty()) {
            return last
        }
        return "$initial..$last"
    }

    private fun parseCommit(raw: RawCommit): CommitInfo {
        val lines = raw.message.trim().split("\n")
        val (headers, notes, title) = parseMessage(lines)
        return CommitInfo(
            commit = raw.commit,
            author = raw.author ?: "",
            authorEmail = raw.authorEmail,
            date = raw.date,
            raw = raw.message,
            headers = headers,
            notes = notes,
            title = title,
        )
    }

    val RELEASE_LINE_RE = "^(\\w*)(?:\\(([\\w\\$\\.\\-\\*\\s]*)\\))?\\:\\s?(.*)$".toRegex()

    data class ParseMessageValue(
        val headers: MutableList<ChangeLogLine>,
        val notes: MutableMap<String, String>,
        val title: String?,
    )

    val IGNORE_COMMITS =
        setOf(
            "minor",
            "fix",
            "fixes",
            "better",
            "ignore",
            "wip",
            "test",
        )

    private fun parseMessage(lines: List<String>): ParseMessageValue {
        if (lines.size == 1 && IGNORE_COMMITS.contains(lines[0].lowercase())) {
            return ParseMessageValue(mutableListOf(), mutableMapOf(), null)
        }

        val headerDelimiterIndex = lines.indexOf("")
        val footerDelimiterIndex =
            if (headerDelimiterIndex != -1) {
                lines.lastIndexOf("")
            } else {
                -1
            }

        val headerLines =
            if (headerDelimiterIndex == -1) {
                lines
            } else {
                lines.subList(0, headerDelimiterIndex)
            }
        val footerLines =
            if (footerDelimiterIndex == -1) {
                emptyList()
            } else {
                lines.asReversed().subList(0, footerDelimiterIndex)
            }

        val titlePresenceDetected =
            lines.size > 2 &&
                headerDelimiterIndex == 1 &&
                (lines[2].startsWith("-") || lines[2].startsWith("*")) &&
                lines[2].contains(":")
        if (titlePresenceDetected) {
            val value = parseMessage(lines.subList(2, lines.size))
            return value.copy(title = headerLines[0])
        }

        var isMajor = false
        val notes = mutableMapOf<String, String>()
        footerLines.forEach {
            val line = it.trim()
            val delimiter = line.indexOf(":")
            if (delimiter == -1) return@forEach
            val key = line.take(delimiter).trim()
            val value = line.substring(delimiter + 1).trim()
            notes[key] = value
            if (key == "BREAKING CHANGE") {
                isMajor = true
            }
        }

        val headers = mutableListOf<ChangeLogLine>()
        headerLines.forEach { lineOriginal ->
            var line = lineOriginal.trim()
            if (line.startsWith("-") || line.startsWith("*")) {
                line = line.substring(1).trim()
            }

            val parsed = RELEASE_LINE_RE.find(line)
            if (parsed != null) {
                var type = parsed.groupValues[1].lowercase()
                var scope: String? = parsed.groupValues[2].lowercase()
                if (scope == "*" || scope == "") scope = null
                var increment =
                    if (type.endsWith("!")) {
                        type = type.take(type.length - 1)
                        IncrementType.MAJOR
                    } else if (isMajor) {
                        IncrementType.MAJOR
                    } else {
                        incrementFromType(type)
                    }
                var group = groupFromType(type)
                val description = parsed.groupValues[3]

                if (line.contains("WIP")) {
                    type = "wip"
                    group = null
                    increment = IncrementType.NONE
                }

                headers += ChangeLogLine(type, scope, description, group, increment)
            } else {
                var type = "feat"
                var increment = incrementFromType(type)
                var group = groupFromType(type)

                if (line.contains("WIP")) {
                    type = "wip"
                    group = null
                    increment = IncrementType.NONE
                }

                headers += ChangeLogLine(type, null, line, group, increment)
            }
        }

        if (isMajor) {
            val commitIncrement = headers.map { it.increment }.maxBy { it.sort }
            if (commitIncrement != IncrementType.MAJOR) {
                if (headers.isEmpty()) {
                    val type = "feat"
                    val group = groupFromType(type)
                    val line =
                        if (headerLines.isEmpty()) {
                            headerLines.first()
                        } else {
                            ""
                        }
                    headers += ChangeLogLine(type, null, line, group, IncrementType.MAJOR)
                } else {
                    headers[0].increment = IncrementType.MAJOR
                }
            }
        }

        return ParseMessageValue(headers.asReversed(), notes, null)
    }

    private fun groupFromType(type: String): String? =
        when (type) {
            "fix", "hotfix", "refactor", "docs", "perf", "BREAKING CHANGE" -> "Fixes"
            "chore", "ci", "build", "style", "test" -> "Other"
            "skip", "wip", "minor" -> null
            else -> "Features"
        }

    private fun incrementFromType(type: String): IncrementType =
        when (type) {
            "BREAKING CHANGE" -> IncrementType.MAJOR

            "fix", "hotfix", "refactor", "docs", "perf", "chore", "ci", "build", "style",
            "test",
            -> IncrementType.PATCH

            "skip", "wip", "minor" -> IncrementType.NONE
            else -> IncrementType.MINOR
        }

    private fun incrementVersion(
        version: String,
        increment: IncrementType,
    ): String {
        val versionParts = "\\.|\\-|\\+".toRegex().split(version)
        if (versionParts.size < 3 || versionParts[0].isEmpty() || versionParts[1].isEmpty() || versionParts[2].isEmpty()) {
            fatal(
                exitCode = 3,
                "version '$version' looks incorrect.",
                "tip: did you forget --tag-prefix option?",
            )
        }
        var major = versionParts[0].toInt()
        var minor = versionParts[1].toInt()
        var patch = versionParts[2].toInt()
        when (increment) {
            IncrementType.NONE -> {}
            IncrementType.PATCH -> patch += 1
            IncrementType.MINOR -> {
                minor += 1
                patch = 0
            }

            IncrementType.MAJOR -> {
                major += 1
                minor = 0
                patch = 0
            }
        }
        return "$major.$minor.$patch"
    }
}

class CliConfig(
    val asJson: Boolean,
    val tagPrefix: String,
    val asTag: Boolean,
    val scope: String,
    val initialRevision: String,
    val lastRevision: String,
    val allowPreReleases: Boolean,
)

class GitParseCommits : CliktCommand(name = "git-parse-commits") {
    override fun help(context: Context) = "Provides next release version and release notes from git commit messages."

    override val printHelpOnEmptyArgs = true
    private val json by option("-j", "--json", help = "Output in json format").flag()
    private val tagPrefix by option(
        "-t",
        "--tag-prefix",
        help = "Prefix for tags (optional)",
    ).default("")
    private val tag by option(
        help = "Add tag prefix to versions (only if tag prefix is defined)",
    ).flag()
    private val scope by option(
        "-s",
        "--scope",
        help = "Scope to filter release note items",
    ).default("")
    private val initialRevision by option(
        "-i",
        "--initial-revision",
        help = "Start range from next revision",
    ).default("")
    private val lastRevision by option(
        "-l",
        "--last-revision",
        help = "Stop on this revision",
    ).default("HEAD")
    private val allowPreReleases by option(
        "-pre",
        "--allow-pre-releases",
        help = "Don't drop pre-release tags",
    ).flag()

    override fun run() {
        currentContext.obj = CliConfig(json, tagPrefix, tag, scope, initialRevision, lastRevision, allowPreReleases)
    }
}

class Version : CliktCommand(name = "version") {
    override fun help(context: Context) = "Prints version of this tool"

    private val config by requireObject<CliConfig>()

    override fun run() {
        val version = "SNAPSHOT"
        val result =
            if (config.asJson) {
                """{"tool_version": "$version"}"""
            } else {
                version
            }
        println(result)
    }
}

class CurrentVersion : CliktCommand(name = "currentVersion") {
    override fun help(context: Context) = "Prints current version (useful for non-release builds)"

    private val config by requireObject<CliConfig>()

    override fun run() {
        val last = config.lastRevision.ifEmpty { "HEAD" }
        val automaticVersion = gitDescribe(config.tagPrefix, config.allowPreReleases, 7, last)
        val version =
            if (config.asTag) {
                automaticVersion.version
            } else {
                automaticVersion.version.substring(config.tagPrefix.length)
            }
        val result =
            if (config.asJson) {
                """{"current_version": "$version"}"""
            } else {
                version
            }
        println(result)
    }
}

class LastReleaseVersion : CliktCommand(name = "lastReleaseVersion") {
    override fun help(context: Context) = "Prints version of last release"

    private val config by requireObject<CliConfig>()

    override fun run() {
        val automaticVersion =
            lastReleaseVersion(
                config.tagPrefix,
                config.asTag,
                config.lastRevision,
                config.allowPreReleases,
            )
        val version = automaticVersion.version
        val result =
            if (config.asJson) {
                """{"last_release_version": "$version"}"""
            } else {
                version
            }
        println(result)
    }
}

class ReleaseVersion(
    private val gitCommitsParser: GitCommitsParser,
) : CliktCommand(name = "releaseVersion") {
    override fun help(context: Context) = "Prints version of next release from git commit messages"

    private val config by requireObject<CliConfig>()

    override fun run() {
        val parsedInfo =
            gitCommitsParser.parse(
                config.tagPrefix,
                config.asTag,
                config.scope,
                config.initialRevision,
                config.lastRevision,
                config.allowPreReleases,
            )
        val version = parsedInfo.version
        val result =
            if (config.asJson) {
                """{"release_version": "${version.current}", "increment": "${version.increment.name.lowercase()}"}"""
            } else {
                version.current
            }
        println(result)
    }
}

class ReleaseNotes(
    private val gitCommitsParser: GitCommitsParser,
) : CliktCommand(name = "releaseNotes") {
    override fun help(context: Context) = "Prints release notes from git commit messages"

    private val asShort by option(
        "-s",
        "--short",
        help = "Switch output to short format to be used as description of git tag",
    ).flag()
    private val asOneline by option(
        "-l",
        "--one-line",
        help = "Switch output to one-line format to be used as description of git tag",
    ).flag()
    private val config by requireObject<CliConfig>()

    override fun run() {
        val parsedInfo =
            gitCommitsParser.parse(
                config.tagPrefix,
                config.asTag,
                config.scope,
                config.initialRevision,
                config.lastRevision,
                config.allowPreReleases,
            )
        val result =
            if (config.asJson) {
                Json.encodeToString<GitCommitsParser.ParsedInfo>(parsedInfo)
            } else {
                formatReleaseNotes(parsedInfo, asShort, asOneline)
            }
        println(result)
    }

    private fun formatReleaseNotes(
        parsedInfo: GitCommitsParser.ParsedInfo,
        asShortInitial: Boolean,
        asOneline: Boolean,
    ): String {
        val asShort = asShortInitial || asOneline

        val linesPerGroup = getReleaseLinesPerGroup(parsedInfo)
        val features = groupToText("Features", linesPerGroup["Features"], asShort)
        val fixes = groupToText("Fixes", linesPerGroup["Fixes"], asShort)
        val other = groupToText("Other", linesPerGroup["Other"], asShort)

        if (asOneline) {
            val result =
                listOfNotNull(features, fixes, other)
                    .joinToString("\n")
                    .split("\n")
                    .firstOrNull()
            if (result.isNullOrEmpty()) return ""
            if (result == features) return result
            return "$result..."
        }
        val separator = if (asShort) "\n" else "\n\n"
        return listOfNotNull(features, fixes, other).joinToString(separator)
    }

    data class ReleaseLine(
        val sha: String,
        val type: String,
        val scope: String?,
        val description: String,
    ) {
        companion object {
            fun of(
                commit: GitCommitsParser.CommitInfo,
                header: GitCommitsParser.ChangeLogLine,
            ) = ReleaseLine(
                sha = commit.commit.take(7),
                type = header.type,
                scope = header.scope,
                description = header.description,
            )
        }
    }

    private fun getReleaseLinesPerGroup(parsedInfo: GitCommitsParser.ParsedInfo): Map<String, Set<ReleaseLine>> {
        val result = mutableMapOf<String, MutableSet<ReleaseLine>>()
        parsedInfo.commits.forEach { commit ->
            commit.headers.forEach { header ->
                if (header.group != null) {
                    result.getOrPut(header.group) { mutableSetOf() }.add(
                        ReleaseLine.of(commit, header),
                    )
                }
            }
        }
        return result
    }

    private fun groupToText(
        header: String,
        lines: Set<ReleaseLine>?,
        asShort: Boolean,
    ): String? {
        if (asShort) {
            return groupToTextShort(lines)
        }
        return groupToTextFull(header, lines)
    }

    private fun groupToTextShort(lines: Set<ReleaseLine>?): String? {
        if (lines.isNullOrEmpty()) return null
        val result = mutableSetOf<String>()
        lines.forEach { line ->
            val scope = if (line.scope != "*" && line.scope != null) line.scope else ""
            var type =
                if (scope.isNotEmpty()) {
                    "${line.type}($scope)"
                } else {
                    line.type
                }
            if (type.isNotEmpty()) {
                type += ": "
            }
            result.add("- ${type}${line.description}")
        }
        return result.joinToString("\n")
    }

    private fun groupToTextFull(
        header: String,
        lines: Set<ReleaseLine>?,
    ): String? {
        if (lines.isNullOrEmpty()) return null
        val result = mutableSetOf("### $header\n")
        lines.forEach { line ->
            val scope = if (line.scope != "*" && line.scope != null) line.scope else ""
            var type =
                if (line.type != "feat" && line.type != "fix") {
                    if (scope.isNotEmpty()) {
                        "${line.type}($scope)"
                    } else if (line.type.isNotEmpty()) {
                        line.type
                    } else {
                        scope
                    }
                } else {
                    scope
                }
            if (type.isNotEmpty()) {
                type += ": "
            }
            result.add("- (${line.sha}) ${type}${line.description}")
        }
        return result.joinToString("\n")
    }
}

val gitCommitsParser = GitCommitsParser()

GitParseCommits()
    .subcommands(
        Version(),
        CurrentVersion(),
        LastReleaseVersion(),
        ReleaseVersion(gitCommitsParser),
        ReleaseNotes(gitCommitsParser),
    ).main(args)
