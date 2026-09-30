package ir.example.slmchat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/*
 * فونت فارسی (مثلاً Vazirmatn):
 *  1) فایل‌های Vazirmatn-Regular.ttf و Vazirmatn-Bold.ttf را در app/src/main/res/font/ بگذارید
 *     (نام فایل‌ها باید حروف کوچک و بدون خط تیره باشد: vazirmatn_regular.ttf, vazirmatn_bold.ttf)
 *  2) این بلوک را فعال کنید و typography را به MaterialTheme بدهید:
 *
 *     private val Vazir = FontFamily(
 *         Font(R.font.vazirmatn_regular, FontWeight.Normal),
 *         Font(R.font.vazirmatn_bold, FontWeight.Bold),
 *     )
 *     private val base = Typography()
 *     private val AppTypography = Typography(
 *         bodyLarge = base.bodyLarge.copy(fontFamily = Vazir),
 *         bodyMedium = base.bodyMedium.copy(fontFamily = Vazir),
 *         bodySmall = base.bodySmall.copy(fontFamily = Vazir),
 *         titleLarge = base.titleLarge.copy(fontFamily = Vazir),
 *         titleMedium = base.titleMedium.copy(fontFamily = Vazir),
 *         titleSmall = base.titleSmall.copy(fontFamily = Vazir),
 *         labelMedium = base.labelMedium.copy(fontFamily = Vazir),
 *         labelSmall = base.labelSmall.copy(fontFamily = Vazir),
 *     )
 *     ... MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
 */

@Composable
fun SlmChatTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        // Material You (رنگ‌های پویا از والپیپر) در اندروید ۱۲ به بالا
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
