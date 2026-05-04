package io.github.flemmli97.multiloader

import com.diluv.schoomp.Webhook
import com.diluv.schoomp.message.Message
import com.diluv.schoomp.message.embed.Embed
import io.github.flemmli97.multiloader.utils.Changelog.changelog
import io.github.flemmli97.multiloader.utils.Conventions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.core.extra
import java.io.IOException

class DiscordHookPlugin : Plugin<Project> {

    lateinit var project: Project

    override fun apply(project: Project) {
        this.project = project
        project.extra["notifications"] = mutableListOf<String>()
        project.tasks.register("discordNotification") {
            doLast {
                @Suppress("unchecked_cast")
                discordNotif((project.extra.get("notifications") ?: listOf<String>()) as List<String>)
            }
        }
        project.subprojects.forEach { sub ->
            sub.afterEvaluate {
                val uploadTask = sub.tasks.findByName("upload")
                uploadTask?.finalizedBy("discordNotification")
                val publishTask = sub.tasks.findByName("uploadAndPublish")
                publishTask?.finalizedBy("discordNotification")
            }
        }
    }

    fun discordChangelog(): List<String> {
        val changelog = changelog(this.project, 1)
        val res = mutableListOf<String>()
        if (changelog.length < 1000) {
            res.add(changelog)
            return res
        }
        var temp = ""
        changelog.split("\n").forEach { line ->
            val line = line + "\n"
            if ((temp.length + line.length) >= 1000) {
                res.add(temp)
                temp = line
            } else {
                temp += line
            }
        }
        res.add(temp)
        return res
    }

    fun propertyWithFallback(name: String, fallback: String): String {
        return (this.project.findProperty(name) ?: this.project.property(fallback)) as String
    }

    fun discordNotif(loaders: List<String>) {
        this.project.logger.lifecycle("Sending notifications for loaders $loaders")
        if (loaders.isEmpty()) {
            return
        }

        try {
            val webhook = Webhook(
                this.project.property("discordHook") as String,
                propertyWithFallback("mod_name", "project_name") + " Upload"
            )

            val message = Message()
            val version: String = propertyWithFallback("curseforge_versions", "curse_versions").split(", ")[0]
            message.setUsername("Curseforge Release")
            var content = "${propertyWithFallback("mod_name", "project_name")} ${
                Conventions.getProperty(
                    this.project,
                    "mod_version"
                )
            } for Minecraft $version has been released!"
            if (this.project.hasProperty("discord_role")) {
                content = "<@&${this.project.property("discord_role")}> " + content
            }
            message.setContent(content)
            message.setAvatarUrl("https://cdn.discordapp.com/avatars/680540027255652407/e4b7a058b24843ae13389a9a3cc3ae8c.png?size=128")

            val embed = Embed()

            if (loaders.contains("fabric")) {
                var fileIDFabric = getCurseForgeId("fabric", "fabric")
                embed.addField(
                    "Get the fabric version here (When it is accepted)",
                    "${propertyWithFallback("curseforge_page", "curseforge_page_fabric")}/files/${fileIDFabric}",
                    false
                )
                fileIDFabric = getModrinthId("fabric")
                embed.addField(
                    "Modrinth version (fabric)",
                    "${this.project.property("modrinth_page")}/version/${fileIDFabric}",
                    false
                )
            }
            if (loaders.contains("forge")) {
                var fileIDForge = getCurseForgeId("forge", "forge")
                embed.addField(
                    "Get the neoforge version here (When it is accepted)",
                    "${propertyWithFallback("curseforge_page", "curseforge_page_forge")}/files/${fileIDForge}",
                    false
                )
                fileIDForge = getModrinthId("modrinth")
                embed.addField(
                    "Modrinth version (forge)",
                    "${this.project.property("modrinth_page")}/version/${fileIDForge}",
                    false
                )
            }
            if (loaders.contains("neoforge")) {
                var fileIDNeoForge = getCurseForgeId("neoforge", "neoforge")
                embed.addField(
                    "Get the neoforge version here (When it is accepted)",
                    "${propertyWithFallback("curseforge_page", "curseforge_page_neoforge")}/files/${fileIDNeoForge}",
                    false
                )
                fileIDNeoForge = getModrinthId("neoforge")
                embed.addField(
                    "Modrinth version (neoforge)",
                    "${this.project.property("modrinth_page")}/version/${fileIDNeoForge}",
                    false
                )
            }
            val changelog = discordChangelog()
            if (changelog.size == 1) {
                embed.addField("Change Log", "```md\n${changelog[0].ifEmpty { "Unavailable :(" }}```", false)
            } else {
                changelog.forEach {
                    embed.addField("Change Log", "```md\n${it}```", false)
                }
            }
            embed.setColor(0xFF8000)
            message.addEmbed(embed)

            webhook.sendMessage(message)
        } catch (e: IOException) {
            this.project.logger.error("Failed to push to the Discord webhook. $e")
        }
    }

    /**
     * Due to kotlin verbosity using this workaround
     */
    fun getCurseForgeId(subProject: String, loader: String): Any {
        val task = this.project.project(subProject).tasks.getByName(
            "curseforge${
                propertyWithFallback(
                    "curseforge_id",
                    "curseforge_id_${loader}"
                )
            }"
        )
        val field = task.property("mainArtifact")!!.javaClass.getDeclaredField("fileID")
        field.isAccessible = true
        return field.get(this)
    }

    /**
     * Due to kotlin verbosity using this workaround
     */
    fun getModrinthId(subProject: String): Any {
        val task = this.project.project(subProject).tasks.getByName("modrinth")
        val field = task.property("uploadInfo")!!.javaClass.getDeclaredField("id")
        field.isAccessible = true
        return field.get(this)
    }
}