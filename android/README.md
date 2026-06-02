# rabbit-android

## 说明

- 原生 Android（Java + XML）
- 网络：OkHttp + Gson
- 目前已实现：注册/登录、加载我的兔舍（用于后续联调打底）
- 已补齐：选择当前兔舍（保存 houseId）、查看笼位/兔子/提醒列表、创建兔舍
- 已补齐：录入兔子、批次全流程操作（催情/配种/摸胎/分娩/断奶/出售）、投喂/异常、留种转后备兔
- 已补齐：设置入口、繁殖性能列表、兔状态历史查询

## 后端地址

默认配置在：
- [Config.java](file:///d:/rabbit%20app/android/app/src/main/java/com/rabbit/app/Config.java)

模拟器访问本机后端用 `http://10.0.2.2:8080`。
真机需要改成你电脑的局域网 IP（例如 `http://192.168.1.10:8080`），并确保同一网络可访问。
