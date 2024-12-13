package com.afoxxvi.asteoreffects.command

import com.afoxxvi.asteoreffects.api.AsteorEffect
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.util.Vector

object AsteorEffectCommand : BaseCommandHandler() {
    object Single : BaseCommand("single", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA).draw()
        }
    }

    object Circle : BaseCommand("circle", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            if (args.contains("-a")) AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .animateRing(3.0, 4, 40, 2, Math.toRadians(10.0))
            else AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA).drawCircle(3.0, 24)
        }
    }

    object Line : BaseCommand("line", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            if (args.contains("-a")) AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .animateLine(Vector(4, 4, 4), 40, 20, 2)
            else AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA).drawLine(Vector(4, 4, 4), 24)
        }
    }

    object Polygon : BaseCommand("polygon", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            if (args.contains("-a")) AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .animatePolygon(1.0, 0.1, 6, 6, 20, 2, 0.0, Math.toRadians(10.0))
            else AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA).drawPolygon(3.0, 6, 6)
        }
    }

    object Star : BaseCommand("star", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            if (args.contains("-a")) AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .animateStar(2.0, 0.1, 1.0, 0.05, 5, 5, 20, 2, 0.0, Math.toRadians(10.0))
            else AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA).drawStar(4.5, 3.0, 5, 5)
        }
    }

    object Sphere : BaseCommand("sphere", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            if (args.contains("-a")) AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .animateSphere(0.5, 0.1, 40, 10, 20, 2)
            else AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA).drawSphere(1.5, 240, 1.0)
        }
    }

    object Vortex : BaseCommand("vortex", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            if (args.contains("-a")) AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .animateVortex(1.0, 0.1, 0.5, 0.05, 80, Math.toRadians(360.0), 6, 40, 2)
            else AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA).drawVortex(3.0, 1.5, 20, 0.1, 6)
        }
    }

    object Triangle : BaseCommand("triangle", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            if (args.contains("-a")) AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .animateTriangle(Vector(3, 1, 0), Vector(0, 2, 4), 20, 20, 2)
            else AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .drawTriangle(Vector(3, 1, 0), Vector(0, 2, 4), 24)
        }
    }

    object Parallelogram : BaseCommand("parallelogram", true) {
        override fun runCommand(sender: CommandSender, args: Array<String>) {
            sender as Player
            if (args.contains("-a")) AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .animateParallelogram(Vector(3, 1, 0), Vector(0, 2, 4), 24, 20, 2)
            else AsteorEffect.create(Particle.DUST, sender.location).dustOptions(Color.AQUA)
                .drawParallelogram(Vector(3, 1, 0), Vector(0, 2, 4), 24)
        }
    }

    override fun onTabComplete(commandSender: CommandSender, command: Command, s: String, strings: Array<String>): List<String> {
        if (strings.size == 2) {
            return listOf("-a")
        }
        return super.onTabComplete(commandSender, command, s, strings)
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