package com.example.ledgermesh.data.repository

import com.example.ledgermesh.data.db.dao.CategoryDao
import com.example.ledgermesh.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    suspend fun insert(category: CategoryEntity) = categoryDao.insert(category)

    suspend fun insertAll(categories: List<CategoryEntity>) = categoryDao.insertAll(categories)

    suspend fun update(category: CategoryEntity) = categoryDao.update(category)

    fun getAllFlow(): Flow<List<CategoryEntity>> = categoryDao.getAllFlow()

    suspend fun getById(id: String): CategoryEntity? = categoryDao.getById(id)

    suspend fun delete(category: CategoryEntity) = categoryDao.delete(category)
}
