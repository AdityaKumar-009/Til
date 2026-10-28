package com.flivoro.tile8auncher.ui.phone

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The same two-dimensional gradient-noise structure used by DiscoLauncher's
 * perlin.js, with stable per-cell gradients in place of Math.random().
 *
 * Disco tileList.js samples noise at elapsedMs/(2000+hash %1000),
 * y=(hash %500) or y+1000, and multiplies by 1.5 * 10 CSS px.
 */
internal object ClassicPhoneEditMotion {
    private fun tileHash(name: String, limit: Int): Int {
        var hash = 0
        name.forEach { hash = hash * 31 + it.code }
        return (hash.toLong().and(0xffffffffL) % limit).toInt()
    }

    private fun smooth(t: Double): Double =
        t * t * t * (t * (t * 6.0 - 15.0) + 10.0)

    private fun gradientDot(x: Double, y: Double, vx: Int, vy: Int, seed: Int): Double {
        var n = vx * 374761393 + vy * 668265263 + seed * 362437
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        val angle = ((n.toLong() and 0xffffffffL).toDouble() / 4294967296.0) * 2.0 * PI
        return (x - vx) * cos(angle) + (y - vy) * sin(angle)
    }

    private fun noise(x: Double, y: Double, seed: Int): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val sx = smooth(x - x0)
        val sy = smooth(y - y0)
        val tl = gradientDot(x, y, x0, y0, seed)
        val tr = gradientDot(x, y, x0 + 1, y0, seed)
        val bl = gradientDot(x, y, x0, y0 + 1, seed)
        val br = gradientDot(x, y, x0 + 1, y0 + 1, seed)
        val top = tl + sx * (tr - tl)
        val bottom = bl + sx * (br - bl)
        return top + sy * (bottom - top)
    }

    fun wobble(tileId: String, elapsedMs: Float, vertical: Boolean): Float {
        val speed = 2000 + tileHash(tileId, 1000)
        val row = tileHash(tileId, 500) + if (vertical) 1000 else 0
        return (15.0 * noise(elapsedMs.toDouble() / speed, row.toDouble(), 89)).toFloat()
    }
}
