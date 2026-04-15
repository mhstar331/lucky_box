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
    // 설정값들을 한 번에 묶어서 반환하는 데이터 클래스 (Main 클래스 밖에 선언하거나 안에 선언)
    data class LuckyBoxCost(
        val enabled: Boolean,
        val material: Material,
        val amount: Int,
        val displayName: String
    )

    // 설정을 불러오는 전용 함수
    private fun getCostSettings(): LuckyBoxCost {
        val enabled = config.getBoolean("lucky_box.cost.enabled", true)
        val materialName = config.getString("lucky_box.cost.item", "DIAMOND")
        val amount = config.getInt("lucky_box.cost.amount", 1)
        val displayName: String = config.getString("lucky_box.cost.display_name") ?: "&6&l다이아몬드"
        val material = Material.matchMaterial(materialName ?: "DIAMOND") ?: Material.DIAMOND

        return LuckyBoxCost(enabled, material, amount, displayName)
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
                        sender.sendMessage(Component.text("§c[Lucky Box] 아이템이 부족합니다! (필요: ${cost.displayName} ${cost.amount}개)"))
                        return true
                    }
                    sender.inventory.removeItem(ItemStack(cost.material, cost.amount))
                    sender.sendMessage(Component.text("§e[Lucky Box] ${cost.displayName} ${cost.amount}개를 사용하여 상자를 엽니다!"))
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

                if (args.size < 4) {
                    sender.sendMessage(Component.text("§c사용법: /lucky_box setcost <아이템코드> <개수> <표시이름>"))
                    sender.sendMessage(Component.text("§7예시: /lucky_box setcost DIAMOND 1 &6&l다이아몬드"))
                    return true
                }

                val materialName = args[1].uppercase()

                // 1. 양의 정수 체크
                val amount = args[2].toIntOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage(Component.text("§c개수는 1 이상의 숫자여야 합니다."))
                    return true
                }

                // 2. 색깔 코드 (& -> §) 변환 로직 추가
                val rawDisplayName = args.slice(3 until args.size).joinToString(" ")
                val displayName = rawDisplayName.replace("&", "§")

                val material = Material.matchMaterial(materialName)
                if (material == null) {
                    sender.sendMessage(Component.text("§c존재하지 않는 아이템 코드입니다: $materialName"))
                    return true
                }

                config.set("lucky_box.cost.item", material.name)
                config.set("lucky_box.cost.amount", amount)
                config.set("lucky_box.cost.display_name", displayName)
                saveConfig()

                sender.sendMessage(Component.text("§a[Lucky Box] 비용 수정 완료! (이름: $displayName§a)"))
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
                sender.sendMessage(Component.text("§f- 필요 아이템: §e${cost.displayName} §8(${cost.material})"))
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

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val view = event.view
        val title = view.title()

        // 1. 뽑기 창 (Lucky Box) - 아이템 클릭 방지
        if (title == Component.text("Lucky Box")) {
            event.isCancelled = true
            return
        }

        // 2. 설정 창 (Lucky Box Config) - 클릭할 때마다 실시간 저장
        if (title == Component.text("Lucky Box Config")) {
            // 스케줄러를 사용하는 이유: 클릭 직후에는 인벤토리 상태가 아직 변하기 전이라,
            // 1틱 뒤(아이템이 옮겨진 후)에 저장해야 정확합니다.
            object : BukkitRunnable() {
                override fun run() {
                    saveConfigFromInventory(event.inventory)
                }
            }.runTaskLater(this, 1L)
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val view = event.view
        val title = view.title()

        // 1. 뽑기 창 (Lucky Box) - 드래그 방지
        if (title == Component.text("Lucky Box")) {
            event.isCancelled = true
            return
        }

        // 2. 설정 창 (Lucky Box Config) - 드래그 직후 실시간 저장
        if (title == Component.text("Lucky Box Config")) {
            object : BukkitRunnable() {
                override fun run() {
                    saveConfigFromInventory(event.inventory)
                }
            }.runTaskLater(this, 1L) // 드래그가 완전히 끝난 1틱 뒤에 저장
        }
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

class LuckyBoxInventory(private val plugin: Main, private val inventory: Inventory) : InventoryHolder {
    override fun getInventory(): Inventory = inventory

    private var pattern = 0
    private var count = 0
    private val totalFlips = 30 // 총 애니메이션 횟수

    fun startAnimation(inventory: Inventory, player: Player) {
        // 1. Config에서 실제 아이템만 필터링 (AIR 무시 로직 유지)
        val configItems = mutableListOf<ItemStack>()
        for (i in 0..53) {
            val item = plugin.config.getItemStack("lucky_box.items.$i")
            if (item != null && item.type != org.bukkit.Material.AIR && item.amount > 0) {
                configItems.add(item)
            }
        }

        // 아이템이 없으면 종료
        if (configItems.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.Component.text("§c[Lucky Box] 설정된 아이템이 없습니다! /lb config를 확인해주세요."))
            player.closeInventory()
            return
        }

        // 2. 점점 느려지는 루프 시작 (Ease-out 로직 유지)
        runVariableTickLoop(inventory, player, 2L, configItems)
    }

    private fun runVariableTickLoop(inventory: Inventory, player: Player, currentDelay: Long, configItems: List<ItemStack>) {
        object : org.bukkit.scheduler.BukkitRunnable() {
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
        val displayName = net.kyori.adventure.text.Component.text("§8LuckyBox")

        val lightBluePane = ItemStack(org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { displayName(displayName) }
        }
        val yellowPane = ItemStack(org.bukkit.Material.YELLOW_STAINED_GLASS_PANE).apply {
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
        player.sendMessage(net.kyori.adventure.text.Component.text("§6[Lucky Box] §f당첨! §b[${resultItem.type.name}]§f 아이템이 지급되었습니다."))
    }
}