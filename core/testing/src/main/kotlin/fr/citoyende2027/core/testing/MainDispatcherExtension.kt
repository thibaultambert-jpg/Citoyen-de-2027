package fr.citoyende2027.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/** Replaces Dispatchers.Main (used by viewModelScope) with a test dispatcher. */
class MainDispatcherExtension(val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()) :
    BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) = Dispatchers.setMain(testDispatcher)

    override fun afterEach(context: ExtensionContext) = Dispatchers.resetMain()
}
