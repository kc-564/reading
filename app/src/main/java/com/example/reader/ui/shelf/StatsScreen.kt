package com.example.reader.ui.shelf

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.size
import com.example.reader.data.StatsRepository
import com.example.reader.db.AppDatabase
import com.example.reader.db.DayStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Reading statistics screen (F07). Shows cumulative reading time, today's time, the last 7 days,
 * and a simple bar chart of daily durations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onNavigateBack: () -> Unit = {}) {
    val viewModel: StatsViewModel = viewModel()
    val totalSeconds by viewModel.totalSeconds.collectAsStateWithLifecycle(0)
    val todaySeconds by viewModel.todaySeconds.collectAsStateWithLifecycle(0)
    val weekSeconds by viewModel.weekSeconds.collectAsStateWithLifecycle(0)
    val daily by viewModel.daily.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读统计") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("累计", formatDuration(totalSeconds), Modifier.weight(1f))
                StatCard("今日", formatDuration(todaySeconds), Modifier.weight(1f))
                StatCard("近七日", formatDuration(weekSeconds), Modifier.weight(1f))
            }
            Text("近七日时长", style = MaterialTheme.typography.titleMedium)
            DailyBarChart(daily = daily)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DailyBarChart(daily: List<DayStat>) {
    if (daily.isEmpty()) {
        Text("暂无阅读记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = daily.maxOfOrNull { it.totalSec }?.coerceAtLeast(1) ?: 1
    val last7 = daily.takeLast(7)
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        last7.forEach { d ->
            val minutes = (d.totalSec / 60f)
            val frac = d.totalSec.toFloat() / max
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("%.0f".format(minutes), style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((frac * 80).dp.coerceAtLeast(2.dp))
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(d.dateKey.takeLast(2), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remMin = minutes % 60
    return if (hours > 0) "${hours}时${remMin}分" else "${minutes}分"
}

/**
 * ViewModel backing [StatsScreen]. Aggregates the whole-library reading sessions.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = StatsRepository(AppDatabase.getInstance(application))

    val totalSeconds: StateFlow<Int> = repo.getTotalDurationAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todaySeconds: StateFlow<Int> = repo.getDailyFlow(todayKey(), todayKey())
        .map { it.sumOf { d -> d.totalSec } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val weekSeconds: StateFlow<Int> = repo.getDailyFlow(dayKeyBefore(6), todayKey())
        .map { it.sumOf { d -> d.totalSec } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val daily: StateFlow<List<DayStat>> = repo.getDailyFlow(dayKeyBefore(6), todayKey())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private fun dayKeyBefore(days: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -days)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
