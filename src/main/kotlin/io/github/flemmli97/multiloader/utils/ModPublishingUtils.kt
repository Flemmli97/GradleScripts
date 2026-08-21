package io.github.flemmli97.multiloader.utils

import io.github.flemmli97.multiloader.DiscordHookPlugin
import me.modmuss50.mpp.CurseForgePublishResult
import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ModrinthPublishResult
import me.modmuss50.mpp.PublishModTask
import me.modmuss50.mpp.PublishResult
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.internal.extensions.core.extra
import org.gradle.jvm.tasks.Jar

object ModPublishingUtils {

    fun applyModPublishingPlugins(project: Project, loader: String, jar: String = "jar") {
        fun prop(property: String, fallback: String? = null): String {
            return Conventions.getProperty(project, property, fallback)
        }
        with(project) {
            if (project.plugins.hasPlugin("com.matthewprenger.cursegradle")
                || project.plugins.hasPlugin("com.modrinth.minotaur")
            ) {
                project.logger.error(
                    """Legacy publishing plugins detected in project ${project.name}. 
                    |These are not (automatically) supported anymore!
                    |Please use https://github.com/modmuss50/mod-publish-plugin instead""".trimMargin()
                )
            }
            if (project.rootProject.plugins.findPlugin("me.modmuss50.mod-publish-plugin") == null) {
                return
            }
            plugins.apply("me.modmuss50.mod-publish-plugin")
            val curseforgeToken = "${findProperty("curseApiToken") ?: ""}"
            val modrinthApiToken = "${findProperty("modrinthApiToken") ?: ""}"
            extensions.configure(ModPublishExtension::class.java) {
                val jar = tasks.named(jar, Jar::class.java).get()
                file.set(jar.archiveFile)
                var txt = Changelog.changelog(project, 1).replace("\n-", "\n\n- ")
                txt = txt + "\n\n" + "For past versions see: ${project.property("full_changelog")}"
                changelog.set(txt)
                type.set(STABLE)
                val modVersion = project.property("mod_version") as String
                if (modVersion.contains("beta")) {
                    type.set(BETA)
                }
                if (modVersion.contains("alpha")) {
                    type.set(ALPHA)
                }
                displayName.set(jar.archiveFileName)
                modLoaders.add(loader)
                if (!curseforgeToken.isEmpty()) {
                    curseforge {
                        accessToken.set(curseforgeToken)
                        projectId.set(prop("curseforge_id", "curseforge_id_${loader}"))
                        "${project.property("curseforge_versions")}".split(", ").forEach {
                            if (it.startsWith("Java ")) {
                                javaVersions.add(JavaVersion.toVersion(it.substringAfter("Java ")))
                            } else if (it.lowercase() == "client") {
                                client.set(true)
                            } else if (it.lowercase() == "server") {
                                server.set(true)
                            } else {
                                minecraftVersions.add(it)
                            }
                        }
                        if (!client.getOrElse(false) && !server.getOrElse(false)) {
                            client.set(true)
                            server.set(true)
                        }
                        val dependencies = project.property("curseforge_dep_${loader}")
                        val optionalDep = project.findProperty("optional_curseforge_dep_${loader}") ?: ""
                        if ("$dependencies".isNotEmpty()) {
                            "$dependencies".split(", ").forEach {
                                requires(it)
                            }
                        }
                        if ("$optionalDep".isNotEmpty()) {
                            "$optionalDep".split(", ").forEach {
                                optional(it)
                            }
                        }
                    }
                }
                if (!modrinthApiToken.isEmpty()) {
                    modrinth {
                        accessToken.set(modrinthApiToken)
                        projectId.set("${project.property("modrinth_id")}")
                        var client = false
                        var server = false
                        "${project.property("modrinth_versions")}".split(", ").forEach {
                            if (it.lowercase() == "client") {
                                client = true
                            } else if (it.lowercase() == "server") {
                                server = true
                            } else {
                                minecraftVersions.add(it)
                            }
                        }
                        if (!client && !server) {
                            environment.set(CLIENT_AND_SERVER)
                        } else if (client) {
                            environment.set(CLIENT_ONLY)
                        } else {
                            environment.set(SERVER_ONLY)
                        }
                        val dependencies = project.property("modrinth_dep_${loader}")
                        val optionalDep = project.findProperty("optional_modrinth_dep_${loader}") ?: ""
                        if ("$dependencies".isNotEmpty()) {
                            "$dependencies".split(", ").forEach {
                                requires(it)
                            }
                        }
                        if ("$optionalDep".isNotEmpty()) {
                            "$optionalDep".split(", ").forEach {
                                optional(it)
                            }
                        }
                    }
                }
            }
            tasks.getByName("publishMods").dependsOn("build")

            tasks.register("upload") {
                group = "publishing"
                dependsOn(":common:clean", "clean", "build")
                tasks.getByName("build").mustRunAfter(":common:clean", "clean")
                dependsOn("publishMods")
                doLast {
                    if (project.rootProject.extra.has("notifications")) {
                        val uploadResult = mutableMapOf<String, String>()
                        @Suppress("UnstableApiUsage")
                        tasks.withType(PublishModTask::class.java).forEach {
                            if (it.result.isPresent) {
                                val res = PublishResult.fromJson(it.result.asFile.get().readText())
                                if (res is ModrinthPublishResult) {
                                    uploadResult["modrinth"] = res.id
                                } else if (res is CurseForgePublishResult) {
                                    uploadResult["curseforge"] = "${res.fileId}"
                                }
                            }
                        }
                        DiscordHookPlugin.get(project)[loader] = uploadResult
                    }
                }
            }

            tasks.register("uploadAndPublish") {
                group = "publishing"
                dependsOn("upload", "publish")
                tasks.getByName("publish").mustRunAfter("upload")
            }
        }
    }
}