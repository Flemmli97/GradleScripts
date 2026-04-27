plugins {
    groovy
    `kotlin-dsl`
    `maven-publish`
}

base.archivesName = rootProject.name
version = project.property("plugin_version") as String
group = project.property("maven_group") as String

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net")
        content {
            includeGroupByRegex("net.fabricmc.*")
            includeGroup("fabric-loom")
        }
    }
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diluv.schoomp:Schoomp:1.2.6")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("net.fabricmc:tiny-remapper:0.10.0")
    implementation("net.fabricmc:mapping-io:0.5.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Here for compilation only with typings
    compileOnly("net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:2.0.141")
    compileOnly("fabric-loom:fabric-loom.gradle.plugin:1.15-SNAPSHOT")
}

tasks.withType<ProcessResources>().configureEach {
    filesMatching("multiloader.properties") {
        expand(Pair("version", version))
    }
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to version
            )
        )
    }
}

tasks.compileGroovy {
    classpath = sourceSets.main.get().compileClasspath
}

tasks.compileKotlin {
    libraries.from(sourceSets.main.get().groovy.classesDirectory)
}

tasks.publish {
    dependsOn(tasks.clean)
}

gradlePlugin {
    plugins {
        create("discordHook") {
            id = "io.github.flemmli97.multiloader.discord_hook"
            implementationClass = "io.github.flemmli97.multiloader.DiscordHookPlugin"
        }
        create("common") {
            id = "io.github.flemmli97.multiloader.platform-common"
            implementationClass = "io.github.flemmli97.multiloader.CommonPlugin"
        }
        create("fabric") {
            id = "io.github.flemmli97.multiloader.platform-fabric"
            implementationClass = "io.github.flemmli97.multiloader.FabricPlugin"
        }
        create("legacyForge") {
            id = "io.github.flemmli97.multiloader.platform-legacyforge"
            implementationClass = "io.github.flemmli97.multiloader.LegacyForgePlugin"
        }
        create("neoforge") {
            id = "io.github.flemmli97.multiloader.platform-neoforge"
            implementationClass = "io.github.flemmli97.multiloader.NeoForgePlugin"
        }
        create("tinyMapper") {
            id = "io.github.flemmli97.multiloader.tiny-remapper"
            implementationClass = "io.github.flemmli97.multiloader.remapper.TinyRemapperPlugin"
        }
    }
}

publishing {
    repositories {
        var token: String? = (project.findProperty("maven.token") ?: System.getenv("BLAZING_COOP_MAVEN_TOKEN")) as String?
        if (token != null) {
            maven {
                uri("https://maven.blazing-coop.net/releases")
                credentials {
                    username = (project.findProperty("maven.user") ?: System.getenv("BLAZING_COOP_MAVEN_USER")) as String?
                    password = token
                }
            }
        }
        token = (project.findProperty("gpr.gitlab.token") ?: System.getenv("GPR_GITLAB_TOKEN")) as String?
        if (token != null) {
            maven {
                uri("https://gitlab.com/api/v4/projects/21830712/packages/maven")
                credentials {
                    username = (project.findProperty("gpr.user") ?: System.getenv("GPR_USER")) as String?
                    password = token
                }
            }
        }
    }
}