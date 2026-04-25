package io.github.flemmli97.multiloader.remapper

import groovy.transform.Canonical
import io.github.flemmli97.multiloader.utils.Conventions
import net.fabricmc.tinyremapper.FileSystemReference
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

abstract class TinyRemapperPlugin implements Plugin<Project> {

    static final String CONFIG_PREFIX = "yarn"

    Project project

    Path getFilesPath() {
        return this.project.getRootDir().toPath().resolve(".gradle").resolve("remapper")
    }

    Path getRemappedModsPath() {
        Path dir = this.getFilesPath().resolve("remappedMods")
        if (!Files.exists(dir))
            Files.createDirectories(dir)
        return dir;
    }

    Project getProject() {
        return this.project
    }

    @Override
    void apply(Project project) {
        this.project = project
        def remapExtension = project.extensions.create("remap", RemapExtension.class)
        this.createRemapConfiguration(remapExtension, JavaPlugin.API_CONFIGURATION_NAME)
        this.createRemapConfiguration(remapExtension, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
        this.createRemapConfiguration(remapExtension, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
        this.createRemapConfiguration(remapExtension, JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME)
        this.createRemapConfiguration(remapExtension, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)

        this.createRemapConfiguration(remapExtension, Conventions.LOCAL_RUNTIME)
        this.createRemapConfiguration(remapExtension, Conventions.COMMON_DEPENDENCY)

        project.rootProject.subprojects {
            it.repositories.with {
                maven {
                    name = "RemappedMods"
                    setUrl(this.getRemappedModsPath().toFile())
                }
            }
        }
    }

    def createRemapConfiguration(RemapExtension extension, String from) {
        def parent = this.getProject().configurations.findByName(from)
        if (parent == null)
            return
        def name = parent.name.capitalize()
        this.getProject().configurations.with {
            def remapped = create("${TinyRemapperPlugin.CONFIG_PREFIX}${name}TinyRemapped")
            parent.extendsFrom remapped
            def conf = create("${TinyRemapperPlugin.CONFIG_PREFIX}${name}")
            this.getProject().afterEvaluate {
                processArtifacts(extension.getMappingVersion(), conf, remapped)
            }
        }
    }

    def processArtifacts(String mapping, Configuration sourceConfig, Configuration targetConfig) {
        def resolved = sourceConfig.getResolvedConfiguration().resolvedArtifacts
        def artifacts = resolved.findAll {
            def file = it.file.toPath()
            return file.getFileName().toString().endsWith(".jar") && Files.exists(FileSystemReference.openJar(file).getPath("fabric.mod.json"))
        }
        List<ArtifactInfo> info = []
        artifacts.each {
            def file = it.file.toPath()
            def group = "remapped_${mapping.replace("+", "_").replace(".", "_")}.${it.moduleVersion.id.group}"
            def name = "${it.moduleVersion.id.name}"
            def version = "${it.moduleVersion.id.version}"
            def classifier = it.classifier
            info.add(new ArtifactInfo(file, group, name, version, classifier))
        }
        if (info.empty)
            return
        boolean forced = this.project.getGradle().getStartParameter().isRefreshDependencies()
        def toRemap = info.findAll {
            return !Files.exists(it.getOutputFile(this.getRemappedModsPath())) || !Files.exists(it.getPomFile(this.getRemappedModsPath())) || forced
        }
        if (!toRemap.empty) {
            project.getLogger().lifecycle("========== TinyMapperPlugin: Remapping mods from ${sourceConfig.name}. Amount: ${toRemap.size()}==========")
            def progress = 0
            toRemap.each {
                def remappedFile = it.getOutputFile(this.getRemappedModsPath())
                def pomFile = it.getPomFile(this.getRemappedModsPath())
                try {
                    Files.delete(remappedFile)
                } catch (IOException ignored) {
                }
                project.getLogger().lifecycle("Remapping: ${it.group}:${it.name}:${it.version}  " +
                        "Progress: ${progress} / ${toRemap.size()}")
                Remapper.remap(this, mapping, it.file, remappedFile)
                savePom(pomFile, it.group, it.name, it.version)
                progress++
            }
        }
        info.each {
            this.project.dependencies.add(targetConfig.name, "${it.group}:${it.name}:${it.version}${it.classifier ? ":${it.classifier}" : ""}")
        }
    }

    /**
     * Based on https://github.com/FabricMC/fabric-loom/blob/dev/1.15/src/main/java/net/fabricmc/loom/configuration/mods/dependency/LocalMavenHelper.java#L64
     */
    void savePom(Path path, String group, String name, String version) {
        try {
            getClass().getResourceAsStream("/mod_compile_template.pom").withCloseable {
                String pomTemplate = new String(it.readAllBytes(), StandardCharsets.UTF_8)
                        .replace("%GROUP%", group)
                        .replace("%NAME%", name)
                        .replace("%VERSION%", version)
                Files.writeString(path, pomTemplate, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write mod pom", e);
        }
    }

    @Canonical
    static class ArtifactInfo {
        Path file
        String group
        String name
        String version
        String classifier

        def getOutputFile(Path base) {
            return base.resolve("${group.replace(".", "/")}/${name}/${version}/" +
                    "${name}-${version}${classifier ? "-${classifier}" : ""}.jar")
        }

        def getPomFile(Path base) {
            return this.getOutputFile(base).getParent().resolve("${name}-${version}.pom")
        }
    }
}
