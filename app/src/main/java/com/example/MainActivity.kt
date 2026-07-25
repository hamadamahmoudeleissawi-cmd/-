package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.screens.ExtractorScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.theme.AudioExtractorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AudioExtractorTheme {
                // Force Right-To-Left layout for native Arabic UI
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var selectedTab by remember { mutableIntStateOf(0) }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "فصل وتعديل الصوت",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                },
                                navigationIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .size(28.dp)
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.testTag("main_navigation_bar")
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    label = { Text("فصل وتعديل الصوت", fontWeight = FontWeight.SemiBold) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 0) Icons.Default.MovieFilter else Icons.Outlined.MovieFilter,
                                            contentDescription = "فصل الصوت"
                                        )
                                    },
                                    modifier = Modifier.testTag("tab_extractor")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    label = { Text("المكتبة والتسجيلات", fontWeight = FontWeight.SemiBold) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 1) Icons.Default.LibraryMusic else Icons.Outlined.LibraryMusic,
                                            contentDescription = "المكتبة"
                                        )
                                    },
                                    modifier = Modifier.testTag("tab_library")
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (selectedTab) {
                                0 -> ExtractorScreen(viewModel = viewModel)
                                1 -> LibraryScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
