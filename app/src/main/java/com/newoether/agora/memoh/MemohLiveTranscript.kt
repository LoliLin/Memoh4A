package com.newoether.agora.memoh

import org.json.JSONArray
import org.json.JSONObject

/**
 * Runtime-delta merge for the live transcript, separate from navigation and connection ownership.
 *
 * Semantics follow the reverse-engineered Memoh web socket protocol (web client):
 * - progress_appends: locate the block by id + type == "tool", append each entry to that
 *   block's progress array and refresh input when the event carries one. Unknown ids are skipped.
 * - user_turn_upserts: the server confirms the optimistic user turn; the first turn_id is
 *   recorded on the ViewModel for later reconciliation against [MemohViewModel.pendingUserText].
 * - steer_turn_upserts / steer_turn_removals: live edits to the queued steering text. They
 *   update the pending steer row in the live view so the composer reflects server state.
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
    applyRequestUserTurn(delta.optJSONObject("request_user_turn"))
    applySteerTurnUpserts(delta.optJSONArray("steer_turn_upserts"))
    applySteerTurnRemovals(delta.optJSONArray("steer_turn_removals"))
    delta.optJSONObject("run")?.let { patch ->
        if (patch.optString("run_id") == runId && patch.has("status"))
            currentRunStatus = patch.optString("status")
    }
    liveBlocks = decodeBlocks(liveJson)
    pruneResolvedDecisions()
    settleIfTerminal(sid)
}

/** Queued steering text the server mirrors back; shown as a live "steering" notice row. */
private fun MemohViewModel.applyRequestUserTurn(request: JSONObject?) {
    // Homem folds run.request_user_turn into the visible user turns when user_turns is
    // empty: it is the confirmed echo of the optimistic pending row.
    if (request == null || request.optString("turn_id").isBlank()) return
    recordPendingUserTurnId(request.optString("turn_id"))
}
private fun MemohViewModel.applySteerTurnUpserts(upserts: JSONArray?) {
    if (upserts == null) return
    for (i in 0 until upserts.length()) {
        val turn = upserts.optJSONObject(i) ?: continue
        val text = turn.optString("text")
        if (text.isBlank()) continue
        val existing = liveJson.steerRow()
        if (existing != null) existing.put("content", text)
        else liveJson.put(JSONObject()
            .put("type", "notice")
            .put("kind", "steer")
            .put("content", text))
    }
}

private fun MemohViewModel.applySteerTurnRemovals(removals: JSONArray?) {
    if (removals == null) return
    for (i in 0 until removals.length()) {
        val removed = removals.optJSONObject(i) ?: continue
        val row = liveJson.steerRow() ?: continue
        val text = removed.optString("text")
        if (text.isBlank() || row.optString("content") == text) {
            for (j in liveJson.length() - 1 downTo 0) {
                if (liveJson.optJSONObject(j) === row) { liveJson.remove(j); break }
            }
        }
    }
}

private fun JSONArray.steerRow(): JSONObject? {
    for (i in 0 until length()) {
        val obj = optJSONObject(i) ?: continue
        if (obj.optString("type") == "notice" && obj.optString("kind") == "steer") return obj
    }
    return null
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
