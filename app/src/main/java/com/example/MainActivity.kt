package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MonitorViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: MonitorViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if necessary
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MonitorScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MonitorScreen(viewModel: MonitorViewModel, modifier: Modifier = Modifier) {
    var urlInput by remember { mutableStateOf("https://example.com") }
    var instanceCountInput by remember { mutableStateOf("1") }

    val isRunning by viewModel.isRunning.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val screenshotsMap by viewModel.screenshots.collectAsState()

    val screenshotsList = screenshotsMap.values.toList().sortedBy { it.instanceId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "أداة المراقبة واختبار التحميل",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "ملاحظة للأمان: تم تحديد أقصى عدد لـ N بـ 5 وتطبيق تأخير بين الطلبات لحماية موارد الجهاز والخوادم.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("رابط الموقع (URL)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = instanceCountInput,
            onValueChange = { instanceCountInput = it.filter { char -> char.isDigit() } },
            label = { Text("عدد نوافذ المراقبة المتزامنة (N)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (!isRunning) {
                Button(onClick = {
                    // Maximum cap at 5 to avoid out-of-memory and adhere to safety policy 
                    val count = instanceCountInput.toIntOrNull()?.coerceIn(1, 5) ?: 1
                    instanceCountInput = count.toString()
                    viewModel.startMonitoring(urlInput, count)
                }) {
                    Text("بدء المعالجة")
                }
            } else {
                Button(
                    onClick = { viewModel.pauseMonitoring() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(if (isPaused) "استئناف" else "إيقاف مؤقت")
                }

                Button(
                    onClick = { viewModel.stopMonitoring() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("إلغاء / إيقاف")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isRunning) {
            Text(
                text = "الخدمة تعمل في الخلفية...",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(screenshotsList) { data ->
                ScreenshotCard(data = data)
            }
        }
    }
}

@Composable
fun ScreenshotCard(data: com.example.service.ScreenshotData) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val timeString = formatter.format(Date(data.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                Text(
                    text = "اكتمل التحميل – نافذة ${data.instanceId + 1} | الوقت: $timeString",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Image(
                bitmap = data.bitmap.asImageBitmap(),
                contentDescription = "Website Screenshot",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            )
        }
    }
}
