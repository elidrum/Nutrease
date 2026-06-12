package com.example.nutrease.domain.usecase

import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.SpecializationType
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.SpecialistDirectoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetLinkedSpecialistUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val specialistDirectoryRepository: SpecialistDirectoryRepository = mockk()
    private val useCase = GetLinkedSpecialistUseCase(authRepository, specialistDirectoryRepository)

    private val patient = AuthUser(
        id = "auth-1",
        email = "p@example.com",
        role = UserRole.PATIENT,
        taxCode = "P1"
    )
    private val specialistUser = AuthUser(
        id = "auth-2",
        email = "s@example.com",
        role = UserRole.SPECIALIST,
        taxCode = "S1"
    )

    private val linkedSpecialist = Specialist(
        taxCode = "S1",
        authUid = "uid-S1",
        firstName = "Marco",
        lastName = "Bianchi",
        email = "marco@nutrease.it",
        phone = null,
        vatNumber = "11111111111",
        info = null,
        specialization = SpecializationType.NUTRIZIONISTA,
        city = "Milano"
    )

    @Test
    fun `patient with active fascicolo gets the linked specialist`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns patient
        coEvery {
            specialistDirectoryRepository.getLinkedSpecialist("P1")
        } returns Result.success(linkedSpecialist)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals("Marco", result.getOrNull()?.firstName)
        coVerify { specialistDirectoryRepository.getLinkedSpecialist("P1") }
    }

    @Test
    fun `patient without active fascicolo gets null`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns patient
        coEvery {
            specialistDirectoryRepository.getLinkedSpecialist("P1")
        } returns Result.success(null)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `specialist user is rejected`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns specialistUser

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(IllegalStateException::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `unauthenticated user is rejected`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null

        val result = useCase()

        assertTrue(result.isFailure)
    }

    @Test
    fun `repository failure is propagated`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns patient
        coEvery {
            specialistDirectoryRepository.getLinkedSpecialist("P1")
        } returns Result.failure(IllegalStateException("boom"))

        val result = useCase()

        assertTrue(result.isFailure)
    }
}
