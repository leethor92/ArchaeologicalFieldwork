package org.wit.archaeologicalfieldwork.views.hillfort

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.DatePicker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_hillfort.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.jetbrains.anko.*
import org.wit.archaeologicalfieldwork.R
import org.wit.archaeologicalfieldwork.helpers.*
import org.wit.archaeologicalfieldwork.models.Hillfort
import org.wit.archaeologicalfieldwork.models.Location
import org.wit.archaeologicalfieldwork.models.Visit
import org.wit.archaeologicalfieldwork.views.*
import java.text.SimpleDateFormat
import java.util.*

class HillfortPresenter(view: BaseView) : BasePresenter(view){

    val locationRequest = createDefaultLocationRequest()
    var defaultLocation = Location(52.245696, -7.139102, 15f)
    var locationService: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(view)
    private lateinit var editedImage: String
    private var editing: Boolean = false
    var date: Date? = null
    var hillfort = Hillfort()
    var map: GoogleMap? = null

    init{
        if (view.intent.hasExtra("hillfort_edit")) {
            editing = true
            hillfort = view.intent.extras.getParcelable("hillfort_edit")
            view.showHillfort(hillfort)
        } else{
            if (checkLocationPermissions(view)) {
                doSetCurrentLocation()
            } else{
              locationUpdate(defaultLocation)
            }
        }
    }

    fun doAddOrSave(){
        hillfort.name = view?.hillfortName!!.text.toString()
        hillfort.description = view?.hillfortDescription!!.text.toString()
        hillfort.addedBy = app.user.email!!

        if(view?.visitedCheckBox!!.isChecked){
            val visit = Visit()
            visit.hillfortId = hillfort.id
            visit.addedBy = app.user.email!!
            if(date != null){
                visit.date = date!!
            }
            else{
                visit.date = Date()
            }
            async(UI) {
                app.visits.create(visit)
            }

        }
        else{
            async(UI) {
                app.visits.delete(app.visits.findAll().filter { it.hillfortId == hillfort.id }.find { it.addedBy == app.user.email!!}!!)
            }
        }
        if (hillfort.name.isNotEmpty()) {
            async(UI) {
                if (editing){app.hillforts.update(hillfort)}
                else {
                    app.hillforts.create(hillfort)
                }
                view?.finish()
                }
        } else{ view?.toast(R.string.no_title_toast) }
    }

    fun doCancel(){
        view?.finish()
    }

    fun doDelete(){
        async(UI) {
            app.hillforts.delete(hillfort)
            view?.finish()
        }
    }

    fun doSelectImage(){
        view?.let{
            showImagePicker(view!!, ADD_IMAGE_REQUEST)
        }
    }

    fun doSetLocation() {
        view?.navigateTo(VIEW.LOCATION, LOCATION_REQUEST, "location", hillfort.location)
    }

    override fun doActivityResult(requestCode: Int, data: Intent?){
        async(UI) {
            (view as HillfortView).showImages(app.images.findAll().filter { it.hillfortId == hillfort.id })
        }
            when(requestCode){
                ADD_IMAGE_REQUEST -> {
                    //todo: fix the below.
                    if(data != null){
                        //hillfort.images.add(data.data.toString())
                    }
                }
                IMAGE_EDIT_REQUEST -> {
                    if(data != null){
                        // hillfort.images[hillfort.images.indexOf(editedImage)] = data.data.toString()
                    }
                    else{
                        //hillfort.images.remove(editedImage)
                    }
                }
                LOCATION_REQUEST -> {
                    if (data != null) {
                        locationUpdate(hillfort.location)
                        hillfort.location = data.extras.getParcelable("location")
                    }
                }
            }
    }

    override fun doEditImage(image: String) {
        editedImage = image
        showImagePicker(view!!, IMAGE_EDIT_REQUEST)
    }

    fun doSetVisited(){
        if(view?.visitedCheckBox!!.isChecked){
            view?.alert {
                isCancelable = false
                lateinit var datePicker: DatePicker
                customView {
                    verticalLayout {
                        datePicker = datePicker {
                            maxDate = System.currentTimeMillis()
                        }
                    }
                }
                yesButton {
                    val rawDate = "${datePicker.dayOfMonth}/${datePicker.month + 1}/${datePicker.year}"
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
                    date = sdf.parse(rawDate)
                    view?.visitedCheckBox!!.text = view?.resources!!.getString(R.string.visited_time, simplifyDate(date!!))
                }
                noButton {
                    view?.visitedCheckBox!!.text = null
                }
            }?.show()
        }
        else{
            view?.visitedCheckBox!!.setText(R.string.visited)
        }
    }

    fun locationUpdate(location: Location){
        hillfort.location = location
        view?.hillfortLat?.text = view?.resources!!.getString(R.string.hillfortLat, location.lat)
        view?.hillfortLng?.text = view?.resources!!.getString(R.string.hillfortLng, location.lng)
        map?.clear()
        map?.uiSettings?.isZoomControlsEnabled = true
        val options = MarkerOptions().title(hillfort.name).position(LatLng(location.lat, location.lng))
        map?.addMarker(options)
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.lat, location.lng), location.zoom))
        view?.showHillfort(hillfort)
    }

    fun doConfigureMap(map: GoogleMap) {
        this.map = map
        locationUpdate(hillfort.location)
    }

    override fun doRequestPermissionsResult(requestCode: Int, permission: Array<String>, grantResults: IntArray) {
        if (isPermissionGranted(requestCode, grantResults)) {
            doSetCurrentLocation()
        } else {
            // permissions denied, so use the default location
            locationUpdate(defaultLocation)
        }
    }

    @SuppressLint("MissingPermission")
    fun doResartLocationUpdates() {
        var locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                if (locationResult != null) {
                    val l = locationResult.locations.last()
                    locationUpdate(Location(l.latitude, l.longitude, hillfort.location.zoom))
                }
            }
        }
        if (!editing) {
            locationService.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    @SuppressLint("MissingPermission")
    fun doSetCurrentLocation() {
        locationService.lastLocation.addOnSuccessListener {
            locationUpdate(Location(it.latitude, it.longitude, 15f))
        }
    }
}