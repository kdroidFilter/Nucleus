/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus

import io.github.kdroidfilter.nucleus.desktop.application.dsl.JvmApplication
import io.github.kdroidfilter.nucleus.desktop.application.dsl.NativeApplication
import io.github.kdroidfilter.nucleus.desktop.application.internal.JvmApplicationInternal
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import javax.inject.Inject

abstract class NucleusExtension
    @Inject
    constructor(
        project: Project,
        private val objectFactory: ObjectFactory,
    ) : ExtensionAware {
        val dependencies = NucleusPlugin.Dependencies(project)

        internal var _isJvmApplicationInitialized = false
            private set
        val application: JvmApplication by lazy {
            _isJvmApplicationInitialized = true
            objectFactory.newInstance(JvmApplicationInternal::class.java, "main")
        }

        fun application(fn: Action<JvmApplication>) {
            fn.execute(application)
        }

        internal var _isNativeApplicationInitialized = false
            private set
        val nativeApplication: NativeApplication by lazy {
            _isNativeApplicationInitialized = true
            objectFactory.newInstance(NativeApplication::class.java, "main")
        }

        fun nativeApplication(fn: Action<NativeApplication>) {
            fn.execute(nativeApplication)
        }
    }
