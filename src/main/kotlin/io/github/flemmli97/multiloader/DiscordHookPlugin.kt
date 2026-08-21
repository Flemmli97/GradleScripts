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
import kotlin.random.Random

typealias NotificationData = MutableMap<String, Map<String, String>>

class DiscordHookPlugin : Plugin<Project> {

    lateinit var project: Project

    companion object {

        @Suppress("unchecked_cast")
        fun get(project: Project): NotificationData {
            return (project.rootProject.extra.get("notifications")
                ?: mapOf<String, Map<String, String>>()) as NotificationData
        }
    }

    override fun apply(project: Project) {
        this.project = project
        if (!this.project.hasProperty("discordHook")) {
            this.project.logger.lifecycle("discordHook property not set. Ignoring DiscordHookPlugin plugin")
            return
        }
        project.rootProject.extra["notifications"] = mutableMapOf<String, Map<String, String>>()

        discordNotif(this.loaderProjects(), -1)

        project.tasks.register("discordNotification") {
            doLast {
                @Suppress("unchecked_cast")
                discordNotif(get(project))
            }
        }
        project.tasks.register("discordNotificationTest") {
            doLast {
                discordNotif(loaderProjects(), 0)
            }
        }
        project.subprojects.forEach { sub ->
            sub.afterEvaluate {
                val uploadTask = sub.tasks.findByName("upload")
                uploadTask?.finalizedBy(project.tasks.named("discordNotification"))
                val publishTask = sub.tasks.findByName("uploadAndPublish")
                publishTask?.finalizedBy(project.tasks.named("discordNotification"))
            }
        }
    }

    fun loaderProjects(): Map<String, Map<String, String>> {
        return this.project.subprojects.associate { p ->
            p.name.lowercase() to mapOf(
                "curseforge" to "${Random.nextInt()}",
                "modrinth" to "${Random.nextInt()}"
            )
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

    fun discordNotif(loaders: Map<String, Map<String, String>>, dummy: Int? = null) {
        if (dummy != -1) {
            this.project.logger.lifecycle("Sending notifications for loaders ${loaders.keys}")
        }
        if (loaders.isEmpty()) {
            return
        }

        try {
            var hook = this.project.property("discordHook")
            if (dummy == 0) {
                hook = this.project.property("discordHookTest")
            }
            val webhook = Webhook(
                "$hook",
                propertyWithFallback("mod_name", "project_name") + " Upload"
            )

            val message = Message()
            val version: String = propertyWithFallback("curseforge_versions", "curse_versions").split(", ")[0]
            var content = "${propertyWithFallback("mod_name", "project_name")} ${
                Conventions.getProperty(
                    this.project,
                    "mod_version"
                )
            } for Minecraft $version has been released!"
            if (this.project.hasProperty("discord_role")) {
                content = "<@&${this.project.property("discord_role")}> " + content
            }
            if (dummy != null) {
                content = "$content  \nThis is a dummy upload!"
            }
            message.setContent(content)

            val embed = Embed()

            if (loaders.contains("fabric")) {
                var fileIDFabric = loaders["fabric"]!!["curseforge"] ?: 0
                embed.addField(
                    "Get the fabric version here (When it is accepted)",
                    "${propertyWithFallback("curseforge_page", "curseforge_page_fabric")}/files/${fileIDFabric}",
                    false
                )
                fileIDFabric = loaders["fabric"]!!["modrinth"] ?: 0
                embed.addField(
                    "Modrinth version (fabric)",
                    "${this.project.property("modrinth_page")}/version/${fileIDFabric}",
                    false
                )
            }
            if (loaders.contains("forge")) {
                var fileIDForge = loaders["forge"]!!["curseforge"] ?: 0
                embed.addField(
                    "Get the neoforge version here (When it is accepted)",
                    "${propertyWithFallback("curseforge_page", "curseforge_page_forge")}/files/${fileIDForge}",
                    false
                )
                fileIDForge = loaders["forge"]!!["modrinth"] ?: 0
                embed.addField(
                    "Modrinth version (forge)",
                    "${this.project.property("modrinth_page")}/version/${fileIDForge}",
                    false
                )
            }
            if (loaders.contains("neoforge")) {
                var fileIDNeoForge = loaders["neoforge"]!!["curseforge"] ?: 0
                embed.addField(
                    "Get the neoforge version here (When it is accepted)",
                    "${propertyWithFallback("curseforge_page", "curseforge_page_neoforge")}/files/${fileIDNeoForge}",
                    false
                )
                fileIDNeoForge = loaders["neoforge"]!!["modrinth"] ?: 0
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
            if (dummy != -1) {
                webhook.sendMessage(message)
            }
        } catch (e: IOException) {
            this.project.logger.error("Failed to push to the Discord webhook. $e")
        }
    }
}