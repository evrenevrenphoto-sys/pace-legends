package com.pace.legends.domain.manager

/**
 * Kullanıcı bu ay zaten pist seçmiş ve kilitlemiş
 */
class TrackLockedException(message: String) : Exception(message)
