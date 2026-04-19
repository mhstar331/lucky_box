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
import java.util.UUID

class LuckyBox : JavaPlugin(), CommandExecutor, TabCompleter, Listener {
    companion object {
        @JvmStatic
        lateinit var instance: LuckyBox
    }
    private val luckyBoxInventories = mutableMapOf<UUID, LuckyBoxInventory>()
    private val configInventories = mutableSetOf<UUID>()

    override fun onEnable() {
        instance = this
        logger.info("Lucky Box 플러그인이 활성화되었습니다!")
        saveDefaultConfig()
        val cmd = getCommand("lucky_box")
        cmd?.setExecutor(this)
        cmd?.tabCompleter = this
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        logger.info("Lucky Box 플러그인이 비활성화되었습니다!")
    }
    // 설정값들을 한 번에 묶어서 반환하는 데이터 클래스 (Main 클래스 밖에 선언하거나 안에 선언)
    data class LuckyBoxCost(
        val enabled: Boolean,
        val material: Material,
        val amount: Int,
        val displayName: Component
    )

    // 설정을 불러오는 전용 함수
    private fun getCostSettings(): LuckyBoxCost {
        val enabled = config.getBoolean("lucky_box.cost.enabled", true)
        val materialName = config.getString("lucky_box.cost.item", "DIAMOND")
        val amount = config.getInt("lucky_box.cost.amount", 1)
        val rawDisplayName = config.getString("lucky_box.cost.display_name") ?: "translatable:item.minecraft.diamond"
        val material = Material.matchMaterial(materialName ?: "DIAMOND") ?: Material.DIAMOND
        // 핵심: translatable 접두사가 있으면 번역 컴포넌트로, 아니면 일반 텍스트로 변환
        val displayNameComponent = if (rawDisplayName.startsWith("translatable:")) {
            Component.translatable(rawDisplayName.removePrefix("translatable:"))
        } else {
            Component.text(rawDisplayName)
        }

        return LuckyBoxCost(enabled, material, amount, displayNameComponent)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("Usage: /lucky_box <open | config | cost | setcost | info>")
            return true
        }

        when (args[0].lowercase()) {
            "open" -> {
                val cost = getCostSettings()

                if (cost.enabled) {
                    if (!sender.inventory.containsAtLeast(ItemStack(cost.material), cost.amount)) {
                        val msg = Component.text("§c[Lucky Box] 아이템이 부족합니다! (필요: ")
                            .append(cost.displayName)
                            .append(Component.text(" §c${cost.amount}개)"))
                        sender.sendMessage(msg)
                        return true
                    }
                    sender.inventory.removeItem(ItemStack(cost.material, cost.amount))
                    val openMsg = Component.text("§e[Lucky Box] ")
                        .append(cost.displayName)
                        .append(Component.text(" §e${cost.amount}개를 사용하여 상자를 엽니다!"))
                    sender.sendMessage(openMsg)
                }
                if (!sender.hasPermission("luckybox.use")) {
                    sender.sendMessage(Component.text("§c이 명령어를 사용할 권한이 없습니다."))
                    return true
                }
                val inventory = Bukkit.createInventory(null, 9, Component.text("Lucky Box"))
                val luckyBox = LuckyBoxInventory(this, inventory)
                luckyBoxInventories[sender.uniqueId] = luckyBox
                luckyBox.startAnimation(inventory, sender)
                sender.openInventory(inventory)
            }
            "config" -> {
                if (!sender.hasPermission("luckybox.admin")) {
                    sender.sendMessage(Component.text("§c이 명령어를 사용할 권한이 없습니다."))
                    return true
                }
                val configInventory = Bukkit.createInventory(null, 54, Component.text("Lucky Box Config"))
                loadConfigToInventory(configInventory)
                configInventories.add(sender.uniqueId)
                sender.openInventory(configInventory)
            }
            "setcost" -> {
                if (!sender.hasPermission("luckybox.admin")) {
                    sender.sendMessage(Component.text("§c이 명령어를 사용할 권한이 없습니다."))
                    return true
                }

                // 최소 인자: /lb setcost <아이템> <개수> (총 3개)
                if (args.size < 3) {
                    sender.sendMessage(Component.text("§c사용법: /lucky_box setcost <아이템코드> <개수> [표시이름]"))
                    return true
                }

                val materialName = args[1].uppercase()
                val amount = args[2].toIntOrNull()

                if (amount == null || amount <= 0) {
                    sender.sendMessage(Component.text("§c개수는 1 이상의 숫자여야 합니다."))
                    return true
                }

                val material = Material.matchMaterial(materialName)
                if (material == null) {
                    sender.sendMessage(Component.text("§c존재하지 않는 아이템 코드입니다: $materialName"))
                    return true
                }

                // 표시이름 처리: 4번째 인자부터 합치기, 없으면 아이템 코드를 저장
                val displayName = if (args.size >= 4) {
                    args.slice(3 until args.size).joinToString(" ").replace("&", "§")
                } else {
                    "translatable:${material.translationKey()}" // 입력 안 하면 기본 아이템 이름 사용
                }
                val resultName = if (displayName.startsWith("translatable:")) {
                    Component.translatable(displayName.removePrefix("translatable:"))
                } else {
                    Component.text(displayName)
                }

                config.set("lucky_box.cost.item", material.name)
                config.set("lucky_box.cost.amount", amount)
                config.set("lucky_box.cost.display_name", displayName)
                saveConfig()

                val successMsg = Component.text("§a[Lucky Box] 비용 설정 완료! (아이템: ")
                    .append(resultName)
                    .append(Component.text("§a)"))
                sender.sendMessage(successMsg)
            }
            "cost" -> {
                if (!sender.hasPermission("luckybox.admin")) {
                    sender.sendMessage(Component.text("§c이 명령어를 사용할 권한이 없습니다."))
                    return true
                }

                if (args.size < 2) {
                    sender.sendMessage(Component.text("§c사용법: /lucky_box cost <on | off>"))
                    return true
                }

                val enable = when (args[1].lowercase()) {
                    "on" -> true
                    "off" -> false
                    else -> {
                        sender.sendMessage(Component.text("§c사용법: /lucky_box cost <on | off>"))
                        return true
                    }
                }

                config.set("lucky_box.cost.enabled", enable)
                saveConfig()

                val status = if (enable) "§a활성화" else "§c비활성화"
                sender.sendMessage(Component.text("§a[Lucky Box] 비용 시스템이 ${status}§a되었습니다."))
            }
            "info" -> {
                if (!sender.hasPermission("luckybox.admin")) {
                    sender.sendMessage(Component.text("§c이 명령어를 사용할 권한이 없습니다."))
                    return true
                }
                val cost = getCostSettings()
                sender.sendMessage(Component.text("§b§l[Lucky Box 설정 정보]"))
                sender.sendMessage(Component.text("§f- 비용 시스템: ${if (cost.enabled) "§a활성화" else "§c비활성화"}"))
                val itemInfo = Component.text("§f- 필요 아이템: §e")
                    .append(cost.displayName)
                    .append(Component.text(" §8(${cost.material})"))
                sender.sendMessage(itemInfo)
                sender.sendMessage(Component.text("§f- 필요 개수: §6${cost.amount}개"))
            }
            else -> sender.sendMessage("Usage: /lucky_box <open | config | cost | setcost | info>")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (!sender.hasPermission("luckybox.admin") && !sender.hasPermission("luckybox.use")) return emptyList()

        return when (args.size) {
            1 -> {
                val list = mutableListOf<String>()
                if (sender.hasPermission("luckybox.use")) list.add("open")
                if (sender.hasPermission("luckybox.admin")) {
                    list.add("config")
                    list.add("setcost")
                    list.add("cost")
                    list.add("info") // info 추가
                }
                list.filter { it.startsWith(args[0], ignoreCase = true) }
            }
            2 -> {
                if (args[0].equals("cost", ignoreCase = true) && sender.hasPermission("luckybox.admin")) {
                    listOf("on", "off").filter { it.startsWith(args[1], ignoreCase = true) }
                } else if (args[0].equals("setcost", ignoreCase = true) && sender.hasPermission("luckybox.admin")) {
                    Material.entries
                        .filter { it.isItem && it.name.startsWith(args[1], ignoreCase = true) }
                        .map { it.name }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    // 중복 로직을 처리할 공통 함수
    private fun handleInventoryRestriction(event: org.bukkit.event.inventory.InventoryEvent, title: Component) {
        // 1. 뽑기 창 (Lucky Box) - 모든 상호작용 방지
        if (title == Component.text("Lucky Box")) {
            if (event is InventoryClickEvent) event.isCancelled = true
            if (event is InventoryDragEvent) event.isCancelled = true
            return
        }

        // 2. 설정 창 (Lucky Box Config) - 실시간 저장
        if (title == Component.text("Lucky Box Config")) {
            object : BukkitRunnable() {
                override fun run() {
                    saveConfigFromInventory(event.inventory)
                }
            }.runTaskLater(this, 1L)
        }
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
    private val totalFlips = 30 // 총 애니메이션 횟수

    fun startAnimation(inventory: Inventory, player: Player) {
        // 1. Config에서 실제 아이템만 필터링 (AIR 무시 로직 유지)
        val configItems = mutableListOf<ItemStack>()
        for (i in 0..53) {
            val item = plugin.config.getItemStack("lucky_box.items.$i")
            if (item != null && item.type != Material.AIR && item.amount > 0) {
                configItems.add(item)
            }
        }

        // 아이템이 없으면 종료
        if (configItems.isEmpty()) {
            player.sendMessage(Component.text("§c[Lucky Box] 설정된 아이템이 없습니다! /lb config를 확인해주세요."))
            player.closeInventory()
            return
        }

        // 2. 점점 느려지는 루프 시작 (Ease-out 로직 유지)
        runVariableTickLoop(inventory, player, 2L, configItems)
    }

    private fun runVariableTickLoop(inventory: Inventory, player: Player, currentDelay: Long, configItems: List<ItemStack>) {
        object : BukkitRunnable() {
            override fun run() {
                if (player.openInventory.topInventory != inventory) return

                if (count < totalFlips) {
                    pattern = 1 - pattern

                    // 배경 패턴 + 중앙 아이템 실시간 변경 + 유리판 이름 변경 적용
                    updatePatternWithRandomItem(inventory, pattern, configItems)

                    // 소리 효과 (피치 상승 로직 유지)
                    player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f + (count.toFloat() / totalFlips))

                    count++
                    // 점점 느려지는 딜레이 계산
                    val nextDelay = if (count > totalFlips * 0.5) currentDelay + 1L else currentDelay
                    runVariableTickLoop(inventory, player, nextDelay, configItems)
                } else {
                    // 최종 결과 도출 및 지급
                    finishResult(inventory, player, configItems)
                }
            }
        }.runTaskLater(plugin, currentDelay)
    }

    private fun updatePatternWithRandomItem(inventory: Inventory, patternType: Int, configItems: List<ItemStack>) {
        // 유리판 이름 회색으로 설정 (LuckyBox)
        val displayName = Component.text("§8LuckyBox")

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
                // 중앙: 돌아가는 동안 리스트 중 랜덤 아이템 계속 변경 (AIR 제외됨)
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

        // 결과 확정 및 실제 아이템 지급
        inventory.setItem(4, resultItem)
        player.inventory.addItem(resultItem.clone())

        player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f)
        player.sendMessage(Component.text("§6[Lucky Box] §f당첨! §b[${resultItem.type.name}]§f 아이템이 지급되었습니다."))
    }
}