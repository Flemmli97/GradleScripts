package io.github.flemmli97.multiloader.utils

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.extensions.core.extra
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.util.internal.VersionNumber
import java.io.File

object Conventions {

    const val LOCAL_RUNTIME: String = "localRuntime"
    const val COMMON_DEPENDENCY: String = "commonDependency"

    fun hasObfuscation(project: Project): Boolean {
        return VersionNumber.parse(getProperty(project, "minecraft_version")) < VersionNumber.parse("26.1")
    }

    fun getProperty(project: Project, property: String, fallback: String? = null): String {
        if (fallback != null && !project.hasProperty(property)) {
            return project.property(fallback) as String
        }
        return project.property(property) as String
    }

    fun apply(project: Project) {
        fun prop(property: String): String {
            return this.getProperty(project, property)
        }

        with(project) {
            plugins.apply("java-library")
            plugins.apply("maven-publish")

            group = prop("maven_group")
            version = "${prop("minecraft_version")}-${prop("mod_version")}-${prop("name")}"

            tasks.withType(JavaCompile::class.java).configureEach {
                options.encoding = "UTF-8"
                options.release.set(((findProperty("jvm") ?: "21") as String).toInt())
            }

            extensions.getByType(JavaPluginExtension::class.java).apply {
                withSourcesJar()
            }

            val map = mutableMapOf(
                "mod_name" to (findProperty("mod_name") ?: prop("project_name")),
                "mod_id" to prop("mod_id"),
                "version" to prop("version"),
                "mod_version" to prop("version"),
                "mod_author" to prop("mod_author"),
                "description" to (findProperty("description") ?: ""),
                "license" to (findProperty("license") ?: ""),
                "minecraft_version" to prop("minecraft_version"),

                "fabric_loader_version" to (findProperty("fabric_loader_version") ?: ""),
                "fabric_api_version" to (findProperty("fabric_api_version") ?: ""),

                "forge_loader_version" to (findProperty("forge_loader_version") ?: ""),
                "forge_version" to (findProperty("forge_version") ?: ""),

                "neo_loader_version" to (findProperty("neo_loader_version") ?: ""),
                "neoforge_version" to (findProperty("neoforge_version") ?: ""),

                "homepage" to (findProperty("homepage") ?: findProperty("curseforge_page") ?: ""),
                "sources_url" to (findProperty("sources_url") ?: ""),
                "issue_tracker" to (findProperty("issue_tracker") ?: ""),
                "homepage_forge" to (findProperty("curseforge_page_forge") ?: ""),
                "homepage_neoforge" to (findProperty("curseforge_page_neoforge") ?: ""),
                "homepage_fabric" to (findProperty("curseforge_page_fabric") ?: ""),
                "java_version" to (findProperty("jvm") ?: 21)
            )
            extra["mod_meta"] = map

            if (hasObfuscation(project)) {
                repositories.maven {
                    name = "Parchment MC"
                    setUrl("https://maven.parchmentmc.org")
                    content {
                        @Suppress("UnstableApiUsage")
                        includeGroupAndSubgroups("org.parchmentmc")
                    }
                }
            }

            tasks.named("sourcesJar", Jar::class.java) {
                val license: File = rootProject.file("LICENSE")
                if (license.exists()) {
                    from(rootProject.file("LICENSE"))
                }
            }

            tasks.withType(Jar::class.java).configureEach {
                val license = rootProject.file("LICENSE")
                if (license.exists()) {
                    from(rootProject.file("LICENSE"))
                }
                val modMeta = extra["mod_meta"] as Map<*, *>
                val version = tasks
                    .named("jar", Jar::class.java)
                    .get()
                    .archiveVersion
                    .get()
                manifest {
                    attributes(
                        mapOf(
                            "Specification-Title" to modMeta["mod_name"],
                            "Specification-Vendor" to modMeta["mod_author"],
                            "Specification-Version" to version,
                            "Implementation-Title" to name,
                            "Implementation-Version" to version,
                            "Implementation-Vendor" to modMeta["mod_author"],
                            "Built-On-Minecraft" to prop("minecraft_version"),
                        )
                    )
                }
            }

            tasks.withType(ProcessResources::class.java).configureEach {
                inputs.property("version", version)

                filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml", "pack.mcmeta")) {
                    @Suppress("unchecked_cast")
                    expand(extra["mod_meta"] as Map<String, *>)
                }
                exclude(".cache")
            }

            configurations.apply {
                if (configurations.findByName(LOCAL_RUNTIME) == null) {
                    // Configuration for local dependency during runtime not exposed to consumers
                    val local = create(LOCAL_RUNTIME)
                    this.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) {
                        extendsFrom(local)
                    }
                }
            }

            extensions.configure(PublishingExtension::class.java) {
                repositories {
                    var token: String? =
                        (findProperty("maven.token") ?: System.getenv("BLAZING_COOP_MAVEN_TOKEN")) as String?
                    if (token != null) {
                        maven {
                            setUrl("https://maven.blazing-coop.net/releases")
                            credentials {
                                username = (findProperty("maven.user")
                                    ?: System.getenv("BLAZING_COOP_MAVEN_USER")) as String?
                                password = token
                            }
                        }
                    }
                    token = (findProperty("gpr.gitlab.token") ?: System.getenv("GPR_GITLAB_TOKEN")) as String?
                    if (token != null) {
                        maven {
                            setUrl("https://gitlab.com/api/v4/projects/21830712/packages/maven")
                            credentials {
                                username = (findProperty("gpr.user") ?: System.getenv("GPR_USER")) as String?
                                password = token
                            }
                        }
                    }
                }
            }
        }
    }
}