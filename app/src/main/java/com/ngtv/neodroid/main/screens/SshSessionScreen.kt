package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.nvim.NvimSession
import com.ngtv.neodroid.sharedComponents.PasswordInput
import com.ngtv.neodroid.sshUtils.NvimTool
import com.ngtv.neodroid.sshUtils.SshSessionManager
import com.ngtv.neodroid.sshUtils.WorkspaceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException

@Composable
fun SshSessionScreen(session: WorkspaceSession) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState { session.tools.size }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.statusBarsPadding()
    ) {
        PrimaryScrollableTabRow(
            modifier = Modifier.height(40.dp),
            selectedTabIndex = selectedTab
        ) {
            session.tools.forEachIndexed { index, tool ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tool.title)
                            IconButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        session.closeTool(tool.id)
                                    }
                                }
                            }) {
                                Text("×")
                            }
                        }
                    }
                )
            }
            IconButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        session.startNvim()
                    }
                }
            }) {
                Text("+")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Text(session.tools[page].id)
        }

    }
}
