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

