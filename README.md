# lucky_box
대충 만든 마크 럭키박스 플러그인

인게임 인벤토리를 통해 당첨 아이템을 설정하고, 특정 아이템을 비용으로 소모하여 뽑기를 진행하는 플러그인입니다.

### 🛠️ 지원 버전
- **Minecraft Version:** `1.21.11` (Paper 기반)

### 🎮 주요 명령어
모든 명령어는 `/lucky_box`,`/luckybox`,`/lb`,`/럭키박스`로 사용 가능합니다.

| 명령어                          | 설명                                | 권한               |
|:-----------------------------|:----------------------------------|:-----------------|
| `/lb open`                   | 럭키박스 뽑기 창을 엽니다. (설정된 비용 소모)       | `luckybox.use`   |
| `/lb config`                 | 당첨 아이템을 설정하는 54칸 인벤토리를 엽니다.       | `luckybox.admin` |
| `/lb info`                   | 현재 설정된 비용 아이템, 개수, 활성화 여부를 확인합니다. | `luckybox.admin` |
| `/lb cost <on/off>`          | 비용 시스템 사용 여부를 설정합니다.              | `luckybox.admin` |
| `/lb setcost <코드> <개수> [이름]` | 비용 아이템 설정 (이름 생략 시 기본 이름 사용)      | `luckybox.admin` |

> **setcost 예시1:** `/lb setcost GOLD_INGOT 1 &6황금 코인`  
> (색깔 코드는 `&`를 사용하여 입힐 수 있습니다.)

> **setcost 예시2:** `/lb setcost GOLD_INGOT 1`   
> (이름 생략 시 게임 내 기본 이름인 **"금 주괴"**(한글)로 자동 설정됩니다.)

### 🔑 권한 (Permissions)
[LuckPerms 플러그인](https://modrinth.com/plugin/luckperms/versions?g=1.21.11&l=paper) 등을 통해 권한을 관리할 수 있습니다.

- **luckybox.use**: 럭키박스를 사용할 수 있는 기본 권한 (일반 유저)
- **luckybox.admin**: 비용 설정 및 아이템 수정을 할 수 있는 관리자 권한 (OP 전용)