package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.model.FeedFilter
import com.example.ui.theme.AmbientIndigo
import com.example.ui.theme.FrostedViolet
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Slate950

@Composable
fun EmptyFeedView(
    filter: FeedFilter,
    onResetFilter: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onClearSearch: () -> Unit = {}
) {
    val isFavorites = filter is FeedFilter.Favorites
    val isSearching = searchQuery.isNotBlank()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Slate950, AmbientIndigo.copy(alpha = 0.4f), Slate950)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x331E293B))
                    .border(1.dp, GlassBorder, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_empty_videos),
                    contentDescription = "Empty Gallery",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = when {
                    isSearching -> "No results for \u201C${searchQuery.trim()}\u201D"
                    isFavorites -> "No Favorites Yet"
                    else -> "No Videos in ${filter.displayName}"
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSearching) {
                    "Nothing in ${filter.displayName} matches that search. Try fewer words, or clear the search."
                } else if (isFavorites) {
                    "Tap the heart icon on any video in your feed to save it here for quick access."
                } else {
                    "No local videos found for this folder or filter. Record or download some videos to your device gallery!"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xCCFFFFFF),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isSearching) {
                Button(
                    onClick = onClearSearch,
                    colors = ButtonDefaults.buttonColors(containerColor = FrostedViolet),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(14.dp))
                        .testTag("empty_view_clear_search_button")
                ) {
                    Text("Clear Search", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else if (filter !is FeedFilter.All) {
                Button(
                    onClick = onResetFilter,
                    colors = ButtonDefaults.buttonColors(containerColor = FrostedViolet),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(14.dp))
                        .testTag("empty_view_reset_filter_button")
                ) {
                    Text("View All Videos", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = FrostedViolet),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(14.dp))
                        .testTag("empty_view_refresh_button")
                ) {
                    Text("Refresh Storage Scan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

