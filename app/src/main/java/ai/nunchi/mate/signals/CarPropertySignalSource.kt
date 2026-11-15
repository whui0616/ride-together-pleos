package ai.nunchi.mate.signals

import ai.nunchi.engine.Gear
import ai.nunchi.engine.SignalUpdate
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

class CarPropertySignalSource(private val context: Context) : SignalSource {
    override val name = "AAOS CarProperty (속도·기어)"

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null

    override fun start(sink: (SignalUpdate) -> Unit) {
        stop()
        val car = Car.createCar(context) ?: return
        val cpm = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                when (value.propertyId) {
                    VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                        (value.value as? Float)?.let { sink(SignalUpdate.Speed(it * 3.6f)) }
                    VehiclePropertyIds.GEAR_SELECTION ->
                        (value.value as? Int)?.let { sink(SignalUpdate.GearChanged(gearOf(it))) }
                }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "property error $propertyId")
            }
        }
        if (context.checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED) {
            subscribe(cpm, cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        }
        subscribe(cpm, cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        this.car = car
        this.manager = cpm
        this.callback = cb
    }

    @Suppress("DEPRECATION")
    override fun stop() {
        callback?.let { manager?.unregisterCallback(it) }
        car?.disconnect()
        car = null
        manager = null
        callback = null
    }

    @Suppress("DEPRECATION")
    private fun subscribe(cpm: CarPropertyManager, cb: CarPropertyManager.CarPropertyEventCallback, id: Int, rate: Float) {
        try {
            cpm.registerCallback(cb, id, rate)
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission for property $id", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "property $id not supported", e)
        }
    }

    private fun gearOf(v: Int): Gear = when (v) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE
    }

    private companion object {
        const val TAG = "CarPropertySource"
    }
}
