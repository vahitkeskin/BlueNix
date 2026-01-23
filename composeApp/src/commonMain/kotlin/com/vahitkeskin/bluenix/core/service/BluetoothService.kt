package com.vahitkeskin.bluenix.core.service

import com.vahitkeskin.bluenix.core.model.BluetoothDeviceDomain
import kotlinx.coroutines.flow.Flow

interface BluetoothService {
    fun scanDevices(): Flow<List<BluetoothDeviceDomain>>
}