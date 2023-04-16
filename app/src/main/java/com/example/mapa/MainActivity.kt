@file:Suppress("DEPRECATION")

package com.example.mapa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mapa.API.Direcciones
import com.example.mapa.API.DireccionesApi
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*


class MainActivity : AppCompatActivity() {
    private var marcador1: Marker? = null
    private var marcador2: Marker? = null
    private var endPoint: GeoPoint? = null
    private var startPoint: GeoPoint? = null
    private var line = Polyline()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    var map: MapView? = null
    private lateinit var btnDestino: Button
    private lateinit var btnInico: Button

    private val direccionesApi: DireccionesApi by lazy {
        Direcciones.retrofitService
    }

    //your items
    var items = ArrayList<OverlayItem>()

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        val ctx: Context = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        setContentView(R.layout.activity_main)


        map = findViewById<View>(R.id.map) as MapView
        map?.setBuiltInZoomControls(true)
        btnDestino = findViewById(R.id.btnpos2)
        btnInico = findViewById(R.id.btnpos1)
        map!!.setTileSource(TileSourceFactory.MAPNIK)

        val mapController = map!!.controller
        mapController.setZoom(19)
        startPoint = GeoPoint(0, 0)
        mapController.setCenter(startPoint)

        //GEOLOCALIZACION INICIADA
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        var hasCenteredMap = false

        startPoint = GeoPoint(61.480145, 23.501203)
        mapController.setCenter(startPoint)
        hasCenteredMap = true

        locationCallback = object : LocationCallback() {
            @Suppress("NAME_SHADOWING")
            override fun onLocationResult(locationResult: LocationResult) {
                btnInico.setOnClickListener{
                    val lastLocation = locationResult.lastLocation
                    startPoint = GeoPoint(lastLocation!!.latitude, lastLocation.longitude)
                    marcador1?.position = startPoint
                    mapController.setZoom(19)
                    mapController.setCenter(startPoint)
                    hasCenteredMap = true
                }

                if (marcador2 != null && line.distance > 0) {
                    line.setPoints(emptyList())
                    map?.overlays?.remove(line)
                    agregarLineas()
                }
                map?.invalidate()
            }
        }


        //CONFIGRANDO UBICACION
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        marcador1 = Marker(map)
        marcador1?.position = startPoint
        marcador1?.setAnchor(Marker.ANCHOR_BOTTOM, Marker.ANCHOR_CENTER)
        marcador1?.title = "UBICACION ACTUAL"
        marcador1?.setIcon(getResources().getDrawable(org.osmdroid.gpkg.R.drawable.marker_default_focused_base))

        map?.overlays?.add(marcador1)
        map?.invalidate()


        val mOverlay: ItemizedOverlayWithFocus<OverlayItem> = ItemizedOverlayWithFocus(
            items, object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem?> {
                override fun onItemSingleTapUp(index: Int, item: OverlayItem?): Boolean {
                    return true
                }

                override fun onItemLongPress(index: Int, item: OverlayItem?): Boolean {
                    return false
                }
            }, ctx
        )
        mOverlay.setFocusItemsOnTap(true)

        // UBICACION DESTINO
        btnDestino.setOnClickListener {
            val touchOverlay = object : Overlay() {
                private val gestureDetector =
                    GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onLongPress(e: MotionEvent) {
                            //OBTENCION DE COORDENADAS
                            endPoint =
                                map?.projection!!.fromPixels(
                                    e.x.toInt(),
                                    e.y.toInt()
                                ) as GeoPoint?

                            map?.overlays?.remove(marcador2)
                            marcador2 = Marker(map)
                            marcador2?.position = endPoint
                            marcador2?.setAnchor(Marker.ANCHOR_BOTTOM, Marker.ANCHOR_CENTER)
                            marcador2?.title = "DESTINO"
                            marcador2?.setIcon(getResources().getDrawable(org.osmdroid.gpkg.R.drawable.marker_default_focused_base))
                            map?.overlays?.add(marcador2)
                            line.setPoints(emptyList())
                            map?.overlays?.remove(line)
                            agregarLineas()
                            map?.invalidate()
                        }
                    })

                override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
                    gestureDetector.onTouchEvent(event)
                    return super.onTouchEvent(event, mapView)
                }
            }

            //AGREGA OVERS AL MAPITA
            map?.overlays?.add(touchOverlay)
            map?.overlays!!.add(mOverlay)
        }
    }

    override fun onResume() {
        super.onResume()
        map!!.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        map!!.onPause()
        stopLocationUpdates()
    }

    fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 0
            )
        }
    }

    override fun onRequestPermissionsResult( requestCode: Int, permissions: Array<String>, grantResults: IntArray ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            0 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    return
                } else {
                    checkPermissions()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    fun agregarLineas() {
        CoroutineScope(Dispatchers.IO).launch {
            val inicio = "${startPoint!!.longitude},${startPoint!!.latitude}"
            val final = "${endPoint!!.longitude},${endPoint!!.latitude}"
            val api = "5b3ce3597851110001cf6248df42a8451614433c89fde58819755646"
            val coordenadas = direccionesApi.getDirections(api, inicio, final)
            val features = coordenadas.features
            for (feature in features) {
                val geometry = feature.geometry
                val coordinates = geometry.coordinates

                for (coordenada in coordinates) {
                    val punto = GeoPoint(coordenada[1], coordenada[0])
                    line.addPoint(punto)
                }
                map?.overlays?.add(line)
            }
        }
    }

}