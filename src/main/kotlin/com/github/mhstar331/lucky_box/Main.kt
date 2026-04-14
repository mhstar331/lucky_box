package com.github.mhstar331.lucky_box

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
import org.bukkit.event.inventory.InventoryCloseEvent
import net.kyori.adventure.text.Component
import java.util.UUID

class Main : JavaPlugin(), CommandExecutor, TabCompleter, Listener {
    private val luckyBoxInventories = mutableMapOf<UUID, LuckyBoxInventory>()
    private val configInventories = mutableSetOf<UUID>()

    override fun onEnable() {
        logger.info("Lucky Box 플러그인이 활성화되었습니다!")
        saveDefaultConfig()
        val cmd = getCommand("lucky_box")
        cmd?.setExecutor(this)
        cmd?.setTabCompleter(this)
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        logger.info("Lucky Box 플러그인이 비활성화되었습니다!")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("Usage: /lucky_box <open|config>")
            return true
        }

        when (args[0]) {
            "open" -> {
                val inventory = Bukkit.createInventory(null, 9, Component.text("Lucky Box"))
                val luckyBox = LuckyBoxInventory(this, inventory)
                luckyBoxInventories[sender.uniqueId] = luckyBox
                luckyBox.startAnimation(inventory, sender)
                sender.openInventory(inventory)
            }
            "config" -> {
                // 권한 확인: luckybox.admin 권한이 있는지 체크
                if (!sender.hasPermission("luckybox.admin")) {
                    sender.sendMessage(Component.text("§c이 명령어를 사용할 권한이 없습니다."))
                    return true
                }
                val configInventory = Bukkit.createInventory(null, 9, Component.text("Lucky Box Config"))
                loadConfigToInventory(configInventory)
                configInventories.add(sender.uniqueId)
                sender.openInventory(configInventory)
            }
            else -> sender.sendMessage("Usage: /lucky_box <open|config>")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String>? {
        if (args.size == 1) {
            val completions = mutableListOf<String>()

            // "open"은 luckybox.use 권한 체크
            if (sender.hasPermission("luckybox.use")) {
                completions.add("open")
            }

            // "config"는 오직 luckybox.admin 권한이 있는 사람에게만 추천
            if (sender.hasPermission("luckybox.admin")) {
                completions.add("config")
            }

            // 현재 입력 중인 글자(args[0])와 일치하는 것만 필터링해서 반환
            return completions.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() == Component.text("Lucky Box")) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.title() == Component.text("Lucky Box")) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title() == Component.text("Lucky Box Config")) {
            val player = event.player as Player
            if (configInventories.contains(player.uniqueId)) {
                saveConfigFromInventory(event.inventory)
                configInventories.remove(player.uniqueId)
            }
        }
    }

    private fun loadConfigToInventory(inventory: Inventory) {
        for (i in 0..8) {
            val item = config.getItemStack("lucky_box.items.$i")
            if (item != null) {
                inventory.setItem(i, item)
            }
        }
    }

    private fun saveConfigFromInventory(inventory: Inventory) {
        for (i in 0..8) {
            config.set("lucky_box.items.$i", inventory.getItem(i))
        }

        saveConfig()
        logger.info("Lucky Box 설정이 저장되었습니다!")
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
                displayName(Component.empty())
            }
        }
        val yellowPane = ItemStack(Material.YELLOW_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.empty())
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
