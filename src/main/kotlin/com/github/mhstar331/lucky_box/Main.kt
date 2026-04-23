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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.UUID
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class LuckyBox : JavaPlugin(), CommandExecutor, TabCompleter, Listener {
    companion object {
        @JvmStatic
        lateinit var instance: LuckyBox
    }
    val luckyBoxInventories = mutableMapOf<UUID, LuckyBoxInventory>()
    private val configInventories = mutableSetOf<UUID>()

    override fun onEnable() {
        instance = this
        logger.info("Lucky Box 플러그인이 활성화되었습니다!")
        saveDefaultConfig()
        val cmd = getCommand("lucky_box")
        cmd?.setExecutor(this)
        cmd?.tabCompleter = this
        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(LuckyBoxItem(this), this)
    }

    override fun onDisable() {
        logger.info("Lucky Box 플러그인이 비활성화되었습니다!")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("플레이어만 이 명령어를 사용할 수 있습니다.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("사용법: /lucky_box <config | item>", NamedTextColor.RED))
            return true
        }

        when (args[0].lowercase()) {
            "config" -> {
                if (!sender.hasPermission("luckybox.admin")) {
                    sender.sendMessage(Component.text("이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED))
                    return true
                }
                val configInventory = Bukkit.createInventory(null, 54, Component.text("Lucky Box Config"))
                loadConfigToInventory(configInventory)
                configInventories.add(sender.uniqueId)
                sender.openInventory(configInventory)
            }
            "item" -> {
                if (!sender.hasPermission("luckybox.admin")) {
                    sender.sendMessage(Component.text("이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED))
                    return true
                }
                val item = ItemStack(Material.PAPER).apply {
                    itemMeta = itemMeta?.apply {
                        // §6§l 대신 Adventure 스타일 적용
                        displayName(Component.text("럭키박스", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
                        setCustomModelData(1)
                    }
                }
                sender.inventory.addItem(item)
                sender.sendMessage(Component.text("럭키박스 아이템을 지급받았습니다!", NamedTextColor.GREEN))
                return true
            }
            else -> sender.sendMessage(Component.text("사용법: /lucky_box <config | item>", NamedTextColor.RED))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (!sender.hasPermission("luckybox.admin") && !sender.hasPermission("luckybox.use")) return emptyList()

        return when (args.size) {
            1 -> {
                val list = mutableListOf<String>()
                if (sender.hasPermission("luckybox.admin")) {
                    list.add("config")
                    list.add("item")
                }
                list.filter { it.startsWith(args[0], ignoreCase = true) }
            }
            else -> emptyList()
        }
    }

    private fun handleInventoryRestriction(event: org.bukkit.event.inventory.InventoryEvent, title: Component) {
        if (title == Component.text("Lucky Box")) {
            if (event is InventoryClickEvent) event.isCancelled = true
            if (event is InventoryDragEvent) event.isCancelled = true
            return
        }

        if (title == Component.text("Lucky Box Config")) {
            object : BukkitRunnable() {
                override fun run() {
                    saveConfigFromInventory(event.inventory)
                }
            }.runTaskLater(this, 1L)
        }
    }

    fun Player.setLuckyBoxResourcePack() = setResourcePack(
        "https://github.com/mhstar331/lucky_box/releases/latest/download/resourcepack.zip",
        "99FE8C1206257B74D09A97209FBDF95BEB9FAE57",
        false
    )

    @EventHandler
    fun playerJoinEvent(event: PlayerJoinEvent) {
        val player = event.player
        player.setLuckyBoxResourcePack()
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        handleInventoryRestriction(event, event.view.title())
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        handleInventoryRestriction(event, event.view.title())
    }

    private fun loadConfigToInventory(inventory: Inventory) {
        for (i in 0..53) {
            val item = config.getItemStack("lucky_box.items.$i")
            if (item != null) {
                inventory.setItem(i, item)
            }
        }
    }

    private fun saveConfigFromInventory(inventory: Inventory) {
        for (i in 0..53) {
            config.set("lucky_box.items.$i", inventory.getItem(i))
        }
        saveConfig()
        logger.info("Lucky Box 설정이 저장되었습니다!")
    }
}

class LuckyBoxInventory(private val plugin: LuckyBox, private val inventory: Inventory) : InventoryHolder {
    override fun getInventory(): Inventory = inventory

    private var pattern = 0
    private var count = 0
    private val totalFlips = 30

    fun startAnimation(inventory: Inventory, player: Player) {
        val configItems = mutableListOf<ItemStack>()
        for (i in 0..53) {
            val item = plugin.config.getItemStack("lucky_box.items.$i")
            if (item != null && item.type != Material.AIR && item.amount > 0) {
                configItems.add(item)
            }
        }

        if (configItems.isEmpty()) {
            player.sendMessage(Component.text("[Lucky Box] 설정된 아이템이 없습니다! /lucky_box config를 확인해주세요.", NamedTextColor.RED))
            player.closeInventory()
            return
        }

        runVariableTickLoop(inventory, player, 2L, configItems)
    }

    private fun runVariableTickLoop(inventory: Inventory, player: Player, currentDelay: Long, configItems: List<ItemStack>) {
        object : BukkitRunnable() {
            override fun run() {
                if (player.openInventory.topInventory != inventory) return

                if (count < totalFlips) {
                    pattern = 1 - pattern
                    updatePatternWithRandomItem(inventory, pattern, configItems)
                    player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f + (count.toFloat() / totalFlips))
                    count++
                    val nextDelay = if (count > totalFlips * 0.5) currentDelay + 1L else currentDelay
                    runVariableTickLoop(inventory, player, nextDelay, configItems)
                } else {
                    finishResult(inventory, player, configItems)
                }
            }
        }.runTaskLater(plugin, currentDelay)
    }

    private fun updatePatternWithRandomItem(inventory: Inventory, patternType: Int, configItems: List<ItemStack>) {
        // §8 대신 NamedTextColor 적용
        val displayName = Component.text("LuckyBox", NamedTextColor.DARK_GRAY)

        val lightBluePane = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { displayName(displayName) }
        }
        val yellowPane = ItemStack(Material.YELLOW_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { displayName(displayName) }
        }

        val p1 = if (patternType == 0) lightBluePane else yellowPane
        val p2 = if (patternType == 0) yellowPane else lightBluePane

        for (i in 0..8) {
            if (i == 4) {
                inventory.setItem(i, configItems.random())
            } else if (i % 2 == 0) {
                inventory.setItem(i, p1)
            } else {
                inventory.setItem(i, p2)
            }
        }
    }

    private fun finishResult(inventory: Inventory, player: Player, configItems: List<ItemStack>) {
        val resultItem = configItems.random()
        inventory.setItem(4, resultItem)
        player.inventory.addItem(resultItem.clone())

        val itemNameComponent = if (resultItem.itemMeta?.hasDisplayName() == true) {
            resultItem.itemMeta.displayName()!!
        } else {
            Component.translatable(resultItem.translationKey())
        }

        player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f)

        // 메시지 조립 (Legacy 코드 제거)
        val msg = Component.text("[Lucky Box] ", NamedTextColor.GOLD)
            .append(Component.text("당첨! ", NamedTextColor.WHITE))
            .append(Component.text("[", NamedTextColor.AQUA))
            .append(itemNameComponent)
            .append(Component.text("]", NamedTextColor.AQUA))
            .append(Component.text(" 아이템이 지급되었습니다.", NamedTextColor.WHITE))

        player.sendMessage(msg)
    }
}

class LuckyBoxItem(private val plugin: LuckyBox) : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            if (item.type == Material.PAPER && item.itemMeta?.hasCustomModelData() == true && item.itemMeta?.customModelData == 1) {
                if (!player.hasPermission("luckybox.use")) {
                    player.sendMessage(Component.text("이 아이템을 사용할 권한이 없습니다.", NamedTextColor.RED))
                    return
                }
                item.amount -= 1
                openLuckyBox(player)
                event.isCancelled = true
            }
        }
    }

    private fun openLuckyBox(player: Player) {
        val inventory = Bukkit.createInventory(null, 9, Component.text("Lucky Box"))
        val luckyBox = LuckyBoxInventory(plugin, inventory)
        plugin.luckyBoxInventories[player.uniqueId] = luckyBox
        luckyBox.startAnimation(inventory, player)
        player.openInventory(inventory)
        player.sendMessage(Component.text("[Lucky Box] 럭키박스를 열었습니다!", NamedTextColor.YELLOW))
    }
}