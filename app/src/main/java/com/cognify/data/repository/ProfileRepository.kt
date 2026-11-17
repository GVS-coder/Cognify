package com.cognify.data.repository

import android.net.Uri
import com.cognify.data.model.Resource
import com.cognify.data.model.User

interface ProfileRepository {
    suspend fun updateDisplayName(displayName: String): Resource<Unit>
    suspend fun updateProfilePhoto(photoUri: Uri?): Resource<Unit>
    suspend fun deleteAccount(): Resource<Unit>
    fun getCurrentUserFlow(): kotlinx.coroutines.flow.Flow<User?>
}
