package io.github.flemmli97.multiloader.remapper

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader
import net.fabricmc.mappingio.format.tiny.Tiny1FileReader
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.TinyUtils
import java.io.*
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Remaps mods in yarn to official
 * Based on https://github.com/jaredlll08/CreateTweaker/blob/1.20.1/buildSrc/src/main/kotlin/com/blamejared/createtweaker/gradle/Remapper.kt
 */
object Remapper {

    var gson: Gson = GsonBuilder().create()

    fun remap(plugin: TinyRemapperPlugin, minecraftVersion: String, input: Path, output: Path) {
        val tiny = fetchYarnMappings(plugin, minecraftVersion)
        BufferedReader(StringReader(tiny)).use { reader ->
            val memoryMappingTree = MemoryMappingTree()
            val official = fetchClientMappings(plugin, minecraftVersion)
            ProGuardFileReader.read(StringReader(official), "named", "official", memoryMappingTree)
            Tiny1FileReader.read(reader as Reader, memoryMappingTree)
            val writer = StringWriter()
            memoryMappingTree.accept(Tiny2FileWriter(writer, false))
            writer.close()
            val remapper = TinyRemapper.newRemapper().withMappings(
                TinyUtils.createTinyMappingProvider(
                    BufferedReader(
                        StringReader(writer.toString())
                    ), "intermediary", "named"
                )
            ).build()
            remapper.readInputs(input)
            OutputConsumerPath.Builder(output).build().use { output ->
                remapper.apply(output)
            }
            remapper.finish()
        }
    }

    fun fetchClientMappings(plugin: TinyRemapperPlugin, minecraftVersion: String): String {
        val mapping = plugin.filesPath.resolve("mappings").resolve("official_$minecraftVersion.tiny")
        if (!Files.exists(mapping)) {
            val manifest = fetchJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
            val versions = manifest.getAsJsonArray("versions")
            for (v in versions.iterator()) {
                val versionMeta = v.getAsJsonObject()
                if (versionMeta.getAsJsonPrimitive("id").getAsString() == minecraftVersion) {
                    val version = fetchJson(versionMeta.getAsJsonPrimitive("url").getAsString())
                    val downloads = version.getAsJsonObject("downloads")
                    val fetched =
                        fetchFrom(downloads.getAsJsonObject("client_mappings").getAsJsonPrimitive("url").getAsString())
                    Files.createDirectories(mapping.parent)
                    Files.writeString(mapping, fetched)
                    return (fetched)
                }
            }
            return ""
        } else {
            return Files.readString(mapping)
        }
    }

    fun fetchYarnMappings(plugin: TinyRemapperPlugin, minecraftVersion: String): String {
        val mapping = plugin.filesPath.resolve("mappings").resolve("intermediary_$minecraftVersion.tiny")
        if (!Files.exists(mapping)) {
            val fetched = fetchFrom(
                "https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/$minecraftVersion.tiny"
            )
            Files.createDirectories(mapping.parent)
            Files.writeString(mapping, fetched)
            return fetched
        } else {
            return Files.readString(mapping)
        }
    }

    fun fetchJson(url: String): JsonObject {
        return gson.fromJson(fetchFrom(url), JsonObject::class.java)
    }

    fun fetchFrom(url: String): String {
        val connection = URI.create(url).toURL().openConnection()
        val reader: Reader = InputStreamReader(connection.getInputStream())
        return reader.readLines().joinToString("\n")
    }
}