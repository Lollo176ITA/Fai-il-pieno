package it.faiilpieno.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.faiilpieno.R
import it.faiilpieno.data.location.LocationProvider
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.ui.car.CarForm
import it.faiilpieno.ui.car.CarViewModel
import it.faiilpieno.ui.settings.PreferencesViewModel
import it.faiilpieno.ui.settings.RoutePreferencesCard
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    fun hasLocationPermission() = locationProvider.hasPermission()

    fun finish() = viewModelScope.launch { prefs.setOnboardingDone() }
}

private const val PAGES = 3

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel(), preferencesViewModel: PreferencesViewModel = hiltViewModel(), carViewModel: CarViewModel = hiltViewModel()) {
    val preferences by preferencesViewModel.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val form by carViewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(carViewModel) {
        carViewModel.saved.collect { if (pager.currentPage == 0) pager.animateScrollToPage(1) }
    }
    var locationGranted by rememberSaveable { mutableStateOf(viewModel.hasLocationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = viewModel.hasLocationPermission()
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { viewModel.finish() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
        HorizontalPager(state = pager, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> Page(R.drawable.ic_car, R.string.car_title, R.string.onboarding_2_text) {
                    if (form.loaded) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CarForm(form, carViewModel, showSave = false)
                    }
                }
                1 -> Page(R.drawable.ic_route, R.string.route_preferences, R.string.onboarding_route_text) {
                    RoutePreferencesCard(preferences.route, preferencesViewModel::setAvoidance,
                        showTitle = false, enabled = preferences.loaded, buffer = preferences.buffer, onBufferChange = preferencesViewModel::setBuffer)
                }
                else -> Page(R.drawable.ic_my_location, R.string.onboarding_3_title, R.string.onboarding_3_text) {
                    if (locationGranted) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.onboarding_location_granted), style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                                )
                            },
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) { Text(stringResource(R.string.action_allow_location)) }
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (pager.currentPage > 0) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                Text(stringResource(R.string.action_back))
            } else PageIndicator(pager.currentPage)
            Spacer(Modifier.weight(1f))
            val last = pager.currentPage == PAGES - 1
            Button(
                onClick = {
                    when {
                        last -> viewModel.finish()
                        pager.currentPage == 0 -> carViewModel.save()
                        else -> scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
                enabled = form.loaded && preferences.loaded,
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text(stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next))
            }
        }
    }
}

@Composable
private fun Page(@DrawableRes icon: Int, title: Int, text: Int, extra: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        extra?.invoke()
    }
}

@Composable
private fun PageIndicator(current: Int) {
    val description = stringResource(R.string.onboarding_page, current + 1, PAGES)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(PAGES) { index ->
            Box(
                Modifier
                    .size(if (index == current) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}
