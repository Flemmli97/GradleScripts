package io.github.flemmli97.multiloader.utils

import org.gradle.api.Project

class Changelog {

    static String changelog(Project project, int versions) {
        var name = project.findProperty("mod_name") ?: project.findProperty("project_name") ?: project.name
        try {
            var changelog = ""
            var match = 0
            project.rootProject.file("Changelog.md").eachLine {
                if (it.matches("${name} ([0-9]+)([-.A-z0-9])+\$"))
                    match++
                if (match <= versions) {
                    changelog += it + "\n"
                }
                it
            }
            return changelog + "\n\n"
        } catch (exception) {
            return """
                |${name} ${project.property("mod_version")}
                |==========
                |There was an error generating the changelog: ${exception}            
                """.stripMargin()
        }
    }
}
