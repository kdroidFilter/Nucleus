/*
 * Copyright 2020-2024 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.dsl

enum class RpmCompression(
    internal val payloadSuffix: String,
    internal val maxLevel: Int,
    internal val defaultLevel: Int,
) {
    GZIP("gzdio", 9, 9),
    XZ("xzdio", 9, 6),
    ZSTD("zstdio", 22, 19),
}
