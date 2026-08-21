package io.github.flemmli97.multiloader.utils

import org.gradle.api.Project

object Changelog {

    fun changelog(project: Project, versions: Int): String {
        val name = project.findProperty("mod_name") ?: project.findProperty("project_name") ?: project.name
        try {
            var changelog = ""
            var match = 0
            project.rootProject.file("Changelog.md").forEachLine {
                if (it.matches(Regex("$name ([0-9]+)([-.A-z0-9])+$")))
                    match++
                if (match <= versions) {
                    changelog += it + "\n"
                }
                it
            }
            return changelog + "\n\n"
        } catch (exception: Exception) {
            return """
                |${name} ${project.property("mod_version")}
                |==========
                |There was an error generating the changelog: $exception            
                """.trimMargin()
        }
    }
}