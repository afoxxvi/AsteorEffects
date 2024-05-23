package com.afoxxvi.asteoreffects

import com.afoxxvi.asteoreffects.command.AsteorEffectCommand
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class AsteorEffects : JavaPlugin() {
    override fun onEnable() {
        super.onEnable()
        inst = this
        getCommand("asteoreffects")?.setExecutor(AsteorEffectCommand)
    }

    fun newTask(task: Runnable): BukkitTask {
        return server.scheduler.runTask(this, task)
    }

    fun newDelayTask(delay: Long, task: Runnable): BukkitTask {
        return server.scheduler.runTaskLater(this, task, delay)
    }

    fun newRepeatTask(delay: Long, period: Long, task: Runnable): BukkitTask {
        return server.scheduler.runTaskTimer(this, task, delay, period)
    }

    fun newAsyncTask(task: Runnable): BukkitTask {
        return server.scheduler.runTaskAsynchronously(this, task)
    }

    fun newDelayAsyncTask(delay: Long, task: Runnable): BukkitTask {
        return server.scheduler.runTaskLaterAsynchronously(this, task, delay)
    }

    fun newRepeatAsyncTask(delay: Long, period: Long, task: Runnable): BukkitTask {
        return server.scheduler.runTaskTimerAsynchronously(this, task, delay, period)
    }

    fun cancelTask(task: BukkitTask) {
        task.cancel()
    }

    companion object {
        lateinit var inst: AsteorEffects
    }
}