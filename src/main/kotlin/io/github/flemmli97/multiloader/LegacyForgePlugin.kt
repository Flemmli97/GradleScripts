package io.github.flemmli97.multiloader

import io.github.flemmli97.multiloader.utils.Conventions
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

class LegacyForgePlugin : Plugin<Project> {

    val Project.sourceSets: SourceSetContainer get() = this.extensions.getByName("sourceSets") as SourceSetContainer

    override fun apply(project: Project) {
        fun prop(property: String, fallback: String? = null): String {
            return Conventions.getProperty(project, property, fallback)
        }

        Conventions.apply(project)
        with(project) {
            plugins.apply("net.neoforged.moddev")

            extensions.configure(LegacyForgeExtension::class.java) {
                version = prop("forge_version")
                val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
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
                runs {
                    configureEach {
                        systemProperty("forge.enabledGameTestNamespaces", prop("mod_id"))
                        disableIdeRun()
                    }
                    create("client") {
                        client()
                        jvmArgument("-Dmixin.debug.export=true")
                    }
                    create("server") {
                        server()
                    }
                }
                mods {
                    create("mod_id") {
                        sourceSet(extensions.getByType(SourceSetContainer::class.java).getByName("main"))
                    }
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
                this.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, project(":common"))
                this.add(
                    JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, project(
                        mapOf(
                            "path" to ":common",
                            "configuration" to Conventions.COMMON_DEPENDENCY
                        )
                    )
                )
            }

            tasks.withType(ProcessResources::class.java).configureEach {
                from(project(":common").sourceSets.getByName("main").resources)
            }

            tasks.named("compileJava", JavaCompile::class.java) {
                source(project(":common").sourceSets.getByName("main").allSource)
            }

            tasks.named("sourcesJar", org.gradle.jvm.tasks.Jar::class.java) {
                from(project(":common").sourceSets.getByName("main").allSource)
                exclude(".cache")
            }

            tasks.register("cleanLocalPublish") {
                group = "publishing"
                dependsOn(":common:clean", "clean", "publishToMavenLocal")
                tasks.getByName("publishToMavenLocal").mustRunAfter(":common:clean", "clean")
            }

            tasks.register("cleanPublish") {
                group = "publishing"
                dependsOn(":common:clean", "clean", "publish")
                tasks.getByName("publish").mustRunAfter(":common:clean", "clean")
            }

            ModPublishingUtils.applyModPublishingPlugins(project, "forge")

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