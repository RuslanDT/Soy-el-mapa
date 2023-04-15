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
import android.widget.Toast
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
    private var firstMarker: Marker? = null
    private var secondMarker: Marker? = null
    private var endPoint: GeoPoint? = null
    private var startPoint: GeoPoint? = null
    private var line = Polyline()
    private var colocar = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    var map: MapView? = null
    private lateinit var boton: Button

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
        boton = findViewById(R.id.ubi)
        map!!.setTileSource(TileSourceFactory.MAPNIK)

        val mapController = map!!.controller
        mapController.setZoom(19)
        startPoint = GeoPoint(0, 0)
        mapController.setCenter(startPoint)

        // Inicializa la variable de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configura el callback de ubicación
        var hasCenteredMap = false
        locationCallback = object : LocationCallback() {
            @Suppress("NAME_SHADOWING")
            override fun onLocationResult(locationResult: LocationResult) {
                val lastLocation = locationResult.lastLocation
                startPoint = GeoPoint(lastLocation!!.latitude, lastLocation.longitude)
                firstMarker?.position = startPoint
                if (!hasCenteredMap) {
                    mapController.setCenter(startPoint)
                    hasCenteredMap = true
                }
                if (secondMarker != null && line.distance > 0) {
                    line.setPoints(emptyList())
                    map?.overlays?.remove(line)
                    coords()
                }
                map?.invalidate()
            }
        }


        // Configura la solicitud de ubicación
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        items.add(
            OverlayItem(
                "Title", "Description", GeoPoint(0.0, 0.0)
            )
        )

        firstMarker = Marker(map)
        firstMarker?.position = startPoint
        firstMarker?.setAnchor(Marker.ANCHOR_BOTTOM, Marker.ANCHOR_CENTER)
        firstMarker?.title = "UBICACION ACTUAL"
        map?.overlays?.add(firstMarker)
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

        // Crear un nuevo Overlay para capturar eventos de toque
        val touchOverlay = object : Overlay() {

            private val gestureDetector =
                GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onLongPress(e: MotionEvent) {
                        // Obtener las coordenadas del punto donde se realizó la pulsación
                        if (colocar) {
                            endPoint =
                                map?.projection!!.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint?
                            map?.overlays?.remove(secondMarker)
                            secondMarker = Marker(map)
                            secondMarker?.position = endPoint
                            secondMarker?.setAnchor(Marker.ANCHOR_BOTTOM, Marker.ANCHOR_CENTER)
                            secondMarker?.title = "DESTINO"
                            map?.overlays?.add(secondMarker)

                            line.setPoints(emptyList())
                            map?.overlays?.remove(line)
                            coords()
                        }
                        // Redibujar el mapa para mostrar el nuevo marcador
                        map?.invalidate()
                    }
                })

            override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
                gestureDetector.onTouchEvent(event)
                return super.onTouchEvent(event, mapView)
            }
        }


        // Agregar el Overlay de eventos de toque al mapa
        map?.overlays?.add(touchOverlay)
        map?.overlays!!.add(mOverlay)

        var mensaje = true
        boton.setOnClickListener {
            if (colocar) {
                colocar = false
                boton.text = "Agregar Ubicacion"
                map?.setBuiltInZoomControls(true)
            } else {
                colocar = true
                boton.text = "Habilitar zoom"
                map?.setBuiltInZoomControls(false)
                if (mensaje) {
                    Toast.makeText(
                        ctx, "TIP: Manten presionado para agregar la ubicacion", Toast.LENGTH_LONG
                    ).show()
                    mensaje = false
                }
            }
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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
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


    fun coords() {
        CoroutineScope(Dispatchers.IO).launch {
            val inicio = "${startPoint!!.longitude},${startPoint!!.latitude}"
            val final = "${endPoint!!.longitude},${endPoint!!.latitude}"
            val api = "5b3ce3597851110001cf6248195446ce6bac45e7851606b557eab502"
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