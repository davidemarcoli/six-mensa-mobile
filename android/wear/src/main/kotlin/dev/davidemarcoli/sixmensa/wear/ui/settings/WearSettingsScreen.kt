package dev.davidemarcoli.sixmensa.wear.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.davidemarcoli.sixmensa.core.ContentLanguage
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.wear.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun WearSettingsScreen() {
    val viewModel: WearSettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text(stringResource(R.string.restaurant)) } }
            items(Restaurant.entries) { restaurant ->
                RadioButton(
                    selected = state.restaurant == restaurant,
                    onSelect = { viewModel.setRestaurant(restaurant) },
                    label = { Text(restaurant.displayName) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { ListHeader { Text(stringResource(R.string.language)) } }
            items(ContentLanguage.entries) { language ->
                RadioButton(
                    selected = state.contentLanguage == language,
                    onSelect = { viewModel.setContentLanguage(language) },
                    label = { Text(stringResource(language.labelRes())) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun ContentLanguage.labelRes(): Int = when (this) {
    ContentLanguage.DE -> R.string.language_de
    ContentLanguage.EN -> R.string.language_en
}
