package io.github.flemmli97.multiloader

import io.github.flemmli97.multiloader.utils.Conventions
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.util.Constants
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

class FabricPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        fun prop(property: String, fallback: String? = null): String {
            return Conventions.getProperty(project, property, fallback)
        }

        Conventions.apply(project)
        with(project) {
            extensions.configure(LoomGradleExtensionAPI::class.java) {
                val aw = file("src/main/resources/${prop("mod_id")}.accesswidener")
                if (aw.exists()) {
                    accessWidenerPath.set(aw)
                }
                @Suppress("UnstableApiUsage")
                mixin {
                    useLegacyMixinAp.set(false)
                }
                runs {
                    named("client") {
                        client()
                        vmArgs("-Dmixin.debug.export=true")
                    }
                    named("server") {
                        server()
                    }
                }
            }

            dependencies.apply {
                this.add(Constants.Configurations.MINECRAFT, "com.mojang:minecraft:${prop("minecraft_version")}")
                if (Conventions.hasObfuscation(project)) {
                    @Suppress("UnstableApiUsage")
                    this.add(
                        Constants.Configurations.MAPPINGS,
                        extensions.getByType(LoomGradleExtensionAPI::class.java).layered {
                            officialMojangMappings()
                            parchment("org.parchmentmc.data:parchment-${prop("parchment_minecraft")}:${prop("parchment_version")}@zip")
                        })
                    this.add("modImplementation", "net.fabricmc:fabric-loader:${prop("fabric_loader_version")}")
                    this.add("modApi", "net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")
                } else {
                    this.add(
                        JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                        "net.fabricmc:fabric-loader:${prop("fabric_loader_version")}"
                    )
                    this.add(
                        JavaPlugin.API_CONFIGURATION_NAME,
                        "net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}"
                    )
                }
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

            val commonJavaExtension = project(":common").extensions.getByType(JavaPluginExtension::class.java)

            tasks.withType(ProcessResources::class.java).configureEach {
                from(commonJavaExtension.sourceSets.getByName("main").resources)
                exclude("**/accesstransformer.cfg")
            }

            tasks.withType(JavaCompile::class.java).configureEach {
                source(commonJavaExtension.sourceSets.getByName("main").allSource)
            }

            tasks.named("sourcesJar", Jar::class.java) {
                from(commonJavaExtension.sourceSets.getByName("main").allSource)
                exclude(".cache")
            }

            tasks.register("cleanLocalPublish") {
                group = "publishing"
                dependsOn(":common:clean", "clean", "publishToMavenLocal")
                tasks.named("publishToMavenLocal") {
                    mustRunAfter(":common:clean", "clean")
                }
            }

            tasks.register("cleanPublish") {
                group = "publishing"
                dependsOn(":common:clean", "clean", "publish")
                tasks.named("publish") {
                    mustRunAfter(":common:clean", "clean")
                }
            }

            ModPublishingUtils.applyModPublishingPlugins(project, "fabric")

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