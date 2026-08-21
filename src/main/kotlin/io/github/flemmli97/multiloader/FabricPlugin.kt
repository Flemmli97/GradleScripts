package io.github.flemmli97.multiloader

import io.github.flemmli97.multiloader.utils.Conventions
import io.github.flemmli97.multiloader.utils.ModPublishingUtils
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.util.Constants
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

class FabricPlugin : Plugin<Project> {

    val Project.sourceSets: SourceSetContainer get() = this.extensions.getByName("sourceSets") as SourceSetContainer

    override fun apply(project: Project) {
        fun prop(property: String, fallback: String? = null): String {
            return Conventions.getProperty(project, property, fallback)
        }

        Conventions.apply(project, false)
        with(project) {
            if (Conventions.hasObfuscation(project)) {
                plugins.apply("fabric-loom")
            } else {
                plugins.apply("net.fabricmc.fabric-loom")
            }
            var ext: LoomGradleExtensionAPI? = null;
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
                // We cant fetch it directly due to classloading things. So save this instance
                ext = this@configure;
            }

            dependencies.apply {
                this.add(Constants.Configurations.MINECRAFT, "com.mojang:minecraft:${prop("minecraft_version")}")
                if (Conventions.hasObfuscation(project)) {
                    ext?.let {
                        @Suppress("UnstableApiUsage")
                        this.add(
                            Constants.Configurations.MAPPINGS,
                            it.layered {
                                officialMojangMappings()
                                parchment("org.parchmentmc.data:parchment-${prop("parchment_minecraft")}:${prop("parchment_version")}@zip")
                            })
                    }
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

            tasks.withType(ProcessResources::class.java).configureEach {
                from(project(":common").sourceSets.getByName("main").resources)
                exclude("**/accesstransformer.cfg")
            }

            tasks.named("compileJava", JavaCompile::class.java) {
                source(project(":common").sourceSets.getByName("main").allSource)
            }

            tasks.named("sourcesJar", Jar::class.java) {
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

            ModPublishingUtils.applyModPublishingPlugins(project,"fabric", if (Conventions.hasObfuscation(project)) "remapJar" else "jar")

            extensions.configure(PublishingExtension::class.java) {
                publications {
                    register("mavenJava", MavenPublication::class.java) {
                        from(components.getByName("java"))
                        artifactId = prop("mod_id")
                        suppressPomMetadataWarningsFor("runtimeElements")
                    }
                }
            }
        }
    }
}