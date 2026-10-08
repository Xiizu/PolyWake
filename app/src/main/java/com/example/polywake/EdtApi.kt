package com.example.polywake

import android.content.Context
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Course(
    val id: Long,
    val start_at: String,
    val end_at: String,
    val modules_for_blocks: String?,
    val rooms_for_blocks: String?,
    val teachers_for_blocks: String?,
    val categories: String?
)

object NetworkManager {
    private val gson = Gson()

    fun getCachedEvents(context: Context, dateStr: String): List<Course> {
        val file = File(context.cacheDir, "edt_cache_$dateStr.json")
        if (!file.exists()) return emptyList()
        return try {
            gson.fromJson(file.readText(), Array<Course>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEvents(context: Context, start: String, end: String, groups: Set<String>): List<Course> {
        return withContext(Dispatchers.IO) {
            try {
                val queryParams = mutableListOf("start=$start", "end=$end")
                groups.forEachIndexed { index, groupId ->
                    queryParams.add("timetables[$index]=$groupId")
                }
                val urlString = "https://edt-v2.univ-nantes.fr/events?" + queryParams.joinToString("&")

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                if (connection.responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }

                    // 2. On sauvegarde dans un fichier nommé avec la date de début
                    File(context.cacheDir, "edt_cache_$start.json").writeText(json)

                    gson.fromJson(json, Array<Course>::class.java).toList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun fetchGroups(): List<Pair<String, String>> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // On télécharge le code HTML de la page que vous venez de montrer
                val url = "https://edt-v2.univ-nantes.fr/polytech/educational_groups"
                val document = org.jsoup.Jsoup.connect(url).get()

                // On cible spécifiquement la zone contenant les cases à cocher
                val labels = document.select("#educational_groups label")
                val groupList = mutableListOf<Pair<String, String>>()

                for (label in labels) {
                    val inputId = label.attr("for")
                    val input = document.select("input[id=$inputId]").first()

                    val groupId = input?.attr("value") ?: continue
                    val groupName = label.text().trim()

                    // --- LE FILTRE ANTI-POLLUTION ---

                    // 1. On ignore les immenses groupes qui englobent toute la promotion
                    if (groupName.contains("PROMOTION", ignoreCase = true)) {
                        continue
                    }

                    // 2. (Optionnel) Vous pouvez ajouter d'autres filtres ici si besoin
                    // if (groupName.startsWith("LANG-")) continue // Pour ignorer les sous-groupes de langues

                    groupList.add(Pair(groupId, groupName))
                }

                // --- LE DÉDOUBLONNAGE ---
                // distinctBy { it.first } s'assure qu'un même ID numérique n'apparaît qu'une seule fois
                // sortedBy { it.second } range tout par ordre alphabétique propre
                groupList.distinctBy { it.first }.sortedBy { it.second }

            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}