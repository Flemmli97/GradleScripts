package io.github.flemmli97.multiloader

import io.github.flemmli97.multiloader.utils.Changelog
import org.gradle.api.Project

class ModPublishingUtils {

    static Object propertyWithFallback(Project project, String name, String fallback) {
        return project.hasProperty(name) ? project."${name}" : project."${fallback}"
    }

    static String curseForgeLoaderMapping(String loader) {
        return switch (loader) {
            case "fabric" -> "Fabric"
            case "neoforge" -> "NeoForge"
            case "forge" -> "Forge"
            default -> ""
        }
    }

    /**
     * Groovy because dynamic typings. Can't really access the publishing plugins classes
     */
    static def applyModPublishingPlugins(Project project, String loader) {
        project.with {
            var hasCurseforge = tasks.findByName("curseforge") != null
            if (hasCurseforge) {
                curseforge {
                    apiKey = findProperty("curseApiToken") ?: "0"
                    it.project {
                        id = "${propertyWithFallback(project, "curseforge_id", "curseforge_id_${loader}")}"
                        "${project.property("curseforge_versions")}".split(", ").each {
                            addGameVersion "${it}"
                        }
                        addGameVersion curseForgeLoaderMapping(loader)
                        mainArtifact(jar.archiveFile) {
                            def txt = Changelog.changelog(project, 1).replace("\n-", "\n\n- ")
                            txt = txt + "\n\n" + "For past versions see: ${project.full_changelog}"
                            changelog = txt
                            changelogType = "markdown"
                            releaseType = "release"
                            if (project.mod_version.contains("beta")) {
                                releaseType = "beta"
                            }
                            if (project.mod_version.contains("alpha")) {
                                releaseType = "alpha"
                            }
                        }
                        var dependencies = project.property("curseforge_dep_${loader}")
                        var optional_dep = project.findProperty("optional_curseforge_dep_${loader}") ?: ""
                        if (!"${dependencies}".isEmpty() || !"${optional_dep}".isEmpty()) {
                            relations {
                                if (!"${dependencies}".isEmpty()) {
                                    "${dependencies}".split(", ").each {
                                        requiredDependency "${it}"
                                    }
                                }
                                if (!"${optional_dep}".isEmpty()) {
                                    "${optional_dep}".split(", ").each {
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
                    projectId = "${project.modrinth_id}"
                    versionNumber = version
                    versionName = jar.archiveFileName
                    changelog = Changelog.changelog(project, 1) + "\n\n" + "For past versions see: ${project.full_changelog}"
                    versionType = "release"
                    if (project.mod_version.contains("beta")) {
                        versionType = "beta"
                    }
                    if (project.mod_version.contains("alpha")) {
                        versionType = "alpha"
                    }
                    uploadFile = jar
                    gameVersions = "${project.modrinth_versions}".split(", ").toList()
                    loaders = [loader]
                    dependencies {
                        var dependencies = project.property("modrinth_dep_${loader}")
                        if (!"${dependencies}".isEmpty()) {
                            "${dependencies}".split(", ").each {
                                required.project "${it}"
                            }
                        }
                        var optional_dep = project.findProperty("optional_modrinth_dep_${loader}") ?: ""
                        if (!"${optional_dep}".isEmpty()) {
                            "${optional_dep}".split(", ").each {
                                optional.project "${it}"
                            }
                        }
                    }
                }
                tasks.getByName("modrinth").dependsOn build
            }

            if (hasCurseforge || hasModrinth) {
                tasks.register("upload") {
                    group = "publishing"
                    dependsOn ":common:clean", clean, build
                    build.mustRunAfter ":common:clean", clean
                    if (hasCurseforge) {
                        dependsOn "curseforge"
                    }
                    if (hasModrinth) {
                        dependsOn "modrinth"
                    }
                    doLast {
                        if (project.hasProperty("notifications")) {
                            project.notifications.add(loader)
                        }
                    }
                }

                tasks.register("uploadAndPublish") {
                    group = "publishing"
                    dependsOn upload, publish
                    publish.mustRunAfter upload
                }
            }
        }
    }
}
