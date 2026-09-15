package com.naber.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import com.naber.app.data.CallInfo
import com.naber.app.ui.Avatar
import com.naber.app.ui.EmptyState
import com.naber.app.ui.formatChatTime
import com.naber.app.ui.formatClock
import com.naber.app.ui.formatDuration
import com.naber.app.ui.theme.NaberColors

@Composable
fun CallsTab() {
    var calls by remember { mutableStateOf<List<CallInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val myId = Naber.session.user?.id ?: 0

    LaunchedEffect(Unit) {
        runCatching { calls = Naber.api.callHistory() }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(NaberColors.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NaberColors.TopBar)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text("Aramalar", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = NaberColors.TextPrimary)
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NaberColors.Accent)
            }

            calls.isEmpty() -> EmptyState("Arama gecmisi bos", "Bir sohbeti acip ustteki telefon simgesiyle arama baslatabilirsiniz.")

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(calls, key = { it.id }) { call ->
                    CallRow(call, myId)
                }
            }
        }
    }
}

@Composable
private fun CallRow(call: CallInfo, myId: Int) {
    val outgoing = call.callerId == myId
    val missed = call.status == "missed" || call.status == "rejected"
    val other = if (outgoing) call.callee else call.caller

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (call.isGroup) {
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = NaberColors.Accent, modifier = Modifier.size(30.dp))
            }
        } else {
            Avatar(other, size = 46.dp)
        }

        Spacer(Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (call.isGroup) call.groupTitle.ifBlank { "Grup aramasi" } else (other?.displayName ?: "Bilinmeyen"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (missed) NaberColors.Danger else NaberColors.TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(
                    when {
                        missed -> Icons.Filled.CallMissed
                        outgoing -> Icons.Filled.CallMade
                        else -> Icons.Filled.CallReceived
                    },
                    contentDescription = null,
                    tint = if (missed) NaberColors.Danger else NaberColors.Accent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    buildString {
                        append(formatChatTime(call.createdAt))
                        append(" ")
                        append(formatClock(call.createdAt))
                        if (call.duration > 0) append(" - ${formatDuration(call.duration)}")
                    },
                    fontSize = 12.5.sp,
                    color = NaberColors.TextSecondary
                )
            }
        }
    }
}
