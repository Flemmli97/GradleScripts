package io.github.flemmli97.multiloader.remapper

import org.gradle.api.tasks.Input

abstract class RemapExtension {

    @get:Input
    abstract val mappingVersion: String

    abstract fun setMappingVersion(mapping: String)
}