package com.ryu.musicplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 사용자가 만든 재생목록(이름 -> 곡 id 집합)을 기기에 저장/복원한다.
 * 폴더 기반 그룹과 달리, 사용자가 직접 만든 목록만 여기에 보관된다.
 */
class PlaylistStore(context: Context) {

    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): LinkedHashMap<String, MutableSet<Long>> {
        val result = LinkedHashMap<String, MutableSet<Long>>()
        val raw = prefs.getString(KEY, null) ?: return result
        runCatching {
            val obj = JSONObject(raw)
            val order = obj.optJSONArray(ORDER) ?: JSONArray()
            for (i in 0 until order.length()) {
                val name = order.getString(i)
                val ids = obj.optJSONArray(name) ?: continue
                val set = LinkedHashSet<Long>()
                for (j in 0 until ids.length()) set.add(ids.getLong(j))
                result[name] = set
            }
        }
        return result
    }

    fun save(playlists: LinkedHashMap<String, MutableSet<Long>>) {
        val obj = JSONObject()
        val order = JSONArray()
        playlists.forEach { (name, ids) ->
            order.put(name)
            obj.put(name, JSONArray().apply { ids.forEach { put(it) } })
        }
        obj.put(ORDER, order)
        prefs.edit().putString(KEY, obj.toString()).apply()
    }

    companion object {
        private const val KEY = "data"
        private const val ORDER = "__order__"
    }
}
