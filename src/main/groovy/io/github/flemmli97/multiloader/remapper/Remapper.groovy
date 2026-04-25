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

import java.nio.file.Files
import java.nio.file.Path

/**
 * Remaps mods in intermediary to official
 * Based on https://github.com/jaredlll08/CreateTweaker/blob/1.20.1/buildSrc/src/main/kotlin/com/blamejared/createtweaker/gradle/Remapper.kt
 */
abstract class Remapper {

    static Gson GSON = new GsonBuilder().create()

    static remap(TinyRemapperPlugin plugin, String minecraftVersion, Path input, Path output) {
        def tiny = fetchYarnMappings(plugin, minecraftVersion)
        new BufferedReader(new StringReader(tiny)).withCloseable { reader ->
            def memoryMappingTree = new MemoryMappingTree()
            def official = fetchClientMappings(plugin, minecraftVersion)
            ProGuardFileReader.read(new StringReader(official), "named", "official", memoryMappingTree)
            Tiny1FileReader.read(reader, memoryMappingTree)
            def writer = new StringWriter()
            memoryMappingTree.accept(new Tiny2FileWriter(writer, false))
            writer.close()
            def remapper = TinyRemapper.newRemapper()
                    .withMappings(TinyUtils.createTinyMappingProvider(new BufferedReader(new StringReader(writer.toString())), "intermediary", "named"))
                    .build()
            remapper.readInputs(input)
            new OutputConsumerPath.Builder(output).build().withCloseable {
                remapper.apply(it)
            }
            remapper.finish()
        }
    }

    static String fetchClientMappings(TinyRemapperPlugin plugin, String minecraftVersion) {
        def mapping = plugin.getFilesPath().resolve("mappings").resolve("official_${minecraftVersion}.tiny")
        if (!Files.exists(mapping)) {
            def manifest = fetchJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
            def versions = manifest.getAsJsonArray("versions")
            for (v in versions.iterator()) {
                def versionMeta = v.getAsJsonObject()
                if (versionMeta.getAsJsonPrimitive("id").getAsString() == minecraftVersion) {
                    def version = fetchJson(versionMeta.getAsJsonPrimitive("url").getAsString())
                    def downloads = version.getAsJsonObject("downloads")
                    def fetched = fetchFrom(downloads.getAsJsonObject("client_mappings").getAsJsonPrimitive("url").getAsString())
                    Files.createDirectories(mapping.getParent())
                    Files.writeString(mapping, fetched)
                    return fetched
                }
            }
            return ""
        } else {
            return Files.readString(mapping)
        }
    }

    static String fetchYarnMappings(TinyRemapperPlugin plugin, String minecraftVersion) {
        def mapping = plugin.getFilesPath().resolve("mappings").resolve("intermediary_${minecraftVersion}.tiny")
        if (!Files.exists(mapping)) {
            String fetched = fetchFrom("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/%s.tiny".formatted(minecraftVersion))
            Files.createDirectories(mapping.getParent())
            Files.writeString(mapping, fetched)
            return fetched
        } else {
            return Files.readString(mapping)
        }
    }

    static JsonObject fetchJson(String url) {
        return GSON.fromJson(fetchFrom(url), JsonObject)
    }

    static String fetchFrom(String url) {
        URLConnection connection = URI.create(url).toURL().openConnection()
        Reader reader = new InputStreamReader(connection.getInputStream())
        return reader.readLines().join("\n")
    }
}
