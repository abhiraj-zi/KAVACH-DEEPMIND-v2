package com.example.data

import kotlinx.coroutines.flow.Flow

class IncidentRepository(private val incidentDao: IncidentDao) {
    val allReports: Flow<List<IncidentReport>> = incidentDao.getAllReports()
    val allContacts: Flow<List<EmergencyContact>> = incidentDao.getAllContacts()

    suspend fun insertReport(report: IncidentReport) {
        incidentDao.insertReport(report)
    }

    suspend fun insertContact(contact: EmergencyContact) {
        incidentDao.insertContact(contact)
    }

    suspend fun deleteContactById(id: Int) {
        incidentDao.deleteContactById(id)
    }
}
