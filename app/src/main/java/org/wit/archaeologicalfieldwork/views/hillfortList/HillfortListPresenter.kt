package org.wit.archaeologicalfieldwork.views.hillfortList

import android.view.MenuItem
import android.widget.ImageButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.startActivityForResult
import org.wit.archaeologicalfieldwork.activities.SettingsActivity
import org.wit.archaeologicalfieldwork.models.Favourite
import org.wit.archaeologicalfieldwork.models.Hillfort
import org.wit.archaeologicalfieldwork.models.stores.firebase.FavouriteFirebaseStore
import org.wit.archaeologicalfieldwork.models.stores.firebase.HillfortFirebaseStore
import org.wit.archaeologicalfieldwork.models.stores.firebase.ImageFirebaseStore
import org.wit.archaeologicalfieldwork.models.stores.firebase.RatingFirebaseStore
import org.wit.archaeologicalfieldwork.views.BasePresenter
import org.wit.archaeologicalfieldwork.views.BaseView
import org.wit.archaeologicalfieldwork.views.VIEW
import org.wit.archaeologicalfieldwork.views.hillfort.HillfortView
import org.wit.archaeologicalfieldwork.views.hillfortMaps.HillfortMapsView
import java.util.*

class HillfortListPresenter(view: BaseView) : BasePresenter(view), AnkoLogger{

    var filterFavourites = false

    fun loadHillforts(){
        async(UI){
            (app.ratings as RatingFirebaseStore).fetchRatings {
                (app.favourites as FavouriteFirebaseStore).fetchFavourites {
                    (app.hillforts as HillfortFirebaseStore).fetchHillforts {
                        (app.images as ImageFirebaseStore).fetchImages {
                            async(UI) {
                                doShowFilteredHillforts(app.hillforts.findAll())
                            }
                        }
                    }
                }
            }
            //if images fetch before hillforts do, they will be simply loaded in and ready for when hillforts do, but this is unlikely.
            //if images fetch after hillforts, the view will simply reload with loaded images.

        }
    }

    fun doShowFilteredHillforts(hillforts: List<Hillfort>){
        async(UI) {
            if(filterFavourites){
                val hillfortsToShow = ArrayList<Long>()
                app.favourites.findAll().filter { it.addedBy == FirebaseAuth.getInstance().currentUser?.email }.forEach {
                    hillfortsToShow.add(it.hillfortId)
                }
                view?.showHillforts(hillforts.filter { hillfort ->  hillfortsToShow.contains(hillfort.id)})
            }
            else{
                view?.showHillforts(hillforts)
            }
        }
    }


    fun doAddHillfort(){
        view?.startActivityForResult<HillfortView>(0)
    }

    fun doEditHillfort(hillfort: Hillfort){
        view?.navigateTo(VIEW.HILLFORT, 0, "hillfort_edit", hillfort)
    }

    fun doShowHillfortsMap(){
        view?.startActivity<HillfortMapsView>()
    }

    fun doViewSettings(){
        view?.startActivity<SettingsActivity>()
    }

    fun doSetAsFavourite(hillfort: Hillfort, favouriteButton: ImageButton){
        async(UI){
            val foundFavourites = app.favourites.findAll().filter { it.hillfortId == hillfort.id && it.addedBy == FirebaseAuth.getInstance().currentUser?.email }
            if(foundFavourites.isEmpty()){
                async(UI){
                    val favourite = Favourite(addedBy = FirebaseAuth.getInstance().currentUser?.email!!, hillfortId = hillfort.id)
                    app.favourites.create(favourite)
                }
                favouriteButton.setImageResource(android.R.drawable.star_big_on)
            }
            else{
                async(UI) {
                    app.favourites.delete(foundFavourites.first())
                }
                favouriteButton.setImageResource(android.R.drawable.star_big_off)
            }
        }
    }

    fun doFilterFavourites(item: MenuItem) {
        item.isChecked = !item.isChecked
        if(item.isChecked){
            item.setIcon(android.R.drawable.star_big_on)
        }
        else{
            item.setIcon(android.R.drawable.star_big_off)
        }
        filterFavourites = item.isChecked
        loadHillforts()
    }
}