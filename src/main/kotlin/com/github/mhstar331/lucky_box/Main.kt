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
            sender.sendMessage("Only players can use this command.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("Usage: /lucky_box <config | item>")
            return true
        }

        when (args[0].lowercase()) {
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
            "item" -> {
                if (!sender.hasPermission("luckybox.admin")) {
                    sender.sendMessage(Component.text("§c이 명령어를 사용할 권한이 없습니다."))
                    return true
                }
                val item = ItemStack(Material.PAPER).apply {
                    itemMeta = itemMeta?.apply {
                        setDisplayName("§6§l럭키박스")
                        setCustomModelData(1)
                    }
                }
                sender.inventory.addItem(item)
                sender.sendMessage(Component.text("§a럭키박스 아이템을 지급받았습니다!"))
                return true
            }
            else -> sender.sendMessage("Usage: /lucky_box <config | item>")
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
        // 1. 아이템 이름 성분(Component) 생성
        val itemNameComponent = if (resultItem.itemMeta?.hasDisplayName() == true) {
            // 커스텀 이름이 있으면 그 이름을 그대로 사용
            resultItem.itemMeta.displayName()!!
        } else {
            // 커스텀 이름이 없으면 마크 기본 번역 키 사용 (유저 언어에 맞춰짐)
            Component.translatable(resultItem.translationKey())
        }

        player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f)
        val msg = Component.text("§6[Lucky Box] §f당첨! §b[")
            .append(itemNameComponent)
            .append(Component.text("§b]§f 아이템이 지급되었습니다."))
        player.sendMessage(msg)
    }
}
class LuckyBoxItem(private val plugin: LuckyBox) : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return // 손에 든 아이템이 없으면 무시

        // 1. 우클릭 동작인지 확인 (공기 클릭 또는 블록 클릭)
        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {

            // 2. 아이템 판별 (종이이면서 CustomModelData가 1인지 확인)
            if (item.type == Material.PAPER && item.itemMeta?.hasCustomModelData() == true && item.itemMeta?.customModelData == 1) {

                // 3. 권한 확인
                if (!player.hasPermission("luckybox.use")) {
                    player.sendMessage(Component.text("§c이 아이템을 사용할 권한이 없습니다."))
                    return
                }

                // 4. 아이템 1개 소모
                item.amount -= 1

                // 5. 럭키박스 오픈 로직 실행
                openLuckyBox(player)

                // 이벤트 취소 (종이가 블록에 써지는 등의 기본 동작 방지)
                event.isCancelled = true
            }
        }
    }

    private fun openLuckyBox(player: Player) {
        val inventory = Bukkit.createInventory(null, 9, Component.text("Lucky Box"))
        // 기존에 작성하신 LuckyBoxInventory 클래스 연결
        val luckyBox = LuckyBoxInventory(plugin, inventory)
        plugin.luckyBoxInventories[player.uniqueId] = luckyBox

        luckyBox.startAnimation(inventory, player)
        player.openInventory(inventory)

        player.sendMessage(Component.text("§e[Lucky Box] 럭키박스를 열었습니다!"))
    }
}