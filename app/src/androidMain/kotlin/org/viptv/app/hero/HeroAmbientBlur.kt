package org.viptv.app.hero

/** Three separable box passes approximate the ambient blur on a small decoded image. */
internal fun blurAmbientPixels(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(width > 0 && height > 0 && pixels.size == width * height && radius >= 0)
    var source = pixels.copyOf()
    var target = IntArray(pixels.size)
    val count = radius * 2 + 1
    repeat(3) {
        for (horizontal in listOf(true, false)) {
            val lines = if (horizontal) height else width
            val length = if (horizontal) width else height
            val step = if (horizontal) 1 else width
            for (line in 0 until lines) {
                val origin = if (horizontal) line * width else line
                val sums = IntArray(4)
                fun add(index: Int, direction: Int) {
                    val pixel = source[origin + index.coerceIn(0, length - 1) * step]
                    for (channel in 0..3) sums[channel] += ((pixel ushr (channel * 8)) and 255) * direction
                }
                for (index in -radius..radius) add(index, 1)
                for (index in 0 until length) {
                    var pixel = 0
                    for (channel in 0..3) pixel = pixel or ((sums[channel] / count) shl (channel * 8))
                    target[origin + index * step] = pixel
                    add(index - radius, -1)
                    add(index + radius + 1, 1)
                }
            }
            val swap = source
            source = target
            target = swap
        }
    }
    return source
}
