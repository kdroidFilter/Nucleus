/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.dsl

/** Publish mode for electron-builder output. */
enum class PublishMode(
    internal val id: String,
) {
    /** Disable auto-update publishing (no latest-*.yml generated). */
    Never("never"),

    /** Publish to GitHub/S3 if configured (detects git tag). */
    Auto("onTag"),

    /** Always publish even without git tag. */
    Always("always"),
}
