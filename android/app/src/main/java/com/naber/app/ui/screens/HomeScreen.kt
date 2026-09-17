package com.naber.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naber.app.Naber
import com.naber.app.ui.UnreadBadge
import com.naber.app.ui.theme.NaberColors

/** Alt sekmeli ana ekran. */
@Composable
fun HomeScreen(
    startConversationId: Int,
    onOpenChat: (Int) -> Unit,
    onNewGroup: () -> Unit,
    onAdmin: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onLoggedOut: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val unread by Naber.events.unreadTotal.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaberColors.Background)
            .statusBarsPadding()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> ChatsTab(
                    startConversationId = startConversationId,
                    onOpenChat = onOpenChat,
                    onNewGroup = onNewGroup,
                    onAdmin = onAdmin,
                    onContacts = { tab = 1 }
                )

                1 -> ContactsTab(onOpenChat = onOpenChat, onNewGroup = onNewGroup, onOpenProfile = onOpenProfile)
                2 -> CallsTab()
                else -> ProfileTab(onAdmin = onAdmin, onLoggedOut = onLoggedOut)
            }
        }

        BottomBar(
            selected = tab,
            unread = unread,
            onSelect = { tab = it }
        )
    }
}

@Composable
private fun BottomBar(selected: Int, unread: Int, onSelect: (Int) -> Unit) {
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NaberColors.Divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NaberColors.TopBar)
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomItem(Icons.Filled.Chat, "Sohbet", selected == 0, unread) { onSelect(0) }
            BottomItem(Icons.Filled.People, "Kisiler", selected == 1, 0) { onSelect(1) }
            BottomItem(Icons.Filled.Call, "Aramalar", selected == 2, 0) { onSelect(2) }
            BottomItem(Icons.Filled.Person, "Profil", selected == 3, 0) { onSelect(3) }
        }
    }
}

@Composable
private fun BottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit
) {
    val color = if (selected) NaberColors.Accent else NaberColors.TextSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp)
    ) {
        Box {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(23.dp))
            if (badge > 0) {
                UnreadBadge(
                    count = badge,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp)
                )
            }
        }
        Text(
            label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
