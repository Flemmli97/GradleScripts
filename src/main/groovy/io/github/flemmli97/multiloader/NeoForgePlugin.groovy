package io.github.flemmli97.multiloader

import io.github.flemmli97.multiloader.utils.Changelog
import io.github.flemmli97.multiloader.utils.Conventions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

class NeoForgePlugin implements Plugin<Project> {

    static propertyWithFallback(Project project, String name, String fallback) {
        return project.hasProperty(name) ? project."${name}" : project."${fallback}"
    }

    @Override
    void apply(Project proj) {
        Conventions.apply(proj)
        proj.with {
            plugins.apply("net.neoforged.moddev")

            neoForge {
                version = neoforge_version
                def at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
                if (at.exists()) {
                    accessTransformers.from(at.absolutePath)
                }
                if (Conventions.hasObfuscation(proj)) {
                    parchment {
                        minecraftVersion = parchment_minecraft
                        mappingsVersion = parchment_version
                    }
                }
                validateAccessTransformers = true
                runs {
                    configureEach {
                        systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
                        disableIdeRun()
                    }
                    client {
                        client()
                        jvmArgument "-Dmixin.debug.export=true"
                    }
                    server {
                        server()
                    }
                }
                mods {
                    "${mod_id}" {
                        sourceSet sourceSets.main
                    }
                }
            }

            repositories.with {
                maven {
                    name = "Fabric"
                    url = uri("https://maven.fabricmc.net")
                    content {
                        it.includeGroupByRegex("net.fabricmc")
                    }
                }
            }

            dependencies.with {
                compileOnly(project(":common"))
                compileOnly(it.project(path: ":common", configuration: Conventions.COMMON_DEPENDENCY))
            }

            tasks.withType(ProcessResources).configureEach {
                from(project(":common").sourceSets.main.resources)
            }

            tasks.named("compileJava", JavaCompile) {
                source(project(":common").sourceSets.main.allSource)
            }

            tasks.named("sourcesJar", Jar) {
                from(project(":common").sourceSets.main.allSource)
                exclude ".cache"
            }

            var hasCurseforge = tasks.findByName("curseforge") != null
            if (hasCurseforge) {
                curseforge {
                    apiKey = findProperty("curseApiToken") ?: "0"
                    it.project {
                        id = "${propertyWithFallback(proj, "curseforge_id", "curseforge_id_neoforge")}"
                        "${proj.curseforge_versions}".split(", ").each {
                            addGameVersion "${it}"
                        }
                        addGameVersion "NeoForge"
                        mainArtifact(jar.archiveFile) {
                            def txt = Changelog.changelog(proj, 1).replace("\n-", "\n\n- ")
                            txt = txt + "\n\n" + "For past versions see: ${full_changelog}"
                            changelog = txt
                            changelogType = "markdown"
                            releaseType = "release"
                            if (mod_version.contains("beta")) {
                                releaseType = "beta"
                            }
                            if (mod_version.contains("alpha")) {
                                releaseType = "alpha"
                            }
                        }
                        var dependencies = proj.curseforge_dep_neoforge
                        if (!"${dependencies}".isEmpty() || proj.hasProperty("optional_curseforge_dep_neoforge")) {
                            relations {
                                if (!"${dependencies}".isEmpty()) {
                                    "${dependencies}".split(", ").each {
                                        requiredDependency "${it}"
                                    }
                                }
                                if (proj.hasProperty("optional_curseforge_dep_neoforge")) {
                                    "${optional_curseforge_dep_neoforge}".split(", ").each {
                                        optionalDependency "${it}"
                                    }
                                }
                            }
                        }
                    }
                }
                tasks.getByName("curseforge").dependsOn build
            }

            var hasModrinth = tasks.findByName("modrinth") != null
            if (hasModrinth) {
                modrinth {
                    token = findProperty("modrinthApiToken") ?: "0"
                    projectId = "${modrinth_id}"
                    versionNumber = version
                    versionName = jar.archiveFileName
                    changelog = Changelog.changelog(proj, 1) + "\n\n" + "For past versions see: ${full_changelog}"
                    versionType = "release"
                    if (mod_version.contains("beta")) {
                        versionType = "beta"
                    }
                    if (mod_version.contains("alpha")) {
                        versionType = "alpha"
                    }
                    uploadFile = jar
                    gameVersions = "${modrinth_versions}".split(", ").toList()
                    loaders = ["neoforge"]
                    dependencies {
                        var dependencies = proj.modrinth_dep_neoforge
                        if (!"${dependencies}".isEmpty()) {
                            "${dependencies}".split(", ").each {
                                required.project "${it}"
                            }
                        }
                        if (proj.hasProperty("optional_modrinth_dep_neoforge")) {
                            "${proj.optional_modrinth_dep_neoforge}".split(", ").each {
                                optional.project "${it}"
                            }
                        }
                    }
                }
                tasks.getByName("modrinth").dependsOn build
            }

            tasks.register("cleanLocalPublish") {
                group = "publishing"
                dependsOn ":common:clean", clean, publishToMavenLocal
                publishToMavenLocal.mustRunAfter ":common:clean", clean
            }

            tasks.register("cleanPublish") {
                group = "publishing"
                dependsOn ":common:clean", clean, publish
                publish.mustRunAfter ":common:clean", clean
            }

            if (hasCurseforge || hasModrinth) {
                tasks.register("upload") {
                    group = "publishing"
                    dependsOn ":common:clean", clean, build
                    dependsOn "curseforge", "modrinth"
                    build.mustRunAfter ":common:clean", clean
                    doLast {
                        if (proj.hasProperty("notifications")) {
                            notifications.add("neoforge")
                        }
                    }
                }

                tasks.register("uploadAndPublish") {
                    group = "publishing"
                    dependsOn upload, publish
                    publish.mustRunAfter upload
                }
            }

            extensions.configure(PublishingExtension) {
                it.publications {
                    it.register("mavenJava", MavenPublication) {
                        it.from components.java
                        it.artifactId = mod_id
                    }
                }
            }
        }
    }
}
