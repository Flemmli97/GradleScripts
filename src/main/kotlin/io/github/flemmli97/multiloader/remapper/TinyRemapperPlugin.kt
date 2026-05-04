package io.github.flemmli97.multiloader.remapper

import groovy.transform.Canonical
import io.github.flemmli97.multiloader.utils.Conventions
import net.fabricmc.tinyremapper.FileSystemReference
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

abstract class TinyRemapperPlugin : Plugin<Project> {

    companion object {
        const val CONFIG_PREFIX: String = "yarn"
    }

    lateinit var project: Project

    val filesPath: Path get() = this.project.rootDir.toPath().resolve(".gradle").resolve("remapper")

    val loomObfuscated: Boolean by lazy {
        if (this.isLoomPresent()) {
            try {
                val field = this.project.extensions.getByName("loom").javaClass.getDeclaredField("disableObfuscation")
                field.isAccessible = true
                return@lazy field.get(this) as Boolean
            } catch (_: NoSuchFieldException) {
            }
        }
        return@lazy false
    }

    val remappedModsPath: Path
        get() {
            val dir = this.filesPath.resolve("remappedMods")
            if (!Files.exists(dir)) {
                Files.createDirectories(dir)
            }
            return dir
        }

    override fun apply(project: Project) {
        this.project = project
        val remapExtension: RemapExtension = project.extensions.create("remap", RemapExtension::class.java)
        this.createRemapConfiguration(remapExtension, JavaPlugin.API_CONFIGURATION_NAME)
        this.createRemapConfiguration(remapExtension, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
        this.createRemapConfiguration(remapExtension, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
        this.createRemapConfiguration(remapExtension, JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME)
        this.createRemapConfiguration(remapExtension, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)

        this.createRemapConfiguration(remapExtension, Conventions.LOCAL_RUNTIME)
        this.createRemapConfiguration(remapExtension, Conventions.COMMON_DEPENDENCY)

        project.rootProject.subprojects.forEach { sub ->
            sub.repositories.maven {
                name = "RemappedMods"
                setUrl(this@TinyRemapperPlugin.remappedModsPath.toFile())
            }
        }
    }

    fun isLoomPresent(): Boolean {
        return this.project.plugins.hasPlugin("fabric-loom")
    }

    fun createRemapConfiguration(extension: RemapExtension, from: String) {
        val parent = this.project.configurations.findByName(from) ?: return
        val name = from.replaceFirstChar { it.uppercase() }
        this.project.configurations.apply {
            val remapped = create("${CONFIG_PREFIX}${name}TinyRemapped")
            val config = create("${CONFIG_PREFIX}${name}")
            applyConfigurationExtension(config, remapped, parent)
            this@TinyRemapperPlugin.project.afterEvaluate {
                processArtifacts(extension.mappingVersion, config, remapped)
            }
        }
    }

    fun applyConfigurationExtension(config: Configuration, remappedConfig: Configuration, parent: Configuration) {
        if (this.isLoomPresent()) {
            // Workaround because loom resolved configurations already so can't mutate them anymore
            when (parent.name) {
                JavaPlugin.API_CONFIGURATION_NAME, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME -> {
                    this.project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
                        .extendsFrom(remappedConfig)
                    this.project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                        .extendsFrom(remappedConfig)
                }
                JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME -> {
                    this.project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
                        .extendsFrom(remappedConfig)
                }
                JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME -> {
                    this.project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                        .extendsFrom(remappedConfig)
                }
            }
            when (parent.name) {
                JavaPlugin.API_CONFIGURATION_NAME -> {
                    this.project.configurations.getByName(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME)
                        .extendsFrom(config)
                }
                JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME -> {
                    this.project.configurations.getByName(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
                        .extendsFrom(config)
                }
            }
        } else {
            parent.extendsFrom(remappedConfig)
        }
    }

    fun processArtifacts(
        mapping: String,
        sourceConfig: Configuration,
        targetConfig: Configuration
    ) {
        val resolved = sourceConfig.resolvedConfiguration.resolvedArtifacts
        val artifacts = resolved.filter {
            val file = it.file.toPath()
            return@filter file.fileName.toString().endsWith(".jar") && Files.exists(
                FileSystemReference.openJar(file).getPath("fabric.mod.json")
            )
        }
        val info = mutableListOf<ArtifactInfo>()
        artifacts.forEach {
            val file = it.file.toPath()
            val group = "remapped_${mapping.replace("+", "_").replace(".", "_")}.${it.moduleVersion.id.group}"
            val name = it.moduleVersion.id.name
            val version = it.moduleVersion.id.version
            val classifier = it.classifier
            info.add(ArtifactInfo(file, group, name, version, classifier, it.moduleVersion.id.group))
        }
        if (info.isEmpty()) return
        val forced = this.project.gradle.startParameter.isRefreshDependencies
        val toRemap = info.filter {
            return@filter !Files.exists(it.getOutputFile(this.remappedModsPath)) || !Files.exists(
                it.getPomFile(
                    this.remappedModsPath
                )
            ) || forced
        }
        if (!toRemap.isEmpty()) {
            project.logger
                .lifecycle("========== TinyMapperPlugin: Remapping mods from ${sourceConfig.name}. Amount: ${toRemap.size}==========")
            var progress = 0
            toRemap.forEach {
                val remappedFile = it.getOutputFile(this.remappedModsPath)
                val pomFile = it.getPomFile(this.remappedModsPath)
                try {
                    Files.delete(remappedFile)
                } catch (_: IOException) {
                }
                project.logger.lifecycle(
                    "Remapping: ${it.group}:${it.name}:${it.version}  " +
                            "Progress: $progress / ${toRemap.size}"
                )
                Remapper.remap(this, mapping, it.file, remappedFile)
                savePom(pomFile, it.group, it.name, it.version)
                progress++
            }
        }
        info.forEach {
            this.project.dependencies.apply {
                /**
                 * Remapped mods have a prefix so Gradle will treat them as different dependencies
                 * Using module replacement we can tell Gradle they are the same
                 * This is only required if the plugin is used in a loom project since well this is for fabric dependencies
                 */
                if (this@TinyRemapperPlugin.loomObfuscated) {
                    modules.module("${it.group}:${it.name}") {
                        replacedBy("remapped.${it.originalGroup}:${it.name}", "Use looms remapping first")
                    }
                } else if (this@TinyRemapperPlugin.isLoomPresent()) {
                    modules.module("${it.group}:${it.name}") {
                        replacedBy("${it.originalGroup}:${it.name}", "Unmapped dependencies are preferred")
                    }
                }
                add(targetConfig.name, "${it.group}:${it.name}:${it.version}${it.classifier()}")
            }
        }
    }

    /**
     * Based on https://github.com/FabricMC/fabric-loom/blob/dev/1.15/src/main/java/net/fabricmc/loom/configuration/mods/dependency/LocalMavenHelper.java#L64
     */
    fun savePom(path: Path, group: String, name: String, version: String) {
        try {
            this.javaClass.getResourceAsStream("/mod_compile_template.pom").use { stream ->
                val pomTemplate = String(stream!!.readAllBytes(), StandardCharsets.UTF_8).replace("%GROUP%", group)
                    .replace("%NAME%", name)
                    .replace("%VERSION%", version)
                Files.writeString(path, pomTemplate, StandardCharsets.UTF_8)
            }
        } catch (e: IOException) {
            throw UncheckedIOException("Failed to write mod pom", e)
        }
    }

    @Canonical
    data class ArtifactInfo(
        val file: Path,
        val group: String,
        val name: String,
        val version: String,
        val classifier: String?,
        val originalGroup: String
    ) {

        fun getOutputFile(base: Path): Path {
            return base.resolve(
                "${group.replace(".", "/")}/${name}/${version}/" +
                        "${name}-${version}${this.classifier()}.jar"
            )
        }

        fun classifier(): String {
            return if (classifier != null) "-${classifier}" else ""
        }

        fun getPomFile(base: Path): Path {
            return this.getOutputFile(base).parent.resolve("${name}-${version}.pom")
        }
    }
}