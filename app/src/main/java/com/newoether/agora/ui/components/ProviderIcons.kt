package com.newoether.agora.ui.components

import com.newoether.agora.R

/**
 * Single source of truth mapping a built-in provider name to its brand icon drawable.
 * Returns 0 for unknown / custom providers (callers fall back to a generic Cloud icon).
 */
fun providerIcon(name: String): Int = when (name.lowercase()) {
    // The platform's own managed provider is Memoh, not a generic cloud.
    "memoh", "memoh cloud", "memoh built-in", "builtin", "built-in" -> R.drawable.memoh_logo
    "google" -> R.drawable.provider_google
    "openai" -> R.drawable.provider_openai
    "anthropic" -> R.drawable.provider_anthropic
    "deepseek" -> R.drawable.provider_deepseek
    "qwen" -> R.drawable.provider_qwen
    "groq" -> R.drawable.provider_groq
    "ollama" -> R.drawable.provider_ollama
    "open router" -> R.drawable.provider_openrouter
    "opencode go" -> R.drawable.provider_opencode
    else -> 0
}
