package com.ipget.aloxing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipget.aloxing.ui.theme.IpGetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

// --- 数据模型 ---
enum class NetworkType { WIFI, MOBILE, VPN, OTHERS }

data class NetworkInfo(
    val interfaceName: String,
    val displayName: String,
    val ipAddress: String,
    val ipV6Address: String?,
    val type: NetworkType,
    val hasIpv4: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 禁用动态取色以确保翡翠绿生效
            IpGetTheme(dynamicColor = false) {
                IpListScreen()
            }
        }
    }
}

// --- 逻辑层：获取 IP 列表 ---
object NetworkUtils {
    fun getAllNetworkInfos(): List<NetworkInfo> {
        val list = mutableListOf<NetworkInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in Collections.list(interfaces)) {
                if (!intf.isUp || intf.isLoopback) continue
                val addresses = Collections.list(intf.inetAddresses)
                val ipv4 = addresses.find { it is Inet4Address }?.hostAddress
                val ipv6 = addresses.find { it is java.net.Inet6Address }?.hostAddress?.split("%")?.get(0)

                if (ipv4 != null || ipv6 != null) {
                    val name = intf.name.lowercase()
                    val type = when {
                        name.contains("wlan") -> NetworkType.WIFI
                        name.contains("rmnet") || name.contains("ccmni") -> NetworkType.MOBILE
                        name.contains("tun") || name.contains("ppp") -> NetworkType.VPN
                        else -> NetworkType.OTHERS
                    }
                    list.add(NetworkInfo(
                        intf.name,
                        intf.displayName,
                        ipv4 ?: "无 IPv4",
                        ipv6,
                        type,
                        ipv4 != null
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 排序：WiFi > 移动网络 > VPN > 其他
        return list.sortedWith(compareBy<NetworkInfo> { it.type }.thenByDescending { it.hasIpv4 })
    }
}

// --- UI 层 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpListScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var networkList by remember { mutableStateOf(NetworkUtils.getAllNetworkInfos()) }
    var isRefreshing by remember { mutableStateOf(false) }

    // 1. 静默自动刷新：每3秒更新一次数据，不干扰刷新图标
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3000)
            val updated = withContext(Dispatchers.IO) { NetworkUtils.getAllNetworkInfos() }
            networkList = updated
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("网络 IP 详情", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        // 2. 下拉刷新逻辑修复
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true // 手动开启图标
                    try {
                        val result = withContext(Dispatchers.IO) {
                            delay(500) // 视觉缓冲，防止闪现
                            NetworkUtils.getAllNetworkInfos()
                        }
                        networkList = result
                    } finally {
                        isRefreshing = false // 无论成功失败，必须关闭图标
                        Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        ) {
            if (networkList.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = "已开启 3s 自动刷新",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(networkList, key = { it.interfaceName }) { info ->
                        NetworkCard(info) {
                            // 3. 复制逻辑：纯地址拼接，无标题
                            val combined = if (!info.ipV6Address.isNullOrBlank()) {
                                "${info.ipAddress} ${info.ipV6Address}"
                            } else {
                                info.ipAddress
                            }
                            copyToClipboard(context, combined)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkCard(info: NetworkInfo, onCombinedCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getIconForType(info.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = info.ipAddress,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                if (!info.ipV6Address.isNullOrBlank()) {
                    Text(
                        text = "IPv6: ${info.ipV6Address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 右侧复制按钮
            IconButton(onClick = onCombinedCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "复制地址",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("未发现活动网络接口", color = MaterialTheme.colorScheme.outline)
    }
}

private fun getIconForType(type: NetworkType): ImageVector {
    return when (type) {
        NetworkType.WIFI -> Icons.Default.Wifi
        NetworkType.MOBILE -> Icons.Default.SignalCellularAlt
        NetworkType.VPN -> Icons.Default.VpnLock
        NetworkType.OTHERS -> Icons.Default.Dns
    }
}

private fun copyToClipboard(context: Context, text: String) {
    // 1. 排除无效点击
    if (text.isBlank() || text.contains("无 IPv4") && !text.contains(":")) {
        Toast.makeText(context, "无有效地址可复制", Toast.LENGTH_SHORT).show()
        return
    }

    // 2. 执行复制操作
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("IP info", text)
    clipboard.setPrimaryClip(clip)

    // 3. 视觉反馈优化
    // 强制显示 Toast（即使在 Android 13+ 也会显示，虽然系统也会弹窗）
    // 这样能确保你明确知道点击生效了
    Toast.makeText(context, "已复制: $text", Toast.LENGTH_SHORT).show()
}