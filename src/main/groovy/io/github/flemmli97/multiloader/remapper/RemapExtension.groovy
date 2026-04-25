package io.github.flemmli97.multiloader.remapper


import org.gradle.api.tasks.Input

abstract class RemapExtension {

    @Input
    abstract String getMappingVersion();

    abstract void setMappingVersion(String mapping);
}