/*
 * Copyright 2020-2024 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.dsl

enum class DebCompression(
    internal val id: String,
    internal val maxLevel: Int,
) {
    GZIP("gzip", 9),
    XZ("xz", 9),
    ZSTD("zstd", 22),
    NONE("none", 0),
}
