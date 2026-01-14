package com.pace.legends.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_badges")
data class UserBadge(
    @PrimaryKey
    val badgeId: String,
    val earnedTimestamp: Long
)

enum class BadgeType(val id: String, val title: String, val description: String) {
    FORMATION_LAP("formation_lap", "Formasyon Turu", "Yarışa hazır! İlk adımlarını attın."),
    CHECKERED_FLAG("checkered_flag", "Damalı Bayrak", "İlk pistini tamamladın. Efsane başlıyor!"),
    NIGHT_RACE("night_race", "Gece Yarışı", "Gece seansı başladı. (22:00-05:00 arası yürüyüş)"),
    ENDURANCE_PILOT("endurance_pilot", "Dayanıklılık Pilotu", "Tam bir maraton mesafesi (42km) kat ettin."),
    
    // 🏆 ŞAMPİYONLUK ROZETLERİ
    PERIOD_CHAMPION("period_champion", "Dönem Şampiyonu", "Bir yarış dönemini 1. bitirdin!"),
    PODIUM_FINISH("podium_finish", "Podyum", "Dönem sonunda ilk 3'e girdin.")
}
