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

val TablerCoffeeOff: ImageVector
    get() {
        if (_TablerCoffeeOff != null) return _TablerCoffeeOff!!

        _TablerCoffeeOff =
            ImageVector
                .Builder(
                    name = "coffee-off",
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
                        curveToRelative(0.73f, -0.565f, 1.783f, -0.923f, 3f, -0.99f)
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(8f, 3f)
                        curveToRelative(-0.194f, 0.14f, -0.364f, 0.305f, -0.506f, 0.49f)
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
                        moveTo(14f, 10f)
                        horizontalLineToRelative(3f)
                        verticalLineToRelative(3f)
                        moveToRelative(-0.257f, 3.743f)
                        arcToRelative(6f, 6f, 0f, false, true, -5.743f, 4.257f)
                        horizontalLineToRelative(-2f)
                        arcToRelative(6f, 6f, 0f, false, true, -6f, -6f)
                        verticalLineToRelative(-5f)
                        horizontalLineToRelative(7f)
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(20.116f, 16.124f)
                        arcToRelative(3f, 3f, 0f, false, false, -3.118f, -4.953f)
                    }
                    path(
                        fill = SolidColor(Color.Transparent),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(3f, 3f)
                        lineToRelative(18f, 18f)
                    }
                }.build()

        return _TablerCoffeeOff!!
    }

private var _TablerCoffeeOff: ImageVector? = null
