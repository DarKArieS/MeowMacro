將 main activity 改造為可懸浮在其他 app 上的形式，可允許拖動

FloatingWindow close 改為右上角 x ，新增 preview

將 FloatingWindow 寬度改為符合內容大小

FloatingWindow 新增拖動慣性，並且允許一半在畫面外

慣性滑動太長，縮短一點，並將這個設定參數化

巨集錄製&播放
MainWindow 新增錄製/播放 Icon
點擊錄製時，會將開始記錄使用者的操作事件 (事件允許超出懸浮視窗)，以及每個事件的間隔。
紀錄點擊以及滑動

點擊播放時，重播已錄製內容

紀錄時，忽略第一個事件出現前的等待時間

讓 FloatingWindow 僅為容器:
```
fun FloatingWindow(
    onDrag: (dx: Int, dy: Int) -> Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
```
巨集所需元件 寫在 MainWindow 的 signature 上

錄製時允許跟其他 APP 互動

將 MacroGesture 跟 MacroEvent 合成: 
```kotlin
sealed interface MacroEvent {

	data class Wait(val durationMillis: Long): MacroEvent

    data class Tap(val position: Offset, val durationMillis: Long) : MacroEvent

    data class Swipe(val points: List<Offset>, val durationMillis: Long) : MacroEvent

}
```

新增 data class:
```
data class Macro(
	val name: String,
	val macro: List<MacroEvent>,
)
```
新增 MacroRepo ，負責處理 將 List<Macro> 存到硬碟以及讀取，可存多條巨集
MainWindow 改為 Column ，由上到下為:
一個錄製按鈕 + 目前選擇即將錄製的的巨集名稱 + 加號按鈕(新增巨集)，點擊 「目前選擇即將錄製的的巨集名稱」 時可以將這欄收起來
已經新增好的巨集列表 Column: 播放按鈕 + 巨集名稱 + (點擊整個欄位可選擇，改變選定中的背景顏色) ，可上下捲動

點擊 「目前選擇即將錄製的的巨集名稱」 時可以將錄製欄收起來，而不是已錄製列表
已錄製列表最上方新增已錄製列表收起來的區域

將錄製欄收起來的形式同已錄製列表

已錄製列表新增 icon 刪除按鈕

已錄製列表長按可重新命名

已錄製列表新增調整順序的滑塊

FloatingWindow 關閉按鈕旁邊新增 減號 ICON ，點擊後可將視窗縮小成一塊純色可拖動色塊，點擊色塊重新展開