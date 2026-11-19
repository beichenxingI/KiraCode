package com.kirakira.kiracode.ui.splash

import com.kirakira.kiracode.domain.repository.PreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    @Test
    fun `unaccepted disclaimer shows merged onboarding even after old onboarding completed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = repository(isFirstLaunch = false, disclaimerAccepted = false)
            val viewModel = SplashViewModel(repository)

            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SplashUiState.Onboarding)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `accepted return user skips onboarding`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = repository(isFirstLaunch = false, disclaimerAccepted = true)
            val viewModel = SplashViewModel(repository)

            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SplashUiState.Branding)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `accepting onboarding writes both flags atomically and enters app`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = repository(isFirstLaunch = true, disclaimerAccepted = false)
            coEvery { repository.acceptFirstRunTerms() } returns Unit
            val viewModel = SplashViewModel(repository)
            advanceUntilIdle()

            viewModel.acceptFirstRunTerms()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SplashUiState.Ready)
            coVerify(exactly = 1) { repository.acceptFirstRunTerms() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun repository(
        isFirstLaunch: Boolean,
        disclaimerAccepted: Boolean
    ): PreferencesRepository {
        val repository = mockk<PreferencesRepository>(relaxed = true)
        every { repository.isFirstLaunch } returns flowOf(isFirstLaunch)
        every { repository.disclaimerAccepted } returns flowOf(disclaimerAccepted)
        return repository
    }
}
