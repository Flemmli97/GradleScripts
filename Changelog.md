2.1.0
==========
- Update publishing plugins for new curseforge required fields

2.0.5
==========
- Make discord plugin optional even if added

2.0.4
==========
- Expose conventions as plugin

2.0.3
==========
- Remove discord hook name and avatar. Can set it via webhook in discord itself

2.0.2
==========
- Fix discord hook

2.0.1
==========
- Fix archivesName not set

2.0.0
==========
- Update to allow for 26.1
  - Starting now vanilla has no obfuscation
- Rewrite to use Plugin format instead of precompiled scripts
- Removed `requiredRuntime` config. Use `localRuntime` for not exposed runtime dependencies
- Add remapper plugin to allow intermediary mapped fabric mods to be used in common
  - Use following script for that
```groovy
// In the common build.gradle
plugins {
    id "io.github.flemmli97.multiloader.tiny-remapper"
}

remap {
    // Mapping version to choose. Should be lower than 26.1 since above there is none
    mappingVersion = "1.21.11"
}

dependencies {
    // All default configurations are supported
    // To prevent conflict with loader plugins the prefix yarn is used
    yarnCompileOnly "your_dependency"
    yarnCommonDependency // Version of commonDependency for common dependency that are also exposed to loader projects
    yarnLocalRuntime // Version of localRuntime
}
```
- Due to above removed `remapCommonArtifact` configuration
- Removed various deprecated props:
  - `mcversion` in mod meta files, use `minecraft_version`
  - `fabric_version` in meta and gradle props, use `fabric_api_version`
  - `loader_version` in mod meta files, use `neoforge_loader_version`
  - `curse_versions` props, use `curseforge_versions`
  - `curse_id_fabric` props, use `curseforge_id_fabric`
  - `curse_dep_fabric` props, use `curseforge_dep_fabric`
  - `optional_curse_dep_fabric` props, use `optional_curseforge_dep_fabric`
  - `curse_id_forge` props, use `curseforge_id_neoforge`
  - `curse_dep_forge` props which was still used for neoforge, use `curseforge_dep_neoforge`
  - `optional_curse_dep_forge` props, use `optional_curseforge_dep_neoforge`
  - `modrinth_dep_forge` props, use `modrinth_dep_neoforge`
  - `optional_modrinth_dep_forge` props, use `optional_modrinth_dep_neoforge`
- Add a forge plugin
