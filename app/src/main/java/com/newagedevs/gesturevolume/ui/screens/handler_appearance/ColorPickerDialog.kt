package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxkeppeker.sheets.core.models.base.Header.Custom
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.color.ColorDialog
import com.maxkeppeler.sheets.color.models.ColorConfig
import com.maxkeppeler.sheets.color.models.ColorSelection
import com.maxkeppeler.sheets.color.models.MultipleColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    currentColor: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val templateColors = MultipleColors.ColorsInt(
        Color.Red.copy(alpha = 0.1f).toArgb(),
        Color.Red.copy(alpha = 0.3f).toArgb(),
        Color.Red.copy(alpha = 0.5f).toArgb(),
        Color.Red.toArgb(),
        Color.Blue.copy(alpha = 0.3f).toArgb(),
        Color.Blue.copy(alpha = 0.5f).toArgb(),
        Color.Blue.toArgb(),
        Color.Green.copy(alpha = 0.3f).toArgb(),
        Color.Green.copy(alpha = 0.5f).toArgb(),
        Color.Green.toArgb(),
        Color.Yellow.toArgb(),
        Color.Cyan.toArgb(),
        Color.Magenta.toArgb(),
        Color.White.copy(alpha = 0.3f).toArgb(),
        Color.White.copy(alpha = 0.5f).toArgb(),
        Color.White.toArgb(),
        Color.Black.copy(alpha = 0.3f).toArgb(),
        Color.Black.copy(alpha = 0.5f).toArgb(),
        Color.Black.toArgb()
    )

    ColorDialog(
        state = rememberUseCaseState(visible = true, onCloseRequest = { onDismiss() }),
        selection = ColorSelection(
            onSelectColor = { onSelect(it) }
        ),
        config = ColorConfig(
            templateColors = templateColors,
//            defaultColor = currentColor
        ),
        header = Custom(
            header = @Composable() {
                Text(
                    text = "Select the color and transparency of the handler",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)
                )
            }
        )
    )
}