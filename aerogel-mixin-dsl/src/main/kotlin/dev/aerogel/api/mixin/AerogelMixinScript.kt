package dev.aerogel.api.mixin

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

@KotlinScript(
    fileExtension = "mixin.kts",
    compilationConfiguration = AerogelMixinScriptConfiguration::class
)
public abstract class AerogelMixinScript

public object AerogelMixinScriptConfiguration : ScriptCompilationConfiguration({
    defaultImports(
        "dev.aerogel.api.mixin.mixin",
        "dev.aerogel.api.mixin.InjectionPoint"
    )
    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
})
