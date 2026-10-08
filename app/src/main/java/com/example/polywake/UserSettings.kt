package com.example.polywake

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 1. Initialisation du fichier de sauvegarde nommé "settings"
val Context.dataStore by preferencesDataStore(name = "settings")

// 2. Le modèle qui représente tous les choix de l'utilisateur
data class UserSettings(
    val x_time: Int = 10,       // Temps de préparation au lever (minutes)
    val z_time: Int = 15,       // Temps de préparation avant de sortir (minutes)
    val address: String = "",   // Adresse du domicile
    val n_alarms: Int = 3,      // Nombre de réveils souhaités
    val interval: Int = 5,      // Intervalle entre chaque réveil (minutes)
    val groups: Set<String> = emptySet(), // Les ID des groupes choisis (ex: "116821")
    val alarms_enabled: Boolean = true
)

// 3. Le gestionnaire pour lire et écrire ces choix
class SettingsManager(private val context: Context) {

    // Définition des "clés" pour retrouver chaque paramètre
    private companion object {
        val X_TIME = intPreferencesKey("x_time")
        val Z_TIME = intPreferencesKey("z_time")
        val ADDRESS = stringPreferencesKey("address")
        val N_ALARMS = intPreferencesKey("n_alarms")
        val INTERVAL = intPreferencesKey("interval")
        val GROUPS = stringSetPreferencesKey("groups")
        val ALARMS_ENABLED = booleanPreferencesKey("alarms_enabled")
    }

    // Fonction pour LIRE les paramètres en temps réel
    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            x_time = preferences[X_TIME] ?: 10,
            z_time = preferences[Z_TIME] ?: 15,
            address = preferences[ADDRESS] ?: "",
            n_alarms = preferences[N_ALARMS] ?: 3,
            interval = preferences[INTERVAL] ?: 5,
            groups = preferences[GROUPS] ?: emptySet(),
            alarms_enabled = preferences[ALARMS_ENABLED] ?: true
        )
    }

    // Fonction pour SAUVEGARDER les paramètres
    suspend fun saveSettings(settings: UserSettings) {
        context.dataStore.edit { preferences ->
            preferences[X_TIME] = settings.x_time
            preferences[Z_TIME] = settings.z_time
            preferences[ADDRESS] = settings.address
            preferences[N_ALARMS] = settings.n_alarms
            preferences[INTERVAL] = settings.interval
            preferences[GROUPS] = settings.groups
            preferences[ALARMS_ENABLED] = settings.alarms_enabled
        }
    }
}