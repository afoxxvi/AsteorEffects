package com.afoxxvi.asteoreffects.api

import com.afoxxvi.asteoreffects.AsteorEffects
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Vibration
import org.bukkit.Vibration.Destination
import org.bukkit.block.data.BlockData
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Suppress("MemberVisibilityCanBePrivate")
class AsteorEffect private constructor(var particle: Particle, var location: Location) {
    //particle parameters
    private var count: Int = 1
    private var offsetX: Double = 0.0
    private var offsetY: Double = 0.0
    private var offsetZ: Double = 0.0
    private var extra: Double = 1.0
    private var data: Any? = null

    //draw parameters
    private var direction: Vector = Vector(0, 1, 0)
        set(value) {
            field = value
            rotateAxis = Vector(-value.z, 0.0, value.x)
            rotateAngle = acos(value.y / value.length())
            if (rotateAxis.isZero) rotateAxis = Vector(0, 1, 0)
        }
    private var rotateAxis: Vector = Vector(0, 0, 0)
    private var rotateAngle: Double = 0.0

    init {
        require(location.world != null) { "Location world can not be null" }
    }

    fun count(count: Int) = this.also {
        it.count = count
    }

    fun offset(all: Double) = this.also {
        it.offsetX = all
        it.offsetY = all
        it.offsetZ = all
    }

    fun offset(x: Double, y: Double, z: Double) = this.also {
        it.offsetX = x
        it.offsetY = y
        it.offsetZ = z
    }

    fun offset(horizontal: Double, vertical: Double) = this.also {
        it.offsetX = horizontal
        it.offsetY = vertical
        it.offsetZ = horizontal
    }

    fun moveDirection(yaw: Float, pitch: Float) = this.also {
        val cosP = cos(Math.toRadians(pitch.toDouble()))
        moveDirection(
            -sin(Math.toRadians(yaw.toDouble())) * cosP, sin(Math.toRadians(pitch.toDouble())), cos(Math.toRadians(yaw.toDouble())) * cosP
        )
    }

    fun moveDirection(x: Double, y: Double, z: Double) = this.also {
        it.offsetX = x
        it.offsetY = y
        it.offsetZ = z
        it.count = 0
    }

    fun extra(extra: Double) = this.also {
        it.extra = extra
    }

    fun dustOptions(color: Color, size: Float = 1.0F) = this.also {
        it.data = Particle.DustOptions(color, size)
    }

    fun dustTransition(color: Color, color2: Color, size: Float = 1.0F) = this.also {
        it.data = Particle.DustTransition(color, color2, size)
    }

    fun itemStack(itemStack: ItemStack) = this.also {
        it.data = itemStack
    }

    fun blockData(blockData: BlockData) = this.also {
        it.data = blockData
    }

    fun vibration(destination: Destination, arrivalTime: Int) = this.also {
        it.data = Vibration(destination, arrivalTime)
    }

    fun data(data: Any) = this.also {
        it.data = data
    }

    fun direction(direction: Vector) = this.also {
        this.direction = direction
    }

    fun sync(consumer: (AsteorEffect) -> Unit) = AsteorEffects.inst.newTask { consumer(this) }

    fun async(consumer: (AsteorEffect) -> Unit) = AsteorEffects.inst.newAsyncTask { consumer(this) }

    private fun repeat(times: Long, consumer: (AsteorEffect) -> Unit): BukkitRunnable {
        return object : BukkitRunnable() {
            var runs = 0
            override fun run() {
                consumer(this@AsteorEffect)
                runs++
                if (runs >= times) {
                    cancel()
                }
            }
        }
    }


    fun syncRepeat(times: Long, interval: Long, consumer: (AsteorEffect) -> Unit) {
        repeat(times, consumer).runTaskTimer(AsteorEffects.inst, 0, interval)
    }

    fun asyncRepeat(times: Long, interval: Long, consumer: (AsteorEffect) -> Unit) {
        repeat(times, consumer).runTaskTimerAsynchronously(AsteorEffects.inst, 0, interval)
    }

    private fun doRotate(vector: Vector) {
        if (rotateAxis.isZero) return
        vector.rotateAroundAxis(rotateAxis, rotateAngle)
    }

    private fun drawRotated(vector: Vector) {
        doRotate(vector)
        draw(vector)
    }

    private fun draw(vector: Vector) {
        location.add(vector)
        draw()
        location.subtract(vector)
    }

    fun draw() {
        location.world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data)
    }

    fun drawCircle(radius: Double, points: Int) {
        val angle = 2 * Math.PI / points
        var currentAngle = 0.0
        for (i in 0 until points) {
            val x = radius * cos(currentAngle)
            val z = radius * sin(currentAngle)
            val vector = Vector(x, 0.0, z)
            drawRotated(vector)
            currentAngle += angle
        }
    }

    fun drawLine(to: Vector, points: Int, startInclusive: Boolean = true, endInclusive: Boolean = true) {
        for (i in (if (startInclusive) 0 else 1)..(if (endInclusive) points else points - 1)) {
            val ratio = i.toDouble() / points
            val offset = Vector(to.x * ratio, to.y * ratio, to.z * ratio)
            drawRotated(offset)
        }
    }

    fun drawPolygon(radius: Double, edges: Int, pointsPerEdge: Int, initialAngle: Double = 0.0) {
        val angle = 2 * Math.PI / edges
        var currentAngle = initialAngle
        for (i in 0 until edges) {
            val start = Vector(radius * cos(currentAngle), 0.0, radius * sin(currentAngle))
            currentAngle += angle
            val end = Vector(radius * cos(currentAngle), 0.0, radius * sin(currentAngle)).subtract(start)
            location.add(start)
            drawLine(end, pointsPerEdge + 1)
            location.subtract(start)
        }
    }

    fun drawStar(outerRadius: Double, innerRadius: Double, edges: Int, pointsPerEdge: Int, initialAngle: Double = 0.0) {
        val angle = Math.PI / edges
        var currentAngle = initialAngle
        for (i in 0 until edges) {
            val start = Vector(outerRadius * cos(currentAngle), 0.0, outerRadius * sin(currentAngle))
            currentAngle += angle
            val mid = Vector(innerRadius * cos(currentAngle), 0.0, innerRadius * sin(currentAngle))
            currentAngle += angle
            val end = Vector(outerRadius * cos(currentAngle), 0.0, outerRadius * sin(currentAngle)).subtract(mid)
            location.add(start)
            mid.subtract(start)
            drawLine(mid, pointsPerEdge)
            mid.add(start)
            location.subtract(start)
            location.add(mid)
            drawLine(end, pointsPerEdge)
            location.subtract(mid)
        }
    }

    fun drawSphere(radius: Double, points: Int, surfaceRate: Double = 1.0) {
        for (i in 0 until points) {
            //a random point on the surface of the sphere
            val theta = Random.nextDouble() * 2 * Math.PI
            val phi = Random.nextDouble() * Math.PI - Math.PI / 2
            val x = radius * cos(theta) * cos(phi)
            val y = radius * sin(phi)
            val z = radius * sin(theta) * cos(phi)
            val vector = Vector(x, y, z).multiply(radius)
            if (surfaceRate < 1.0 && Random.nextDouble() > surfaceRate) {
                vector.multiply(Random.nextDouble())
            }
            draw(vector)
        }
    }

    fun drawVortex(radius: Double, height: Double, points: Int, curve: Double, lines: Int) =
        drawVortex(radius, 0.0, height, 0.0, points, curve, lines)

    fun drawVortex(radius: Double, initRadius: Double, height: Double, initHeight: Double, points: Int, curve: Double, lines: Int) {
        val da = 2 * Math.PI / lines
        val dr = radius / points
        val dh = height / points
        var currentAngle = 0.0
        var currentRadius = initRadius
        var currentHeight = initHeight
        for (i in 0 until points) {
            currentRadius += dr
            currentHeight += dh
            for (j in 0..lines) {
                val vector = Vector(currentRadius * cos(currentAngle), currentHeight, currentRadius * sin(currentAngle))
                drawRotated(vector)
                currentAngle += da
            }
            currentAngle += curve
        }
    }

    fun drawPartLine(b: Vector, c: Vector, points: Int, i: Int, j: Int) {
        val vector = Vector(
            b.x * i.toDouble() / points + c.x * j.toDouble() / points,
            b.y * i.toDouble() / points + c.y * j.toDouble() / points,
            b.z * i.toDouble() / points + c.z * j.toDouble() / points
        )
        drawRotated(vector)
    }

    fun drawTriangle(b: Vector, c: Vector, points: Int) {
        for (total in 0..points) {
            for (i in 0..total) {
                drawPartLine(b, c, points, i, total - i)
            }
        }
    }

    fun drawParallelogram(b: Vector, c: Vector, points: Int) {
        for (total in 0 until points * 2) {
            for (i in (total - points).coerceAtLeast(0)..total.coerceAtMost(points)) {
                drawPartLine(b, c, points, i, total - i)
            }
        }
    }

    companion object {
        fun create(particle: Particle, location: Location): AsteorEffect = AsteorEffect(particle, location)
    }
}