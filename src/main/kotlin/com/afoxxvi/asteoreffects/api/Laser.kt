@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.afoxxvi.asteoreffects.api

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger


/**
 * A whole class to create Guardian Lasers and Ender Crystal Beams using packets and reflection.<br></br>
 * Inspired by the API
 * [GuardianBeamAPI](https://www.spigotmc.org/resources/guardianbeamapi.18329)<br></br>
 * **1.9 -> 1.20.4**
 *
 * @see [GitHub repository](https://github.com/SkytAsul/GuardianBeam)
 *
 * @version 2.3.6
 * @author SkytAsul
 */
abstract class Laser protected constructor(start: Location, end: Location, duration: Int, distance: Int) {
    private val distanceSquared: Int
    protected val duration: Int
    protected var durationInTicks: Boolean = false
    protected var _start: Location
    protected var _end: Location

    private var plugin: Plugin? = null
    protected var main: BukkitRunnable? = null

    private var startMove: BukkitTask? = null
    private var endMove: BukkitTask? = null

    protected var show: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    private val seen: MutableSet<Player> = HashSet()

    private val executeEnd: MutableList<Runnable> = ArrayList(1)

    init {
        check(Packets.enabled) { "The Laser Beam API is disabled. An error has occurred during initialization." }
        require(start.world === end.world) { "Locations do not belong to the same worlds." }
        this._start = start.clone()
        this._end = end.clone()
        this.duration = duration
        distanceSquared = if (distance < 0) -1 else distance * distance
    }

    /**
     * Adds a runnable to execute when the laser reaches its final duration
     * @param runnable action to execute
     * @return this [Laser] instance
     */
    fun executeEnd(runnable: Runnable): Laser {
        executeEnd.add(runnable)
        return this
    }

    /**
     * Makes the duration provided in the constructor passed as ticks and not seconds
     * @return this [Laser] instance
     */
    fun durationInTicks(): Laser {
        durationInTicks = true
        return this
    }

    /**
     * Starts this laser.
     *
     *
     * It will make the laser visible for nearby players and start the countdown to the final duration.
     *
     *
     * Once finished, it will destroy the laser and execute all runnable passed with [Laser.executeEnd].
     * @param plugin plugin used to start the task
     */
    fun start(plugin: Plugin) {
        check(main == null) { "Task already started" }
        this.plugin = plugin
        main = object : BukkitRunnable() {
            var time: Int = 0

            override fun run() {
                try {
                    if (time == duration) {
                        cancel()
                        return
                    }
                    if (!durationInTicks || time % 20 == 0) {
                        for (p in _start.world.players) {
                            if (isCloseEnough(p)) {
                                if (show.add(p)) {
                                    sendStartPackets(p, !seen.add(p))
                                }
                            } else if (show.remove(p)) {
                                sendDestroyPackets(p)
                            }
                        }
                    }
                    time++
                } catch (e: ReflectiveOperationException) {
                    e.printStackTrace()
                }
            }

            @Synchronized
            @Throws(IllegalStateException::class)
            override fun cancel() {
                super.cancel()
                main = null
                try {
                    for (p in show) {
                        sendDestroyPackets(p)
                    }
                    show.clear()
                    executeEnd.forEach(Consumer { obj: Runnable -> obj.run() })
                } catch (e: ReflectiveOperationException) {
                    e.printStackTrace()
                }
            }
        }
        (main as BukkitRunnable).runTaskTimerAsynchronously(plugin, 0L, if (durationInTicks) 1L else 20L)
    }

    /**
     * Stops this laser.
     *
     *
     * This will destroy the laser for every player and start execute all runnable passed with [Laser.executeEnd]
     */
    fun stop() {
        checkNotNull(main) { "Task not started" }
        main!!.cancel()
    }

    val isStarted: Boolean
        /**
         * Gets laser status.
         * @return    `true` if the laser is currently running
         * (i.e. [.start] has been called and the duration is not over)
         */
        get() = main != null

    /**
     * Gets laser type.
     * @return LaserType enum constant of this laser
     */
    abstract fun getLaserType(): LaserType?

    /**
     * Instantly moves the start of the laser to the location provided.
     * @param location New start location
     * @throws ReflectiveOperationException if a reflection exception occurred during laser moving
     */
    @Throws(ReflectiveOperationException::class)
    abstract fun moveStart(location: Location)

    /**
     * Instantly moves the end of the laser to the location provided.
     * @param location New end location
     * @throws ReflectiveOperationException if a reflection exception occurred during laser moving
     */
    @Throws(ReflectiveOperationException::class)
    abstract fun moveEnd(location: Location)

    /**
     * Gets the start location of the laser.
     * @return where exactly is the start position of the laser located
     */
    fun getStart(): Location {
        return _start.clone()
    }

    /**
     * Gets the end location of the laser.
     * @return where exactly is the end position of the laser located
     */
    open fun getEnd(): Location {
        return _end.clone()
    }

    /**
     * Moves the start of the laser smoothly to the new location, within a given time.
     * @param location New start location to go to
     * @param ticks Duration (in ticks) to make the move
     * @param callback [Runnable] to execute at the end of the move (nullable)
     */
    fun moveStart(location: Location, ticks: Int, callback: Runnable?) {
        startMove = moveInternal(
            location, ticks, startMove, getStart(),
            { loc -> this.moveStart(loc) }, callback
        )
    }

    /**
     * Moves the end of the laser smoothly to the new location, within a given time.
     * @param location New end location to go to
     * @param ticks Duration (in ticks) to make the move
     * @param callback [Runnable] to execute at the end of the move (nullable)
     */
    fun moveEnd(location: Location, ticks: Int, callback: Runnable?) {
        endMove = moveInternal(
            location, ticks, endMove, getEnd(),
            { loc -> this.moveEnd(loc) }, callback
        )
    }

    private fun moveInternal(
        location: Location, ticks: Int, oldTask: BukkitTask?, from: Location,
        moveConsumer: ReflectiveConsumer<Location>, callback: Runnable?
    ): BukkitTask {
        require(ticks > 0) { "Ticks must be a positive value" }
        checkNotNull(plugin) { "The laser must have been started a least once" }
        if (oldTask != null && !oldTask.isCancelled) oldTask.cancel()
        return object : BukkitRunnable() {
            var xPerTick: Double = (location.x - from.x) / ticks
            var yPerTick: Double = (location.y - from.y) / ticks
            var zPerTick: Double = (location.z - from.z) / ticks
            var loc: Location = from.clone()
            var elapsed: Int = 0

            override fun run() {
                try {
                    loc.add(xPerTick, yPerTick, zPerTick)
                    moveConsumer.accept(loc)
                } catch (e: ReflectiveOperationException) {
                    e.printStackTrace()
                    cancel()
                    return
                }

                if (++elapsed == ticks) {
                    cancel()
                    callback?.run()
                }
            }
        }.runTaskTimer(plugin!!, 0L, 1L)
    }

    @Throws(ReflectiveOperationException::class)
    protected fun moveFakeEntity(location: Location, entityId: Int, fakeEntity: Any?) {
        if (fakeEntity != null) Packets.moveFakeEntity(fakeEntity, location)
        if (main == null) return
        val packet = if (fakeEntity == null) {
            Packets.createPacketMoveEntity(location, entityId)
        } else {
            Packets.createPacketMoveEntity(fakeEntity)
        }
        for (p in show) {
            Packets.sendPackets(p, packet)
        }
    }

    @Throws(ReflectiveOperationException::class)
    protected abstract fun sendStartPackets(p: Player?, hasSeen: Boolean)

    @Throws(ReflectiveOperationException::class)
    protected abstract fun sendDestroyPackets(p: Player?)

    protected open fun isCloseEnough(player: Player): Boolean {
        if (distanceSquared == -1) return true
        val location = player.location
        return getStart().distanceSquared(location) <= distanceSquared ||
                getEnd().distanceSquared(location) <= distanceSquared
    }

    open class GuardianLaser : Laser {
        private var createGuardianPacket: Any? = null
        private var createSquidPacket: Any? = null
        private var teamCreatePacket: Any? = null
        private lateinit var destroyPackets: Array<Any?>
        private var metadataPacketGuardian: Any? = null
        private var metadataPacketSquid: Any? = null
        private var fakeGuardianDataWatcher: Any? = null

        private val squidUUID: UUID = UUID.randomUUID()
        private val guardianUUID: UUID = UUID.randomUUID()
        private val squidID = Packets.generateEID()
        private val guardianID = Packets.generateEID()
        private var squid: Any? = null
        private var guardian: Any? = null

        private var targetID: Int
        private var targetUUID: UUID

        private var endEntity: LivingEntity? = null

        private var correctStart: Location? = null
        private var correctEnd: Location? = null

        /**
         * Creates a new Guardian Laser instance
         * @param start Location where laser will start
         * @param end Location where laser will end
         * @param duration Duration of laser in seconds (*-1 if infinite*)
         * @param distance Distance where laser will be visible (*-1 if infinite*)
         * @throws ReflectiveOperationException if a reflection exception occurred during Laser creation
         * @see Laser._start
         * @see .durationInTicks
         * @see .executeEnd
         * @see .GuardianLaser
         */
        constructor(start: Location, end: Location, duration: Int, distance: Int) : super(start, end, duration, distance) {
            initSquid()

            targetID = squidID
            targetUUID = squidUUID

            initLaser()
        }

        /**
         * Creates a new Guardian Laser instance
         * @param start Location where laser will start
         * @param endEntity Entity who the laser will follow
         * @param duration Duration of laser in seconds (*-1 if infinite*)
         * @param distance Distance where laser will be visible (*-1 if infinite*)
         * @throws ReflectiveOperationException if a reflection exception occurred during Laser creation
         * @see Laser._start
         * @see .durationInTicks
         * @see .executeEnd
         * @see .GuardianLaser
         */
        constructor(start: Location, endEntity: LivingEntity, duration: Int, distance: Int) : super(
            start,
            endEntity.location,
            duration,
            distance
        ) {
            targetID = endEntity.entityId
            targetUUID = endEntity.uniqueId

            initLaser()
        }

        @Throws(ReflectiveOperationException::class)
        private fun initLaser() {
            fakeGuardianDataWatcher = Packets.createFakeDataWatcher()
            Packets.initGuardianWatcher(fakeGuardianDataWatcher, targetID)
            if (Packets.version >= 17) {
                guardian = Packets.createGuardian(getCorrectStart(), guardianUUID, guardianID)
            }
            metadataPacketGuardian = Packets.createPacketMetadata(guardianID, fakeGuardianDataWatcher)

            teamCreatePacket = Packets.createPacketTeamCreate("noclip" + teamID.getAndIncrement(), squidUUID, guardianUUID)
            destroyPackets = Packets.createPacketsRemoveEntities(squidID, guardianID)
        }

        @Throws(ReflectiveOperationException::class)
        private fun initSquid() {
            if (Packets.version >= 17) {
                squid = Packets.createSquid(getCorrectEnd(), squidUUID, squidID)
            }
            metadataPacketSquid = Packets.createPacketMetadata(squidID, Packets.fakeSquidWatcher)
            Packets.setDirtyWatcher(Packets.fakeSquidWatcher)
        }

        @get:Throws(ReflectiveOperationException::class)
        private val guardianSpawnPacket: Any?
            get() {
                if (createGuardianPacket == null) {
                    createGuardianPacket = if (Packets.version < 17) {
                        Packets.createPacketEntitySpawnLiving(getCorrectStart(), Packets.mappings!!.guardianID, guardianUUID, guardianID)
                    } else {
                        Packets.createPacketEntitySpawnLiving(guardian)
                    }
                }
                return createGuardianPacket
            }

        @get:Throws(ReflectiveOperationException::class)
        private val squidSpawnPacket: Any?
            get() {
                if (createSquidPacket == null) {
                    createSquidPacket = if (Packets.version < 17) {
                        Packets.createPacketEntitySpawnLiving(getCorrectEnd(), Packets.mappings!!.squidID, squidUUID, squidID)
                    } else {
                        Packets.createPacketEntitySpawnLiving(squid)
                    }
                }
                return createSquidPacket
            }

        override fun getLaserType(): LaserType {
            return LaserType.GUARDIAN
        }

        /**
         * Makes the laser follow an entity (moving end location).
         *
         * This is done client-side by making the fake guardian follow the existing entity.
         * Hence, there is no consuming of server resources.
         *
         * @param entity living entity the laser will follow
         * @throws ReflectiveOperationException if a reflection operation fails
         */
        @Throws(ReflectiveOperationException::class)
        fun attachEndEntity(entity: LivingEntity) {
            require(entity.world === _start.world) { "Attached entity is not in the same world as the laser." }
            this.endEntity = entity
            setTargetEntity(entity.uniqueId, entity.entityId)
        }

        fun getEndEntity(): Entity? {
            return endEntity
        }

        @Throws(ReflectiveOperationException::class)
        private fun setTargetEntity(uuid: UUID, id: Int) {
            targetUUID = uuid
            targetID = id
            fakeGuardianDataWatcher = Packets.createFakeDataWatcher()
            Packets.initGuardianWatcher(fakeGuardianDataWatcher, targetID)
            metadataPacketGuardian = Packets.createPacketMetadata(guardianID, fakeGuardianDataWatcher)

            for (p in show) {
                Packets.sendPackets(p, metadataPacketGuardian)
            }
        }

        override fun getEnd(): Location {
            return if (endEntity == null) _end else endEntity!!.location
        }

        private fun getCorrectStart(): Location {
            if (correctStart == null) {
                correctStart = _start.clone()
                correctStart!!.subtract(0.0, 0.5, 0.0)
            }
            return correctStart!!
        }

        private fun getCorrectEnd(): Location {
            if (correctEnd == null) {
                correctEnd = _end.clone()
                correctEnd!!.subtract(0.0, 0.5, 0.0)

                val corrective = correctEnd!!.toVector().subtract(getCorrectStart().toVector()).normalize()
                if (java.lang.Double.isNaN(corrective.x)) corrective.setX(0)
                if (java.lang.Double.isNaN(corrective.y)) corrective.setY(0)
                if (java.lang.Double.isNaN(corrective.z)) corrective.setZ(0)
                // coordinates can be NaN when start and end are strictly equals
                correctEnd!!.subtract(corrective)
            }
            return correctEnd!!
        }

        override fun isCloseEnough(player: Player): Boolean {
            return player === endEntity || super.isCloseEnough(player)
        }

        @Throws(ReflectiveOperationException::class)
        override fun sendStartPackets(p: Player?, hasSeen: Boolean) {
            if (squid == null) {
                Packets.sendPackets(
                    p,
                    guardianSpawnPacket,
                    metadataPacketGuardian
                )
            } else {
                Packets.sendPackets(
                    p,
                    guardianSpawnPacket,
                    squidSpawnPacket,
                    metadataPacketGuardian,
                    metadataPacketSquid
                )
            }

            if (!hasSeen) Packets.sendPackets(p, teamCreatePacket)
        }

        @Throws(ReflectiveOperationException::class)
        override fun sendDestroyPackets(p: Player?) {
            Packets.sendPackets(p, *destroyPackets)
        }

        @Throws(ReflectiveOperationException::class)
        override fun moveStart(location: Location) {
            this._start = location.clone()
            correctStart = null

            createGuardianPacket = null // will force re-generation of spawn packet
            moveFakeEntity(getCorrectStart(), guardianID, guardian)

            if (squid != null) {
                correctEnd = null
                createSquidPacket = null
                moveFakeEntity(getCorrectEnd(), squidID, squid)
            }
        }

        @Throws(ReflectiveOperationException::class)
        override fun moveEnd(location: Location) {
            this._end = location.clone()
            createSquidPacket = null // will force re-generation of spawn packet
            correctEnd = null

            if (squid == null) {
                initSquid()
                for (p in show) {
                    Packets.sendPackets(p, squidSpawnPacket, metadataPacketSquid)
                }
            } else {
                moveFakeEntity(getCorrectEnd(), squidID, squid)
            }
            if (targetUUID !== squidUUID) {
                endEntity = null
                setTargetEntity(squidUUID, squidID)
            }
        }

        /**
         * Asks viewers' clients to change the color of this laser
         * @throws ReflectiveOperationException
         */
        @Throws(ReflectiveOperationException::class)
        fun callColorChange() {
            for (p in show) {
                Packets.sendPackets(p, metadataPacketGuardian)
            }
        }

        companion object {
            private val teamID = AtomicInteger(ThreadLocalRandom.current().nextInt(0, Int.MAX_VALUE))
        }
    }

    /**
     * Creates a new Ender Crystal Laser instance
     * @param start Location where laser will start. The Crystal laser do not handle decimal number, it will be rounded to blocks.
     * @param end Location where laser will end. The Crystal laser do not handle decimal number, it will be rounded to blocks.
     * @param duration Duration of laser in seconds (*-1 if infinite*)
     * @param distance Distance where laser will be visible (*-1 if infinite*)
     * @throws ReflectiveOperationException if a reflection exception occurred during Laser creation
     * @see .start
     * @see .durationInTicks
     * @see .executeEnd
     */
    class CrystalLaser(start: Location, end: Location, duration: Int, distance: Int) : Laser(
        start, Location(end.world, end.blockX.toDouble(), end.blockY.toDouble(), end.blockZ.toDouble()), duration,
        distance
    ) {
        private var createCrystalPacket: Any? = null
        private var metadataPacketCrystal: Any
        private val destroyPackets: Array<Any?>
        private val fakeCrystalDataWatcher = Packets.createFakeDataWatcher()

        private var crystal: Any? = null
        private val crystalID = Packets.generateEID()

        init {
            Packets.setCrystalWatcher(fakeCrystalDataWatcher, end)
            crystal = if (Packets.version < 17) {
                null
            } else {
                Packets.createCrystal(start, UUID.randomUUID(), crystalID)
            }
            metadataPacketCrystal = Packets.createPacketMetadata(crystalID, fakeCrystalDataWatcher)

            destroyPackets = Packets.createPacketsRemoveEntities(crystalID)
        }

        @get:Throws(ReflectiveOperationException::class)
        private val crystalSpawnPacket: Any?
            get() {
                if (createCrystalPacket == null) {
                    createCrystalPacket = if (Packets.version < 17) {
                        Packets.createPacketEntitySpawnNormal(_start, Packets.CRYSTAL_ID, Packets.crystalType, crystalID)
                    } else {
                        Packets.createPacketEntitySpawnNormal(crystal)
                    }
                }
                return createCrystalPacket
            }

        override fun getLaserType(): LaserType {
            return LaserType.ENDER_CRYSTAL
        }

        @Throws(ReflectiveOperationException::class)
        override fun sendStartPackets(p: Player?, hasSeen: Boolean) {
            Packets.sendPackets(p, crystalSpawnPacket)
            Packets.sendPackets(p, metadataPacketCrystal)
        }

        @Throws(ReflectiveOperationException::class)
        override fun sendDestroyPackets(p: Player?) {
            Packets.sendPackets(p, *destroyPackets)
        }

        @Throws(ReflectiveOperationException::class)
        override fun moveStart(location: Location) {
            this._start = location.clone()
            createCrystalPacket = null // will force re-generation of spawn packet
            moveFakeEntity(_start, crystalID, crystal)
        }

        @Throws(ReflectiveOperationException::class)
        override fun moveEnd(location: Location) {
            var loc = location
            loc = Location(loc.world, loc.blockX.toDouble(), loc.blockY.toDouble(), loc.blockZ.toDouble())

            if (_end == loc) return

            this._end = loc
            if (main != null) {
                Packets.setCrystalWatcher(fakeCrystalDataWatcher, loc)
                metadataPacketCrystal = Packets.createPacketMetadata(crystalID, fakeCrystalDataWatcher)
                for (p in show) {
                    Packets.sendPackets(p, metadataPacketCrystal)
                }
            }
        }
    }

    enum class LaserType {
        /**
         * Represents a laser from a Guardian entity.
         *
         *
         * It can be pointed to precise locations and
         * can track entities smoothly using [GuardianLaser.attachEndEntity]
         */
        GUARDIAN,

        /**
         * Represents a laser from an Ender Crystal entity.
         *
         *
         * Start and end locations are automatically rounded to integers (block locations).
         */
        ENDER_CRYSTAL;

        /**
         * Creates a new Laser instance, [GuardianLaser] or [CrystalLaser] depending on this enum value.
         * @param start Location where laser will start
         * @param end Location where laser will end
         * @param duration Duration of laser in seconds (*-1 if infinite*)
         * @param distance Distance where laser will be visible
         * @throws ReflectiveOperationException if a reflection exception occurred during Laser creation
         * @see Laser._start
         * @see Laser.durationInTicks
         * @see Laser.executeEnd
         */
        @Throws(ReflectiveOperationException::class)
        fun create(start: Location, end: Location, duration: Int, distance: Int): Laser {
            return when (this) {
                ENDER_CRYSTAL -> CrystalLaser(start, end, duration, distance)
                GUARDIAN -> GuardianLaser(start, end, duration, distance)
            }
        }
    }

    private object Packets {
        private val lastIssuedEID = AtomicInteger(2000000000)

        fun generateEID(): Int {
            return lastIssuedEID.getAndIncrement()
        }

        private var logger: Logger? = null
        var version: Int = 0
        private var versionMinor = 0
        private val npack = "net.minecraft.server." + Bukkit.getServer().javaClass.getPackage().name.replace(".", ",").split(",".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()[3]
        private val cpack = Bukkit.getServer().javaClass.getPackage().name + "."
        var mappings: ProtocolMappings? = null

        const val CRYSTAL_ID: Int = 51 // pre-1.13

        var crystalType: Any? = null
        private var squidType: Any? = null
        private var guardianType: Any? = null

        private var crystalConstructor: Constructor<*>? = null
        private var squidConstructor: Constructor<*>? = null
        private var guardianConstructor: Constructor<*>? = null

        private var watcherObject1: Any? = null // invisibility
        private var watcherObject2: Any? = null // spikes
        private var watcherObject3: Any? = null // attack id
        private var watcherObject4: Any? = null // crystal target
        private var watcherObject5: Any? = null // crystal baseplate

        private var watcherConstructor: Constructor<*>? = null
        private var watcherSet: Method? = null
        private var watcherRegister: Method? = null
        private var watcherDirty: Method? = null
        private var watcherPack: Method? = null

        private var blockPositionConstructor: Constructor<*>? = null

        private var packetSpawnLiving: Constructor<*>? = null
        private var packetSpawnNormal: Constructor<*>? = null
        private var packetRemove: Constructor<*>? = null
        private var packetTeleport: Constructor<*>? = null
        private var packetMetadata: Constructor<*>? = null
        private var packetTeam: Class<*>? = null

        private var createTeamPacket: Method? = null
        private var createTeam: Constructor<*>? = null
        private var createScoreboard: Constructor<*>? = null
        private var setTeamPush: Method? = null
        private var pushNever: Any? = null
        private var getTeamPlayers: Method? = null

        private var getHandle: Method? = null
        private var playerConnection: Field? = null
        private var sendPacket: Method? = null
        private var setLocation: Method? = null
        private var setUUID: Method? = null
        private var setID: Method? = null

        private var fakeSquid: Any? = null
        var fakeSquidWatcher: Any? = null

        private var nmsWorld: Any? = null

        var enabled: Boolean = false

        init {
            try {
                logger = object : Logger("GuardianBeam", null) {
                    override fun log(logRecord: LogRecord) {
                        logRecord.message = "[GuardianBeam] " + logRecord.message
                        super.log(logRecord)
                    }
                }
                logger?.setParent(Bukkit.getServer().logger)
                logger?.setLevel(Level.ALL)

                // e.g. Bukkit.getServer().getClass().getPackage().getName() -> org.bukkit.craftbukkit.v1_17_R1
                var versions = Bukkit.getServer().javaClass.getPackage().name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[3].substring(1).split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                version = versions[1].toInt() // 1.X
                if (version >= 17) {
                    // e.g. Bukkit.getBukkitVersion() -> 1.17.1-R0.1-SNAPSHOT
                    versions = Bukkit.getBukkitVersion().split("-R".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[0].split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    versionMinor = if (versions.size <= 2) 0 else versions[2].toInt()
                } else versionMinor = versions[2].substring(1).toInt() // 1.X.Y

                logger?.info("Found server version 1.$version.$versionMinor")

                mappings = ProtocolMappings.getMappings(version)
                if (mappings == null) {
                    mappings = ProtocolMappings.values()[ProtocolMappings.values().size - 1]
                    logger?.warning("Loaded not matching version of the mappings for your server version (1.$version.$versionMinor)")
                }
                logger?.info("Loaded mappings " + mappings!!.name)

                val entityTypesClass = getNMSClass("world.entity", "EntityTypes")
                val entityClass = getNMSClass("world.entity", "Entity")
                val crystalClass = getNMSClass("world.entity.boss.enderdragon", "EntityEnderCrystal")
                val squidClass = getNMSClass("world.entity.animal", "EntitySquid")
                val guardianClass = getNMSClass("world.entity.monster", "EntityGuardian")
                watcherObject1 = getField(entityClass, mappings!!.watcherFlags, null)
                watcherObject2 = getField(guardianClass, mappings!!.watcherSpikes, null)
                watcherObject3 = getField(guardianClass, mappings!!.watcherTargetEntity, null)
                watcherObject4 = getField(crystalClass, mappings!!.watcherTargetLocation, null)
                watcherObject5 = getField(crystalClass, mappings!!.watcherBasePlate, null)

                if (version >= 13) {
                    crystalType = entityTypesClass.getDeclaredField(mappings!!.crystalTypeName)[null]
                    if (version >= 17) {
                        squidType = entityTypesClass.getDeclaredField(mappings!!.squidTypeName!!)[null]
                        guardianType = entityTypesClass.getDeclaredField(mappings!!.guardianTypeName!!)[null]
                    }
                }

                val dataWatcherClass = getNMSClass("network.syncher", "DataWatcher")
                watcherConstructor = dataWatcherClass.getDeclaredConstructor(entityClass)
                if (version >= 18) {
                    watcherSet = dataWatcherClass.getDeclaredMethod("b", watcherObject1!!.javaClass, Any::class.java)
                    watcherRegister = dataWatcherClass.getDeclaredMethod("a", watcherObject1!!.javaClass, Any::class.java)
                } else {
                    watcherSet = getMethod(dataWatcherClass, "set")
                    watcherRegister = getMethod(dataWatcherClass, "register")
                }
                if (version >= 15) watcherDirty = getMethod(dataWatcherClass, "markDirty")
                if (version > 19 || (version == 19 && versionMinor >= 3)) watcherPack = dataWatcherClass.getDeclaredMethod("b")
                packetSpawnNormal = getNMSClass("network.protocol.game", "PacketPlayOutSpawnEntity").getDeclaredConstructor(
                    *if (version < 17) arrayOfNulls(0) else arrayOf(
                        getNMSClass("world.entity", "Entity")
                    )
                )
                packetSpawnLiving = if (version >= 19) packetSpawnNormal else getNMSClass(
                    "network.protocol.game",
                    "PacketPlayOutSpawnEntityLiving"
                ).getDeclaredConstructor(
                    *if (version < 17) arrayOfNulls(0) else arrayOf(
                        getNMSClass("world.entity", "EntityLiving")
                    )
                )
                packetRemove = getNMSClass(
                    "network.protocol.game",
                    "PacketPlayOutEntityDestroy"
                ).getDeclaredConstructor(if (version == 17 && versionMinor == 0) Int::class.javaPrimitiveType else IntArray::class.java)
                packetMetadata = getNMSClass("network.protocol.game", "PacketPlayOutEntityMetadata")
                    .getDeclaredConstructor(
                        *if (version < 19 || (version == 19 && versionMinor < 3)
                        ) arrayOf(Int::class.javaPrimitiveType, dataWatcherClass, Boolean::class.javaPrimitiveType)
                        else arrayOf(Int::class.javaPrimitiveType, MutableList::class.java)
                    )
                packetTeleport = getNMSClass(
                    "network.protocol.game",
                    "PacketPlayOutEntityTeleport"
                ).getDeclaredConstructor(*if (version < 17) arrayOfNulls(0) else arrayOf(entityClass))
                packetTeam = getNMSClass("network.protocol.game", "PacketPlayOutScoreboardTeam")

                blockPositionConstructor =
                    getNMSClass("core", "BlockPosition").getConstructor(
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )

                nmsWorld = Class.forName(cpack + "CraftWorld").getDeclaredMethod("getHandle").invoke(Bukkit.getWorlds()[0])

                squidConstructor = squidClass.declaredConstructors[0]
                if (version >= 17) {
                    guardianConstructor = guardianClass.declaredConstructors[0]
                    crystalConstructor = crystalClass.getDeclaredConstructor(
                        nmsWorld!!.javaClass.superclass,
                        Double::class.javaPrimitiveType,
                        Double::class.javaPrimitiveType,
                        Double::class.javaPrimitiveType
                    )
                }

                val entityConstructorParams = if (version < 14) arrayOf(nmsWorld) else arrayOf(
                    entityTypesClass.getDeclaredField(
                        mappings!!.squidTypeName!!
                    )[null], nmsWorld
                )
                fakeSquid = squidConstructor!!.newInstance(*entityConstructorParams)
                fakeSquidWatcher = createFakeDataWatcher()
                tryWatcherSet(fakeSquidWatcher, watcherObject1, 32.toByte())

                getHandle = Class.forName(cpack + "entity.CraftPlayer").getDeclaredMethod("getHandle")
                playerConnection = getNMSClass("server.level", "EntityPlayer")
                    .getDeclaredField(if (version < 17) "playerConnection" else (if (version < 20) "b" else "c"))
                playerConnection!!.setAccessible(true)
                sendPacket = getNMSClass("server.network", "PlayerConnection").getMethod(
                    if (version < 18) "sendPacket" else (if (version >= 20 && versionMinor >= 2) "b" else "a"),
                    getNMSClass("network.protocol", "Packet")
                )

                if (version >= 17) {
                    setLocation = entityClass.getDeclaredMethod(
                        if (version < 18) "setLocation" else "a",
                        Double::class.javaPrimitiveType,
                        Double::class.javaPrimitiveType,
                        Double::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType
                    )
                    setUUID = entityClass.getDeclaredMethod("a_", UUID::class.java)
                    setID = entityClass.getDeclaredMethod("e", Int::class.javaPrimitiveType)

                    createTeamPacket = packetTeam!!.getMethod(
                        "a", getNMSClass("world.scores", "ScoreboardTeam"),
                        Boolean::class.javaPrimitiveType
                    )

                    val scoreboardClass = getNMSClass("world.scores", "Scoreboard")
                    val teamClass = getNMSClass("world.scores", "ScoreboardTeam")
                    val pushClass = getNMSClass("world.scores", "ScoreboardTeamBase\$EnumTeamPush")
                    createTeam = teamClass.getDeclaredConstructor(scoreboardClass, String::class.java)
                    createScoreboard = scoreboardClass.getDeclaredConstructor()
                    setTeamPush = teamClass.getDeclaredMethod(mappings!!.teamSetCollision!!, pushClass)
                    pushNever = pushClass.getDeclaredField("b")[null]
                    getTeamPlayers = teamClass.getDeclaredMethod(mappings!!.teamGetPlayers!!)
                }

                enabled = true
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg =
                    "Laser Beam reflection failed to initialize. The util is disabled. Please ensure your version (" + Bukkit.getServer().javaClass.getPackage().name + ") is supported."
                if (logger == null) System.err.println(errorMsg)
                else logger!!.severe(errorMsg)
            }
        }

        @Throws(ReflectiveOperationException::class)
        fun sendPackets(p: Player?, vararg packets: Any?) {
            val connection = playerConnection!![getHandle!!.invoke(p)]
            for (packet in packets) {
                if (packet == null) continue
                sendPacket!!.invoke(connection, packet)
            }
        }

        @Throws(ReflectiveOperationException::class)
        fun createFakeDataWatcher(): Any {
            val watcher = watcherConstructor!!.newInstance(fakeSquid)
            if (version > 13) setField(watcher, "registrationLocked", false)
            return watcher
        }

        @Throws(ReflectiveOperationException::class)
        fun setDirtyWatcher(watcher: Any?) {
            if (version >= 15) watcherDirty!!.invoke(watcher, watcherObject1)
        }

        @Throws(ReflectiveOperationException::class)
        fun createSquid(location: Location, uuid: UUID?, id: Int): Any {
            val entity = squidConstructor!!.newInstance(squidType, nmsWorld)
            setEntityIDs(entity, uuid, id)
            moveFakeEntity(entity, location)
            return entity
        }

        @Throws(ReflectiveOperationException::class)
        fun createGuardian(location: Location, uuid: UUID?, id: Int): Any {
            val entity = guardianConstructor!!.newInstance(guardianType, nmsWorld)
            setEntityIDs(entity, uuid, id)
            moveFakeEntity(entity, location)
            return entity
        }

        @Throws(ReflectiveOperationException::class)
        fun createCrystal(location: Location, uuid: UUID?, id: Int): Any {
            val entity = crystalConstructor!!.newInstance(nmsWorld, location.x, location.y, location.z)
            setEntityIDs(entity, uuid, id)
            return entity
        }

        @Throws(ReflectiveOperationException::class)
        fun createPacketEntitySpawnLiving(location: Location, typeID: Int, uuid: UUID?, id: Int): Any {
            val packet = packetSpawnLiving!!.newInstance()
            setField(packet, "a", id)
            setField(packet, "b", uuid)
            setField(packet, "c", typeID)
            setField(packet, "d", location.x)
            setField(packet, "e", location.y)
            setField(packet, "f", location.z)
            setField(packet, "j", (location.yaw * 256.0f / 360.0f).toInt().toByte())
            setField(packet, "k", (location.pitch * 256.0f / 360.0f).toInt().toByte())
            if (version <= 14) setField(packet, "m", fakeSquidWatcher)
            return packet
        }

        @Throws(ReflectiveOperationException::class)
        fun createPacketEntitySpawnNormal(location: Location, typeID: Int, type: Any?, id: Int): Any {
            val packet = packetSpawnNormal!!.newInstance()
            setField(packet, "a", id)
            setField(packet, "b", UUID.randomUUID())
            setField(packet, "c", location.x)
            setField(packet, "d", location.y)
            setField(packet, "e", location.z)
            setField(packet, "i", (location.yaw * 256.0f / 360.0f).toInt())
            setField(packet, "j", (location.pitch * 256.0f / 360.0f).toInt())
            setField(packet, "k", if (version < 13) typeID else type)
            return packet
        }

        @Throws(ReflectiveOperationException::class)
        fun createPacketEntitySpawnLiving(entity: Any?): Any {
            return packetSpawnLiving!!.newInstance(entity)
        }

        @Throws(ReflectiveOperationException::class)
        fun createPacketEntitySpawnNormal(entity: Any?): Any {
            return packetSpawnNormal!!.newInstance(entity)
        }

        @Throws(ReflectiveOperationException::class)
        fun initGuardianWatcher(watcher: Any?, targetId: Int) {
            tryWatcherSet(watcher, watcherObject1, 32.toByte())
            tryWatcherSet(watcher, watcherObject2, java.lang.Boolean.FALSE)
            tryWatcherSet(watcher, watcherObject3, targetId)
        }

        @Throws(ReflectiveOperationException::class)
        fun setCrystalWatcher(watcher: Any?, target: Location) {
            val blockPosition =
                blockPositionConstructor!!.newInstance(target.blockX, target.blockY, target.blockZ)
            tryWatcherSet(watcher, watcherObject4, Optional.of(blockPosition))
            tryWatcherSet(watcher, watcherObject5, java.lang.Boolean.FALSE)
        }

        @Throws(ReflectiveOperationException::class)
        fun createPacketsRemoveEntities(vararg entitiesId: Int): Array<Any?> {
            val packets: Array<Any?>
            if (version == 17 && versionMinor == 0) {
                packets = arrayOfNulls(entitiesId.size)
                for (i in entitiesId.indices) {
                    packets[i] = packetRemove!!.newInstance(entitiesId[i])
                }
            } else {
                packets = arrayOf(packetRemove!!.newInstance(*arrayOf(entitiesId)))
            }
            return packets
        }

        @Throws(ReflectiveOperationException::class)
        fun createPacketMoveEntity(location: Location, entityId: Int): Any {
            val packet = packetTeleport!!.newInstance()
            setField(packet, "a", entityId)
            setField(packet, "b", location.x)
            setField(packet, "c", location.y)
            setField(packet, "d", location.z)
            setField(packet, "e", (location.yaw * 256.0f / 360.0f).toInt().toByte())
            setField(packet, "f", (location.pitch * 256.0f / 360.0f).toInt().toByte())
            setField(packet, "g", true)
            return packet
        }

        @Throws(ReflectiveOperationException::class)
        fun setEntityIDs(entity: Any?, uuid: UUID?, id: Int) {
            setUUID!!.invoke(entity, uuid)
            setID!!.invoke(entity, id)
        }

        @Throws(ReflectiveOperationException::class)
        fun moveFakeEntity(entity: Any?, location: Location) {
            setLocation!!.invoke(entity, location.x, location.y, location.z, location.pitch, location.yaw)
        }

        @Throws(ReflectiveOperationException::class)
        fun createPacketMoveEntity(entity: Any?): Any {
            return packetTeleport!!.newInstance(entity)
        }

        @Suppress("UNCHECKED_CAST")
        @Throws(ReflectiveOperationException::class)
        fun createPacketTeamCreate(teamName: String?, vararg entities: UUID): Any {
            val packet: Any
            if (version < 17) {
                packet = packetTeam!!.getDeclaredConstructor().newInstance()
                setField(packet, "a", teamName)
                setField(packet, "i", 0)
                setField(packet, "f", "never")
                val players = getField(packetTeam, "h", packet) as MutableCollection<String>
                for (entity in entities) players.add(entity.toString())
            } else {
                val team = createTeam!!.newInstance(createScoreboard!!.newInstance(), teamName)
                setTeamPush!!.invoke(team, pushNever)
                val players = getTeamPlayers!!.invoke(team) as MutableCollection<String>
                for (entity in entities) players.add(entity.toString())
                packet = createTeamPacket!!.invoke(null, team, true)
            }
            return packet
        }

        @Throws(ReflectiveOperationException::class)
        fun createPacketMetadata(entityId: Int, watcher: Any?): Any {
            return if (version < 19 || (version == 19 && versionMinor < 3)) {
                packetMetadata!!.newInstance(entityId, watcher, false)
            } else {
                packetMetadata!!.newInstance(entityId, watcherPack!!.invoke(watcher))
            }
        }

        @Throws(ReflectiveOperationException::class)
        private fun tryWatcherSet(watcher: Any?, watcherObject: Any?, watcherData: Any) {
            try {
                watcherSet!!.invoke(watcher, watcherObject, watcherData)
            } catch (ex: InvocationTargetException) {
                watcherRegister!!.invoke(watcher, watcherObject, watcherData)
                if (version >= 15) watcherDirty!!.invoke(watcher, watcherObject)
            }
        }

        /* Reflection utils */
        @Throws(NoSuchMethodException::class)
        private fun getMethod(clazz: Class<*>, name: String): Method {
            for (m in clazz.declaredMethods) {
                if (m.name == name) return m
            }
            throw NoSuchMethodException(name + " in " + clazz.name)
        }

        @Throws(ReflectiveOperationException::class)
        private fun setField(instance: Any, name: String, value: Any?) {
            val field = instance.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field[instance] = value
        }

        @Throws(ReflectiveOperationException::class)
        private fun getField(clazz: Class<*>?, name: String?, instance: Any?): Any {
            val field = clazz!!.getDeclaredField(name!!)
            field.isAccessible = true
            return field[instance]
        }

        @Throws(ClassNotFoundException::class)
        private fun getNMSClass(package17: String, className: String): Class<*> {
            return Class.forName((if (version < 17) npack else "net.minecraft.$package17") + "." + className)
        }

        enum class ProtocolMappings(
            val major: Int,
            open val watcherFlags: String?,
            val watcherSpikes: String,
            val watcherTargetEntity: String,
            val watcherTargetLocation: String,
            val watcherBasePlate: String,
            open val squidID: Int,
            open val guardianID: Int,
            open val guardianTypeName: String? = null,
            open val squidTypeName: String? = "SQUID",
            val crystalTypeName: String = "END_CRYSTAL",
            val teamSetCollision: String? = null,
            val teamGetPlayers: String? = null
        ) {
            V1_9(9, "Z", "bA", "bB", "b", "c", 94, 68),
            V1_10(10, V1_9),
            V1_11(11, V1_10),
            V1_12(12, V1_11),
            V1_13(13, "ac", "bF", "bG", "b", "c", 70, 28),
            V1_14(14, "W", "b", "bD", "c", "d", 73, 30),
            V1_15(15, "T", "b", "bA", "c", "d", 74, 31),
            V1_16(16, null, "b", "d", "c", "d", -1, 31) {
                override val squidID: Int
                    get() = if (versionMinor < 2) 74 else 81

                override val watcherFlags: String
                    get() = if (versionMinor < 2) "T" else "S"
            },
            V1_17(17, "Z", "b", "e", "c", "d", 86, 35, "K", "aJ", "u", "setCollisionRule", "getPlayerNameSet"),
            V1_18(18, null, "b", "e", "c", "d", 86, 35, "K", "aJ", "u", "a", "g") {
                override val watcherFlags: String
                    get() = if (versionMinor < 2) "aa" else "Z"
            },
            V1_19(19, null, "b", "e", "c", "d", 89, 38, null, null, "w", "a", "g") {
                override val watcherFlags: String
                    get() = if (versionMinor < 4) "Z" else "an"
                override val guardianID: Int
                    get() = if (versionMinor < 3) 38 else 39

                override val squidTypeName: String
                    get() = if (versionMinor < 3) "aM"
                    else if (versionMinor == 3) "aN"
                    else "aT"

                override val guardianTypeName: String
                    get() = if (versionMinor < 3) "N"
                    else if (versionMinor == 3) "O"
                    else "V"
            },
            V1_20(20, null, "b", "e", "c", "d", 89, 38, null, null, "B", "a", "g") {
                override val watcherFlags: String
                    get() = if (versionMinor < 2) "an" else "ao"
                override val guardianTypeName: String
                    get() = if (versionMinor < 3) "V" else "W"

                override val squidTypeName: String
                    get() = if (versionMinor < 3) "aT" else "aU"
            },
            ;

            constructor(major: Int, parent: ProtocolMappings) : this(
                major,
                parent.watcherFlags,
                parent.watcherSpikes,
                parent.watcherTargetEntity,
                parent.watcherTargetLocation,
                parent.watcherBasePlate,
                parent.squidID,
                parent.guardianID,
                parent.guardianTypeName,
                parent.squidTypeName,
                parent.crystalTypeName,
                parent.teamSetCollision,
                parent.teamGetPlayers
            )

            companion object {
                fun getMappings(major: Int): ProtocolMappings? {
                    for (map in values()) {
                        if (major == map.major) return map
                    }
                    return null
                }
            }
        }
    }

    fun interface ReflectiveConsumer<T> {
        @Throws(ReflectiveOperationException::class)
        fun accept(t: T)
    }
}