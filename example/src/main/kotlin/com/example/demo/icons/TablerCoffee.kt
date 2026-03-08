package com.example.demo.icons

/*
MIT License

Copyright (c) 2020-2025 Paweł Kuna

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TablerCoffee: ImageVector
    get() {
        if (_TablerCoffee != null) return _TablerCoffee!!

        _TablerCoffee =
            ImageVector
                .Builder(
                    name = "coffee",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(3f, 14f)
                        curveToRelative(0.83f, 0.642f, 2.077f, 1.017f, 3.5f, 1f)
                        curveToRelative(1.423f, 0.017f, 2.67f, -0.358f, 3.5f, -1f)
                        curveToRelative(0.83f, -0.642f, 2.077f, -1.017f, 3.5f, -1f)
                        curveToRelative(1.423f, -0.017f, 2.67f, 0.358f, 3.5f, 1f)
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(8f, 3f)
                        arcToRelative(2.4f, 2.4f, 0f, false, false, -1f, 2f)
                        arcToRelative(2.4f, 2.4f, 0f, false, false, 1f, 2f)
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(12f, 3f)
                        arcToRelative(2.4f, 2.4f, 0f, false, false, -1f, 2f)
                        arcToRelative(2.4f, 2.4f, 0f, false, false, 1f, 2f)
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(3f, 10f)
                        horizontalLineToRelative(14f)
                        verticalLineToRelative(5f)
                        arcToRelative(6f, 6f, 0f, false, true, -6f, 6f)
                        horizontalLineToRelative(-2f)
                        arcToRelative(6f, 6f, 0f, false, true, -6f, -6f)
                        verticalLineToRelative(-5f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(16.746f, 16.726f)
                        arcToRelative(3f, 3f, 0f, true, false, 0.252f, -5.555f)
                    }
                }.build()

        return _TablerCoffee!!
    }

private var _TablerCoffee: ImageVector? = null
