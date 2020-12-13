package de.fayard.buildSrcLibs.internal

import com.squareup.kotlinpoet.*


internal data class Library(
    val group: String = "",
    val module: String = "",
    val version: String = ""
) {
    val name: String get() = module
    fun groupModuleVersion() = "$group:$module:$version"
    fun groupModuleUnderscore() = "$group:$module:_"
    fun groupModule() = "$group:$module"
    fun versionName(mode: VersionMode): String = PluginConfig.escapeLibsKt(
        when (mode) {
            VersionMode.MODULE -> module
            VersionMode.GROUP -> group
            VersionMode.GROUP_MODULE -> "${group}_$module"
        }
    )

    override fun toString() = groupModuleVersion()
}


internal class Deps(
    val libraries: List<Library>,
    val names: Map<Library, String>
)


internal enum class VersionMode {
    GROUP, GROUP_MODULE, MODULE
}


internal fun kotlinpoet(
    deps: Deps
): FileSpec {
    val libraries: List<Library> = deps.libraries
    val indent = "    "

    val libsProperties: List<PropertySpec> = libraries
        .distinctBy { it.groupModule() }
        .map { d ->
            val libValue = when {
                d.version == "none" -> CodeBlock.of("%S", d.groupModule())
                else -> CodeBlock.of("%S", d.groupModuleUnderscore())
            }
            constStringProperty(
                name = deps.names[d]!!,
                initializer = libValue,
                kdoc = null
            )
        }

    val libsTypeSpec = TypeSpec.objectBuilder("Libs")
        .addKdoc(PluginConfig.KDOC_LIBS)
        .addProperties(libsProperties)
        .build()


    return FileSpec.builder("", "Libs")
        .indent(indent)
        .addType(libsTypeSpec)
        .build()

}

internal fun List<Library>.checkModeAndNames(useFdqnByDefault: List<String>): Deps {
    val dependencies = this

    val modes: Map<Library, VersionMode> =
        dependencies.associateWith { d ->
            when {
                d.module in useFdqnByDefault -> VersionMode.GROUP_MODULE
                PluginConfig.escapeLibsKt(d.module) in useFdqnByDefault -> VersionMode.GROUP_MODULE
                else -> VersionMode.MODULE
            }
        }.toMutableMap()

    val versionNames = dependencies.associateWith { d -> d.versionName(modes.getValue(d)) }
    val sortedDependencies = dependencies.sortedBy { d: Library -> d.groupModule() }
    return Deps(sortedDependencies, versionNames)
}


internal fun constStringProperty(
    name: String,
    initializer: CodeBlock,
    kdoc: CodeBlock? = null
): PropertySpec = PropertySpec.builder(name, String::class)
    .addModifiers(KModifier.CONST)
    .initializer(initializer)
    .apply {
        if (kdoc != null) addKdoc(kdoc)
    }.build()
