package com.gratitudegarden.app.model

data class Plant(
    val id: Int,
    val name: String,
    val emoji: String,
    val growthStage: String = "seedling",
    val health: String = "healthy"
)

data class UserStats(
    val coins: Int = 50,
    val streakDays: Int = 0
)