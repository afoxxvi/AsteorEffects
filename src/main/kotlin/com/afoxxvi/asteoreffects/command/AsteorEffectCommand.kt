package com.afoxxvi.asteoreffects.command

import com.afoxxvi.asteoreffects.api.AsteorEffect
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.util.Vector

object AsteorEffectCommand : BaseCommandHandler() {
    object Single : BaseCommand("single", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.REDSTONE, sender.location).dustOptions(Color.AQUA).draw()
        }
    }

    object Circle : BaseCommand("circle", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.REDSTONE, sender.location).dustOptions(Color.AQUA).drawCircle(3.0, 24)
        }
    }

    object Line : BaseCommand("line", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.REDSTONE, sender.location).dustOptions(Color.AQUA).drawLine(Vector(4, 4, 4), 24)
        }
    }

    object Polygon : BaseCommand("polygon", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.REDSTONE, sender.location).dustOptions(Color.AQUA).drawPolygon(3.0, 6, 6)
        }
    }

    object Star : BaseCommand("star", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.REDSTONE, sender.location).dustOptions(Color.AQUA).drawStar(4.5, 3.0, 5, 5)
        }
    }

    object Sphere : BaseCommand("sphere", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.REDSTONE, sender.location).dustOptions(Color.AQUA).drawSphere(1.5, 240, 1.0)
        }
    }

    object Vortex : BaseCommand("vortex", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.REDSTONE, sender.location).dustOptions(Color.AQUA).drawVortex(3.0, 1.5, 20, 0.1, 6)
        }
    }

    object Triangle : BaseCommand("triangle", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.REDSTONE, sender.location).dustOptions(Color.AQUA)
                .drawTriangle(Vector(3, 1, 0), Vector(0, 2, 4), 24)
        }
    }

    object Parallelogram : BaseCommand("parallelogram", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.REDSTONE, sender.location).dustOptions(Color.AQUA)
                .drawParallelogram(Vector(3, 1, 0), Vector(0, 2, 4), 24)
        }
    }

    init {
        commands.add(Single)
        commands.add(Circle)
        commands.add(Line)
        commands.add(Polygon)
        commands.add(Star)
        commands.add(Sphere)
        commands.add(Vortex)
        commands.add(Triangle)
        commands.add(Parallelogram)
    }
}