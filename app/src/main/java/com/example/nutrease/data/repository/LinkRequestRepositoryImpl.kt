package com.example.nutrease.data.repository

import com.example.nutrease.data.dto.LinkRequestDto
import com.example.nutrease.data.dto.LinkRequestWithPatientDto
import com.example.nutrease.data.dto.toDomain
import com.example.nutrease.domain.model.LinkRequest
import com.example.nutrease.domain.model.LinkRequestStatus
import com.example.nutrease.domain.model.LinkRequestWithPatient
import com.example.nutrease.domain.repository.LinkRequestRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * Implementazione di [LinkRequestRepository] su PostgREST (`richiesta_collegamento`).
 * Accept/reject sono semplici UPDATE: il lavoro vero (creazione fascicolo+chat
 * all'accettazione) lo fa un trigger sul DB con privilegi elevati, perché il client
 * non ha policy di INSERT su quelle tabelle.
 */
class LinkRequestRepositoryImpl(
    private val supabase: SupabaseClient
) : LinkRequestRepository {

    override suspend fun sendRequest(
        patientTaxCode: String,
        specialistTaxCode: String,
        message: String?
    ): Result<LinkRequest> = withContext(Dispatchers.IO) {
        runCatching {
            // Upsert (non semplice insert) sulla UNIQUE (CodFiscalePaziente,
            // CodFiscaleSpecialista): se esiste già una richiesta rifiutata verso questo
            // specialista, la riporta a 'In Attesa' invece di violare la UNIQUE (23505).
            // Il body è costruito a mano come JsonObject: il serializer di default usa
            // encodeDefaults=false, quindi i campi pari al default (Stato='In Attesa',
            // DataRisposta=null, MotivazioneRifiuto=null) verrebbero omessi dal payload e
            // NON azzerati sul ramo UPDATE → violazione di chk_risposta_coerente. Qui li
            // inviamo esplicitamente (DataRisposta/MotivazioneRifiuto a JsonNull).
            val body = buildJsonArray {
                add(
                    buildJsonObject {
                        put("CodFiscalePaziente", patientTaxCode)
                        put("CodFiscaleSpecialista", specialistTaxCode)
                        put("Stato", LinkRequestStatus.PENDING.dbLabel)
                        put("MessaggioRichiesta", message)            // null → JsonNull
                        put("DataRichiesta", Clock.System.now().toString())
                        put("DataRisposta", null as String?)          // azzera (chk_risposta_coerente)
                        put("MotivazioneRifiuto", null as String?)    // azzera
                    }
                )
            }
            supabase.from("richiesta_collegamento")
                .upsert(body) {
                    onConflict = "CodFiscalePaziente,CodFiscaleSpecialista"
                    select()
                }
                .decodeSingle<LinkRequestDto>()
                .toDomain()
        }
    }

    override suspend fun getReceivedRequests(
        specialistTaxCode: String,
        status: LinkRequestStatus
    ): Result<List<LinkRequestWithPatient>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("richiesta_collegamento")
                .select(
                    Columns.raw(
                        """"IdRichiesta","CodFiscalePaziente","CodFiscaleSpecialista",
                           "Stato","MessaggioRichiesta","DataRichiesta","DataRisposta",
                           "MotivazioneRifiuto",paziente("Nome","Cognome")"""
                            .trimIndent()
                    )
                ) {
                    filter {
                        eq("CodFiscaleSpecialista", specialistTaxCode)
                        eq("Stato", status.dbLabel)
                    }
                    order("DataRichiesta", Order.DESCENDING)
                }
                .decodeList<LinkRequestWithPatientDto>()
                .map { it.toDomain() }
        }
    }

    override suspend fun getSentRequests(
        patientTaxCode: String
    ): Result<List<LinkRequest>> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.from("richiesta_collegamento")
                .select {
                    filter { eq("CodFiscalePaziente", patientTaxCode) }
                    order("DataRichiesta", Order.DESCENDING)
                }
                .decodeList<LinkRequestDto>()
                .map { it.toDomain() }
        }
    }

    override suspend fun acceptRequest(requestId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.from("richiesta_collegamento").update({
                    set("Stato", LinkRequestStatus.ACCEPTED.dbLabel)
                    set("DataRisposta", Clock.System.now().toString())
                }) {
                    filter { eq("IdRichiesta", requestId) }
                }
                Unit
            }
        }

    override suspend fun rejectRequest(requestId: Long, reason: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(reason.isNotBlank()) { "La motivazione del rifiuto è obbligatoria" }
                supabase.from("richiesta_collegamento").update({
                    set("Stato", LinkRequestStatus.REJECTED.dbLabel)
                    set("DataRisposta", Clock.System.now().toString())
                    set("MotivazioneRifiuto", reason.trim())
                }) {
                    filter { eq("IdRichiesta", requestId) }
                }
                Unit
            }
        }
}