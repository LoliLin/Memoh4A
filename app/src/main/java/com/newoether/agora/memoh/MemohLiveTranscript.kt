package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject

/**
 * Runtime-delta merge for the live transcript, extracted from MemohViewModel to keep the
 * ViewModel under the 800-line source gate.
 *
 * Semantics follow the reverse-engineered Memoh web socket protocol (web client):
 * - progress_appends: locate the block by id + type == "tool", append each entry to that
 *   block's progress array and refresh input when the event carries one. Unknown ids are skipped.
 * - user_turn_upserts: the server confirms the optimistic user turn; the first turn_id is
 *   recorded on the ViewModel for later reconciliation against [MemohViewModel.pendingUserText]
 *   (no dedicated UI yet).
 */
internal fun MemohViewModel.applyRuntimeDelta(delta: JSONObject, sid: String) {
    val replacement = delta.optJSONObject("current_run_view")
    if (replacement != null) { applyRunView(replacement); return }
    if (delta.optBoolean("reset_messages", false)) liveJson = JSONArray()
    delta.optJSONArray("message_upserts")?.let { upserts ->
        for (i in 0 until upserts.length()) upserts.optJSONObject(i)?.let(::upsertBlock)
    }
    delta.optJSONArray("message_appends")?.let { appends ->
        for (i in 0 until appends.length()) {
            val update = appends.optJSONObject(i) ?: continue
            val id = update.optInt("id", -1)
            if (id < 0) continue
            val existing = liveJson.blockById(id)
            if (existing != null) {
                existing.put("content", existing.optString("content") + update.optString("content"))
            } else liveJson.put(JSONObject(update.toString()))
        }
    }
    delta.optJSONArray("progress_appends")?.let { appends ->
        for (i in 0 until appends.length()) {
            val update = appends.optJSONObject(i) ?: continue
            val id = update.optInt("id", -1)
            if (id < 0) continue
            val existing = liveJson.blockById(id) ?: continue
            if (existing.optString("type") != "tool") continue
            when (val progress = update.opt("progress")) {
                is JSONArray -> for (j in 0 until progress.length()) existing.appendProgress(progress.opt(j))
                JSONObject.NULL, null -> Unit
                else -> existing.appendProgress(progress)
            }
            update.opt("input")?.takeIf { it != JSONObject.NULL }?.let { existing.put("input", it) }
        }
    }
    delta.optJSONArray("user_turn_upserts")?.let { upserts ->
        for (i in 0 until upserts.length()) {
            val turn = upserts.optJSONObject(i) ?: continue
            val turnId = turn.optString("turn_id")
            if (turnId.isNotBlank()) {
                recordPendingUserTurnId(turnId)
                break
            }
        }
    }
    delta.optJSONObject("run")?.let { patch ->
        if (patch.optString("run_id") == runId && patch.has("status"))
            currentRunStatus = patch.optString("status")
    }
    liveBlocks = decodeBlocks(liveJson)
    pruneResolvedDecisions()
    settleIfTerminal(sid)
}

private fun JSONArray.blockById(id: Int): JSONObject? {
    for (i in 0 until length()) {
        val obj = optJSONObject(i) ?: continue
        if (obj.optInt("id", -1) == id) return obj
    }
    return null
}

private fun JSONObject.appendProgress(entry: Any?) {
    val progress = optJSONArray("progress") ?: JSONArray().also { put("progress", it) }
    progress.put(entry ?: JSONObject.NULL)
}
