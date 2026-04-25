package io.github.flemmli97.multiloader.utils

import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.util.internal.VersionNumber

class Conventions {

    static final String LOCAL_RUNTIME = "localRuntime"
    static final String COMMON_DEPENDENCY = "commonDependency"

    static boolean hasObfuscation(Project project) {
        return VersionNumber.parse(project.minecraft_version as String) < VersionNumber.parse("26.1")
    }

    static void apply(Project proj) {
        proj.with {
            plugins.apply("java-library")
            plugins.apply("maven-publish")

            group = maven_group
            version = "$minecraft_version-$mod_version-$name"
            extensions.getByType(BasePluginExtension).archivesName.set(mod_id)

            tasks.<JavaCompile> withType(JavaCompile).configureEach {
                it.options.encoding = "UTF-8"
                it.options.release.set((findProperty("jvm") ?: 21) as Integer)
            }

            extensions.getByType(JavaPluginExtension).with {
                withSourcesJar()
            }

            extensions.extraProperties.mod_meta = [
                    "mod_name"             : findProperty("mod_name") ?: proj.project_name,
                    "mod_id"               : mod_id,
                    "version"              : version,
                    "mod_version"          : version,
                    "mod_author"           : mod_author,
                    "description"          : findProperty("description") ?: "",
                    "license"              : findProperty("license") ?: "",
                    "minecraft_version"    : minecraft_version,

                    "fabric_loader_version": findProperty("fabric_loader_version") ?: "",
                    "fabric_api_version"   : findProperty("fabric_api_version") ?: "",

                    "forge_loader_version" : findProperty("forge_loader_version") ?: "",
                    "forge_version"        : findProperty("forge_version") ?: "",

                    "neo_loader_version"   : findProperty("neo_loader_version") ?: "",
                    "neoforge_version"     : findProperty("neoforge_version") ?: "",

                    "homepage"             : findProperty("homepage") ?: findProperty("curseforge_page") ?: "",
                    "sources_url"          : findProperty("sources_url") ?: "",
                    "issue_tracker"        : findProperty("issue_tracker") ?: "",
                    "homepage_forge"       : findProperty("curseforge_page_forge") ?: "",
                    "homepage_neoforge"    : findProperty("curseforge_page_neoforge") ?: "",
                    "homepage_fabric"      : findProperty("curseforge_page_fabric") ?: "",
                    "java_version"         : findProperty("jvm") ?: 21
            ]

            if (hasObfuscation(proj)) {
                repositories.with {
                    maven {
                        name = "Parchment MC"
                        url = "https://maven.parchmentmc.org"
                        content {
                            it.includeGroupAndSubgroups("org.parchmentmc")
                        }
                    }
                }
            }

            tasks.named("sourcesJar", Jar) {
                var license = rootProject.file("LICENSE")
                if (license.exists()) {
                    it.from(rootProject.file("LICENSE"))
                }
            }

            tasks.withType(Jar).configureEach {
                var license = rootProject.file("LICENSE")
                if (license.exists()) {
                    it.from(rootProject.file("LICENSE"))
                }
                var mod_meta = project.mod_meta
                it.manifest {
                    attributes([
                            "Specification-Title"   : mod_meta.mod_name,
                            "Specification-Vendor"  : mod_meta.mod_author,
                            "Specification-Version" : proj.jar.archiveVersion,
                            "Implementation-Title"  : proj.name,
                            "Implementation-Version": proj.jar.archiveVersion,
                            "Implementation-Vendor" : mod_meta.mod_author,
                            "Built-On-Minecraft"    : minecraft_version,
                    ])
                }
            }

            tasks.withType(ProcessResources).configureEach {
                it.inputs.property "version", version

                it.filesMatching(["fabric.mod.json", "META-INF/neoforge.mods.toml", "pack.mcmeta"]) {
                    it.expand(proj.mod_meta)
                }
                it.exclude ".cache"
            }

            configurations.with {
                if (proj.configurations.findByName(Conventions.LOCAL_RUNTIME) == null) {
                    // Configuration for local dependency during runtime not exposed to consumers
                    create(Conventions.LOCAL_RUNTIME).with {
                        runtimeClasspath.extendsFrom it
                    }
                }
                return
            }

            extensions.configure(PublishingExtension) {
                it.repositories {
                    def token = proj.findProperty("maven.token") ?: System.getenv("BLAZING_COOP_MAVEN_TOKEN")
                    if (token) {
                        it.maven {
                            url = "https://maven.blazing-coop.net/releases"
                            credentials.with {
                                username = proj.findProperty("maven.user") ?: System.getenv("BLAZING_COOP_MAVEN_USER")
                                password = token
                            }
                        }
                    }
                    token = proj.findProperty("gpr.gitlab.token") ?: System.getenv("GPR_GITLAB_TOKEN")
                    if (token) {
                        it.maven {
                            url = "https://gitlab.com/api/v4/projects/21830712/packages/maven"
                            credentials.with {
                                username = proj.findProperty("gpr.user") ?: System.getenv("GPR_USER")
                                password = token
                            }
                        }
                    }
                }
            }
        }
    }
}
