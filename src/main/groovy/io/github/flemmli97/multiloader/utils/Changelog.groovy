package io.github.flemmli97.multiloader.utils

import org.gradle.api.Project

class Changelog {

    static def changelog(Project project, int versions) {
        try {
            var changelog = ""
            var match = 0
            project.rootProject.file("Changelog.md").eachLine {
                if (it.matches("${project.findProperty("mod_name") ?: project.project_name} ([0-9]+)([-.A-z0-9])+\$"))
                    match++
                if (match <= versions) {
                    changelog += it + "\n"
                }
                it
            }
            return changelog + "\n\n"
        } catch (exception) {
            return "${project.findProperty("mod_name") ?: project.name} ${project.property("mod_version")}\n==========\nThere was an error generating the changelog: $exception"
        }
    }
}
