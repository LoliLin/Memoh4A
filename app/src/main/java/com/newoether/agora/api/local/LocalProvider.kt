package com.newoether.agora.api.local

import android.content.Context
import com.newoether.agora.api.GenerationError
import com.newoether.agora.api.LlmProvider
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Compatibility boundary for imported Agora settings; execution belongs to the Memoh server. */
@Suppress("UNUSED_PARAMETER")
class LocalProvider(context: Context, settings: SettingsRepository) : LlmProvider {
    override val name: String = Constants.PROVIDER_LOCAL
    override val defaultBaseUrl: String = ""

    override suspend fun fetchModels(apiKey: String, baseUrl: String?): List<String> = emptyList()

    override fun generateResponse(messages: List<ChatMessage>, config: ProviderConfig): Flow<StreamEvent> =
        flowOf(StreamEvent.Error(GenerationError.LocalModel(
            "Local inference is unavailable in Memoh4A. Select a Memoh bot to run this conversation.",
        )))
}
