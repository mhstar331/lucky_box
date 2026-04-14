# lucky_box
대충 만든 마크 럭키박스 플러그인(미완)                  
### 권한
[LuckPerms 플러그인](https://modrinth.com/plugin/luckperms/versions?g=1.21.11&l=paper)과 연동하여 권한이 있는 플레이어만 럭키박스를 사용할 수 있도록 설정할 수 있습니다.         
- luckybox.use : 럭키박스를 사용할 수 있는 권한입니다. 이 권한이 없는 플레이어는 럭키박스를 사용할 수 없습니다.
- luckybox.admin : 럭키박스의 관리자 권한입니다. 이 권한이 있는 플레이어는 럭키박스의 설정을 변경할 수 있습니다.
#### LuckpPrms 플러그인 명령어 간단 정리
- /lp creategroup [그룹 이름] : 그룹 생성
- /lp deletegroup [그룹 이름] : 그룹 제거
- /lp listgroups : 그룹 리스트
- /lp user [이름] parent set [그룹 이름] : 유저를 그룹에 추가
- /lp user [이름] parent remove [그룹 이름] : 유저를 그룹에서 제거
- /lp group [그룹 이름] permission set [권한] : 그룹에 권한 부여
- /lp group [그룹 이름] permission unset [권한] : 그룹에 권한 제거
- /lp user [이름] permission set [권한] : 유처에게 권한 부여
- /lp user [이름] permission unset [권한] : 유처에게 권한 제거
