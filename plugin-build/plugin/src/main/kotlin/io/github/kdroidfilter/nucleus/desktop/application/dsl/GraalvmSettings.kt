package io.github.kdroidfilter.nucleus.desktop.application.dsl

import io.github.kdroidfilter.nucleus.internal.utils.new
import io.github.kdroidfilter.nucleus.internal.utils.notNullProperty
import io.github.kdroidfilter.nucleus.internal.utils.nullableProperty
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JvmVendorSpec
import javax.inject.Inject

abstract class GraalvmSettings
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val isEnabled: Property<Boolean> = objects.notNullProperty(false)

        @Suppress("MagicNumber")
        val javaLanguageVersion: Property<Int> = objects.notNullProperty(25)
        val jvmVendor: Property<JvmVendorSpec> = objects.nullableProperty()
        val imageName: Property<String> = objects.nullableProperty()
        val march: Property<String> = objects.notNullProperty("native")
        val buildArgs: ListProperty<String> = objects.listProperty(String::class.java)
        val nativeImageConfigBaseDir: DirectoryProperty = objects.directoryProperty()
        val macOS: GraalvmMacOSSettings = objects.new()

        fun macOS(fn: Action<GraalvmMacOSSettings>) {
            fn.execute(macOS)
        }
    }

abstract class GraalvmMacOSSettings
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val cStubsSrc: RegularFileProperty = objects.fileProperty()
        val minimumSystemVersion: Property<String> = objects.notNullProperty("12.0")
    }
