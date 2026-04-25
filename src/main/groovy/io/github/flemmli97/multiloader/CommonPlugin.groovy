package io.github.flemmli97.multiloader

import io.github.flemmli97.multiloader.utils.Conventions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

class CommonPlugin implements Plugin<Project> {

    @Override
    void apply(Project proj) {
        Properties props = new Properties()
        props.load(getClass().getResourceAsStream("/multiloader.properties"))
        proj.getLogger().lifecycle("Applying Multiloader plugin - version ${props.get("version")}");

        Conventions.apply(proj)
        proj.with {
            plugins.apply("net.neoforged.moddev")

            neoForge {
                neoFormVersion = neoForm_version
                def at = file("src/main/resources/META-INF/accesstransformer.cfg")
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
            }

            configurations.with {
                // Use this configuration if the dependency is only required in common
                create(Conventions.COMMON_DEPENDENCY).with {
                    compileClasspath.extendsFrom it
                }
            }

            repositories.with {
                maven {
                    name = "Fabric"
                    url = uri("https://maven.fabricmc.net")
                    content {
                        it.includeGroupAndSubgroups("net.fabricmc")
                    }
                }
            }

            dependencies.with {
                // This is only here for the EnvType annotation which can be present with fabric dependencies
                commonDependency "net.fabricmc:fabric-loader:${fabric_loader_version}"
                compileOnly("org.ow2.asm:asm-tree:9.6")
                compileOnly group: "org.spongepowered", name: "mixin", version: "0.8.7"
                compileOnly group: "io.github.llamalad7", name: "mixinextras-common", version: "0.5.0"
                annotationProcessor group: "io.github.llamalad7", name: "mixinextras-common", version: "0.5.0"
            }

            tasks.register("cleanLocalPublish") {
                group = "publishing"
                dependsOn clean, publishToMavenLocal
                publishToMavenLocal.mustRunAfter clean
            }

            tasks.register("cleanPublish") {
                group = "publishing"
                dependsOn clean, publish
                publish.mustRunAfter clean
            }

            if ((findProperty("with_publish") ?: "true").toBoolean()) {
                tasks.register("uploadAndPublish") {
                    group = "publishing"
                    dependsOn clean, publish
                    publish.mustRunAfter clean
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
