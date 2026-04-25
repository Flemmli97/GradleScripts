package io.github.flemmli97.multiloader

import io.github.flemmli97.multiloader.utils.Conventions
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import java.util.*

class CommonPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        fun prop(property: String): String {
            return Conventions.getProperty(project, property)
        }

        val props = Properties()
        props.load(javaClass.getResourceAsStream("/multiloader.properties"))
        project.logger.lifecycle("Applying Multiloader plugin - version ${props["version"]}")

        Conventions.apply(project)
        with(project) {
            plugins.apply("net.neoforged.moddev")

            extensions.configure(NeoForgeExtension::class.java) {
                neoFormVersion = prop("neoForm_version")
                val at = file("src/main/resources/META-INF/accesstransformer.cfg")
                if (at.exists()) {
                    accessTransformers.from(at.absolutePath)
                }
                if (Conventions.hasObfuscation(project)) {
                    parchment.apply {
                        minecraftVersion.set(prop("parchment_minecraft"))
                        mappingsVersion.set(prop("parchment_version"))
                    }
                }
                validateAccessTransformers.set(true)
            }

            configurations.apply {
                // Use this configuration if the dependency is only required in common
                val common = create(Conventions.COMMON_DEPENDENCY)
                this.named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME) {
                    extendsFrom(common)
                }
            }

            repositories.maven {
                name = "Fabric"
                url = uri("https://maven.fabricmc.net")
                content {
                    @Suppress("UnstableApiUsage")
                    includeGroupAndSubgroups("net.fabricmc")
                }
            }

            dependencies.apply {
                // This is only here for the EnvType annotation which can be present with fabric dependencies
                this.add(
                    Conventions.COMMON_DEPENDENCY,
                    "net.fabricmc:fabric-loader:${prop("fabric_loader_version")}"
                )
                this.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, "org.ow2.asm:asm-tree:9.6")
                this.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, "org.spongepowered:mixin:0.8.7")
                this.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, "io.github.llamalad7:mixinextras-common:0.5.0")
                this.add(
                    JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                    "io.github.llamalad7:mixinextras-common:0.5.0"
                )
            }

            tasks.register("cleanLocalPublish") {
                group = "publishing"
                dependsOn("clean", "publishToMavenLocal")
                tasks.getByName("publishToMavenLocal").mustRunAfter("clean")
            }

            tasks.register("cleanPublish") {
                group = "publishing"
                dependsOn("clean", "publish")
                tasks.getByName("publish").mustRunAfter("clean")
            }

            if ((findProperty("with_publish") ?: true) as Boolean) {
                tasks.register("uploadAndPublish") {
                    group = "publishing"
                    dependsOn("clean", "publish")
                    tasks.getByName("publish").mustRunAfter("clean")
                }
            }

            extensions.configure(PublishingExtension::class.java) {
                publications {
                    register("mavenJava", MavenPublication::class.java) {
                        from(components.getByName("java"))
                        artifactId = prop("mod_id")
                    }
                }
            }
        }
    }
}