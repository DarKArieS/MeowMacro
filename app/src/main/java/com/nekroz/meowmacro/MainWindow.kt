package com.nekroz.meowmacro

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nekroz.meowmacro.ui.theme.MeowMacroTheme

@Composable
fun MainWindow(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun MainWindowPreview() {
    MeowMacroTheme {
        MainWindow("Android")
    }
}