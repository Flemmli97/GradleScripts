package io.github.flemmli97.multiloader

import io.github.flemmli97.multiloader.utils.Conventions
import org.gradle.api.Plugin
import org.gradle.api.Project

class ConventionsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        Conventions.apply(project)
    }
}