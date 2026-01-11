package com.unrey.stability.checks

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

class ComposeStabilityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {

        // Extension to allow per-module overrides
        val ext = extensions.create<ComposeStabilityExtension>("composeStability")

        // Register the task
        val checkTask = tasks.register<CheckComposeStabilityTask>("checkComposeStability") {
            group = "verification"
            description = "Checks Compose stability (your custom rule)."

            reports.from(
                fileTree(layout.buildDirectory) {
                    include(
                        "compose-reports/**/*classes*.txt",
                    )
                }
            )

            // Optional: only run if enabled
            onlyIf { ext.enabled.get() }
        }

        // Hook into the standard lifecycle if you want
        // (so ./gradlew check runs it)
        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(checkTask)
        }
    }
}
