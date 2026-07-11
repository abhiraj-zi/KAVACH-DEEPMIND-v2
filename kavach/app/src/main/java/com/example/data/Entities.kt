package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incident_reports")
data class IncidentReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val triggerType: String, // "VOICE" or "BUTTON" or "VOLUME"
    val timestamp: Long = System.currentTimeMillis(),
    val codePhraseTriggered: String,
    val capturedTranscript: String,
    val contactsNotified: String, // Comma-separated list of names/phones
    val safetyRouteTaken: String, // Description of route taken
    val generatedReport: String,  // Text report written by Gemini Scribe
    val isResolved: Boolean = true
)

@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val relation: String = "Guardian"
)
