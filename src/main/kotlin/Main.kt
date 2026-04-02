package org.example

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import java.util.UUID

class Main : JavaPlugin(), CommandExecutor, TabCompleter, Listener {
    private val luckyBoxInventories = mutableMapOf<UUID, LuckyBoxInventory>()

    override fun onEnable() {
        logger.info("Lucky Box 플러그인이 활성화되었습니다!")
        val cmd = getCommand("lucky_box")
        cmd?.setExecutor(this)
        cmd?.setTabCompleter(this)
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        logger.info("Lucky Box 플러그인이 비활성화되었습니다!")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender is Player) {
            val inventory = Bukkit.createInventory(null, 9, "Lucky Box") as Inventory
            val luckyBox = LuckyBoxInventory(this, inventory)
            luckyBoxInventories[sender.uniqueId] = luckyBox
            luckyBox.startAnimation(inventory, sender)
            sender.openInventory(inventory)
        } else {
            sender.sendMessage("Only players can use this command.")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String>? {
        return emptyList()
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title == "Lucky Box") {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.title == "Lucky Box") {
            event.isCancelled = true
        }
    }
}

class LuckyBoxInventory(private val plugin: Main, private val inventory: Inventory) : InventoryHolder {
    override fun getInventory(): Inventory = inventory

    private var pattern = 0  // 0 = pattern1, 1 = pattern2
    private var taskId: Int? = null

    fun startAnimation(inventory: Inventory, player: Player) {
        updatePattern(inventory, 0)

        taskId = object : BukkitRunnable() {
            override fun run() {
                if (!player.openInventory.topInventory.equals(inventory)) {
                    cancel()
                    return
                }
                pattern = 1 - pattern
                updatePattern(inventory, pattern)
            }
        }.runTaskTimer(plugin, 5L, 5L).taskId
    }

    private fun updatePattern(inventory: Inventory, patternType: Int) {
        val lightBluePane = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("")
                lore = null
            }
        }
        val yellowPane = ItemStack(Material.YELLOW_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("")
                lore = null
            }
        }

        if (patternType == 0) {
            // 패턴 1: 하늘색, 노란색, 하늘색, 노란색, 빈칸, 하늘색, 노란색, 하늘색, 노란색
            inventory.setItem(0, lightBluePane)
            inventory.setItem(1, yellowPane)
            inventory.setItem(2, lightBluePane)
            inventory.setItem(3, yellowPane)
            inventory.setItem(4, null)
            inventory.setItem(5, lightBluePane)
            inventory.setItem(6, yellowPane)
            inventory.setItem(7, lightBluePane)
            inventory.setItem(8, yellowPane)
        } else {
            // 패턴 2: 노란색, 하늘색, 노란색, 하늘색, 빈칸, 노란색, 하늘색, 노란색, 하늘색
            inventory.setItem(0, yellowPane)
            inventory.setItem(1, lightBluePane)
            inventory.setItem(2, yellowPane)
            inventory.setItem(3, lightBluePane)
            inventory.setItem(4, null)
            inventory.setItem(5, yellowPane)
            inventory.setItem(6, lightBluePane)
            inventory.setItem(7, yellowPane)
            inventory.setItem(8, lightBluePane)
        }
    }
}
