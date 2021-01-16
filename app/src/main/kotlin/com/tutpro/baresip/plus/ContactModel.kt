package com.tutpro.baresip.plus

import android.graphics.Bitmap
import android.net.Uri
import com.google.gson.annotations.SerializedName

class ContactModel {
    @SerializedName("id")
    private var id: String = ""

    @SerializedName("name")
    private var name: String? = ""

    @SerializedName("mobileNumber")
    private var mobileNumber: String? = ""

    @SerializedName("photo")
    private var photo: Bitmap? = null

    @SerializedName("photoURI")
    private var photoURI: Uri? = null


    fun getId(): String {
        return id
    }

    fun setId(id: String) {
        this.id = id
    }

    fun getName(): String? {
        return name
    }

    fun setName(name: String) {
        this.name = name
    }

    fun getMobileNumber(): String? {
        return mobileNumber
    }

    fun setMobileNumber(mobileNumber: String) {
        this.mobileNumber = mobileNumber
    }

    fun getPhoto(): Bitmap? {
        return photo
    }

    fun setPhoto(photo: Bitmap) {
        this.photo = photo
    }

    fun getPhotoURI(): Uri? {
        return photoURI
    }

    fun setPhotoURI(photoURI: Uri) {
        this.photoURI = photoURI
    }


    override fun toString(): String {
        return "ContactModel(id='$id', name=$name, mobileNumber=$mobileNumber, photo=$photo, photoURI=$photoURI)"
    }
}