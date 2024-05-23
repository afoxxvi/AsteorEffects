package com.afoxxvi.asteoreffects.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

abstract class BaseCommandHandler : CommandExecutor, TabCompleter {
    protected val commands: MutableList<BaseCommand> = ArrayList()
    override fun onCommand(commandSender: CommandSender, command: Command, s: String, strings: Array<String>): Boolean {
        if (strings.isEmpty()) {
            commandSender.sendMessage("help")
            return true
        }
        commands.firstOrNull { it.command.equals(strings[0], ignoreCase = true) }?.let {
            if (it.checkCommand(commandSender)) {
                it.runCommand(commandSender, strings.copyOfRange(1, strings.size))
                return true
            } else {
                if (it.inGameOnly && commandSender !is Player) {
                    commandSender.sendMessage(Component.text("This command is in-game only.", NamedTextColor.RED))
                } else {
                    commandSender.sendMessage(Component.text("You don't have the permission.", NamedTextColor.RED))
                }
            }
            return true
        }
        commandSender.sendMessage(Component.text("Unknown command.", NamedTextColor.RED))
        return true
    }

    override fun onTabComplete(
        commandSender: CommandSender, command: Command, s: String, strings: Array<String>
    ): List<String> {
        if (strings.size == 1) {
            return commands.filter { it.command.startsWith(strings[0]) && it.checkCommand(commandSender) }.map { it.command }
        }
        commands.firstOrNull { it.command.equals(strings[0], ignoreCase = true) && it.checkCommand(commandSender) }
            ?.let { return it.getTabComplete(commandSender, strings.copyOfRange(1, strings.size)) }
        return emptyList()
    }

    abstract class BaseCommand(
        val command: String, val inGameOnly: Boolean, private val permission: String? = null
    ) {
        fun checkCommand(sender: CommandSender): Boolean {
            return (permission == null || sender.hasPermission(permission)) && (!inGameOnly || sender is Player)
        }

        abstract fun runCommand(sender: CommandSender, args: Array<String>)

        fun getTabComplete(sender: CommandSender, args: Array<String>): List<String> = emptyList()
    }
}