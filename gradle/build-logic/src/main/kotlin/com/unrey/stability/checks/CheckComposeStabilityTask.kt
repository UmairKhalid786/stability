package com.unrey.stability.checks

import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

abstract class CheckComposeStabilityTask : DefaultTask() {
    @get:InputFiles
    abstract val reports: ConfigurableFileCollection

    @get:Input
    abstract val reportFormat: Property<String>

    @get:Input
    abstract val failOnUnstableMembers: Property<Boolean>

    @get:OutputFile
    abstract val outputReport: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    init {
        description = "Checks Compose compiler stability reports and fails if unstable classes are found."
        group = "verification"
        failOnUnstableMembers.convention(false)
        outputReport.convention(project.layout.buildDirectory.file("reports/stability/stability-check.txt"))
    }

    @TaskAction
    fun run() {
        val classHeader = Regex("""^\s*(stable|unstable)?\s*class\s+([A-Za-z0-9_]+)\s*\{\s*$""")
        val runtime =
            Regex("""<runtime stability>\s*=\s*(Stable|Unstable)""", RegexOption.IGNORE_CASE)
        val unstableMember = Regex("""\bunstable\s+val\b.*$""")

        data class Finding(
            val reportFile: String,
            val className: String,
            val runtimeStability: String,
            val unstableMembers: List<String>
        )

        val findings = mutableListOf<Finding>()

        reports.files
            .filter { it.isFile }
            .forEach { file ->
                val lines = file.readLines()
                var i = 0
                while (i < lines.size) {
                    val m = classHeader.find(lines[i])
                    if (m != null) {
                        val className = m.groupValues[2]
                        var braceDepth = 1
                        i++

                        var runtimeStability = "Unknown"
                        val unstableMembers = mutableListOf<String>()

                        while (i < lines.size && braceDepth > 0) {
                            val line = lines[i]
                            braceDepth += line.count { it == '{' }
                            braceDepth -= line.count { it == '}' }

                            runtime.find(line)?.let { rm ->
                                runtimeStability = rm.groupValues[1]
                                    .lowercase(Locale.getDefault())
                                    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                            }
                            if (unstableMember.containsMatchIn(line)) {
                                unstableMembers += line.trim()
                            }

                            i++
                        }

                        val isRuntimeUnstable =
                            runtimeStability.equals("Unstable", ignoreCase = true)
                        val hasUnstableMembers = unstableMembers.isNotEmpty()

                        if (isRuntimeUnstable || (failOnUnstableMembers.get() && hasUnstableMembers)) {
                            findings += Finding(
                                reportFile = file.path,
                                className = className,
                                runtimeStability = runtimeStability,
                                unstableMembers = unstableMembers
                            )
                        }
                        continue
                    }
                    i++
                }
            }

        // Write a human-readable report
        val out = outputReport.get().asFile
        out.parentFile.mkdirs()
        out.writeText(buildString {
            appendLine("Compose Stability Check")
            appendLine("Scanned reports:")
            reports.files.forEach { appendLine("- ${it.path}") }
            appendLine()

            if (findings.isEmpty()) {
                appendLine("✅ No unstable classes found.")
            } else {
                appendLine("❌ Unstable findings (${findings.size}):")
                findings.forEach { f ->
                    appendLine("- ${f.className} (runtime=${f.runtimeStability})")
                    appendLine("  report: ${f.reportFile}")
                    if (f.unstableMembers.isNotEmpty()) {
                        appendLine("  unstable members:")
                        f.unstableMembers.forEach { appendLine("   • $it") }
                    }
                }
            }
        })

        if (findings.isNotEmpty()) {
            val message = buildString {
                findings.forEach {
                    appendLine("Unstable class: ${it.className} (runtime=${it.runtimeStability}) in ${it.reportFile}")
                    if (it.unstableMembers.isNotEmpty()) {
                        appendLine("  Unstable members:")
                        it.unstableMembers.forEach { memberLine ->
                            appendLine("   • $memberLine")
                        }
                    }
                }
            }

            throw GradleException(
                "Compose stability check failed: found ${findings.size} unstable class(es). \n" + message +
                        "\n See: ${out.path}"
            )
        } else {
            logger.lifecycle("Compose stability check passed. Report: ${out.path}")
        }
    }
}
