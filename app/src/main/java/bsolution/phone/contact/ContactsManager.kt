/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package bsolution.phone.contact

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorDescription
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import android.util.Patterns
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.MutableLiveData
import bsolution.phone.LinphoneApplication.Companion.coreContext
import bsolution.phone.LinphoneApplication.Companion.corePreferences
import bsolution.phone.R
import bsolution.phone.utils.ImageUtils
import bsolution.phone.utils.PermissionHelper
import java.lang.NumberFormatException
import kotlinx.coroutines.*
import org.linphone.core.*
import org.linphone.core.tools.Log

interface ContactsUpdatedListener {
    fun onContactsUpdated()

    fun onContactUpdated(friend: Friend)
}

open class ContactsUpdatedListenerStub : ContactsUpdatedListener {
    override fun onContactsUpdated() {}

    override fun onContactUpdated(friend: Friend) {}
}

class ContactsManager(private val context: Context) {
    val magicSearch: MagicSearch by lazy {
        val magicSearch = coreContext.core.createMagicSearch()
        magicSearch.limitedSearch = false
        magicSearch
    }

    var latestContactFetch: String = ""

    val fetchInProgress = MutableLiveData<Boolean>()

    var contactIdToWatchFor: String = ""

    private val localFriends = arrayListOf<Friend>()

    private val contactsUpdatedListeners = ArrayList<ContactsUpdatedListener>()

    private val friendListListener: FriendListListenerStub = object : FriendListListenerStub() {
        @Synchronized
        override fun onPresenceReceived(list: FriendList, friends: Array<Friend>) {
            Log.i("[Contacts Manager] Presence received")
            for (friend in friends) {
                refreshContactOnPresenceReceived(friend)
            }
            notifyListeners()
        }
    }

    init {
        initSyncAccount()

        val core = coreContext.core
        for (list in core.friendsLists) {
            list.addListener(friendListListener)
        }
        Log.i("[Contacts Manager] Created")
    }

    fun shouldDisplaySipContactsList(): Boolean {
        return coreContext.core.defaultAccount?.params?.identityAddress?.domain == corePreferences.defaultDomain
    }

    fun fetchFinished() {
        Log.i("[Contacts Manager] Contacts loader have finished")
        latestContactFetch = System.currentTimeMillis().toString()
        updateLocalContacts()
        fetchInProgress.value = false
        notifyListeners()
    }

    @Synchronized
    fun updateLocalContacts() {
        Log.i("[Contacts Manager] Updating local contact(s)")
        localFriends.clear()

        for (account in coreContext.core.accountList) {
            val friend = coreContext.core.createFriend()
            friend.name = account.params.identityAddress?.displayName ?: account.params.identityAddress?.username

            val address = account.params.identityAddress ?: continue
            friend.address = address

            val pictureUri = corePreferences.defaultAccountAvatarPath
            if (pictureUri != null) {
                val parsedUri = if (pictureUri.startsWith("/")) "file:$pictureUri" else pictureUri
                Log.i("[Contacts Manager] Found local picture URI: $parsedUri")
                friend.photo = parsedUri
            }

            Log.i("[Contacts Manager] Local contact created for account [${address.asString()}] and picture [${friend.photo}]")
            localFriends.add(friend)
        }
    }

    @Synchronized
    fun getAndroidContactIdFromUri(uri: Uri): String? {
        val projection = arrayOf(ContactsContract.Data.CONTACT_ID)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        if (cursor?.moveToFirst() == true) {
            val nameColumnIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val id = cursor.getString(nameColumnIndex)
            cursor.close()
            return id
        }
        return null
    }

    @Synchronized
    fun findContactById(id: String): Friend? {
        return coreContext.core.defaultFriendList?.findFriendByRefKey(id)
    }

    @Synchronized
    fun findContactByPhoneNumber(number: String): Friend? {
        return coreContext.core.findFriendByPhoneNumber(number)
    }

    @Synchronized
    fun findContactByAddress(address: Address): Friend? {
        for (friend in localFriends) {
            val found = friend.addresses.find {
                it.weakEqual(address)
            }
            if (found != null) {
                return friend
            }
        }

        val friend = coreContext.core.findFriend(address)
        if (friend != null) return friend

        val username = address.username
        if (username != null && Patterns.PHONE.matcher(username).matches()) {
            return findContactByPhoneNumber(username)
        }

        return null
    }

    @Synchronized
    fun addListener(listener: ContactsUpdatedListener) {
        contactsUpdatedListeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: ContactsUpdatedListener) {
        contactsUpdatedListeners.remove(listener)
    }

    @Synchronized
    fun notifyListeners() {
        val list = contactsUpdatedListeners.toMutableList()
        for (listener in list) {
            listener.onContactsUpdated()
        }
    }

    @Synchronized
    fun notifyListeners(friend: Friend) {
        val list = contactsUpdatedListeners.toMutableList()
        for (listener in list) {
            listener.onContactUpdated(friend)
        }
    }

    @Synchronized
    fun destroy() {
        val core = coreContext.core
        for (list in core.friendsLists) list.removeListener(friendListListener)
    }

    private fun initSyncAccount() {
        val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        val accounts = accountManager.getAccountsByType(context.getString(R.string.sync_account_type))
        if (accounts.isEmpty()) {
            val newAccount = Account(
                context.getString(R.string.sync_account_name),
                context.getString(
                    R.string.sync_account_type
                )
            )
            try {
                accountManager.addAccountExplicitly(newAccount, null, null)
                Log.i("[Contacts Manager] Contact account added")
            } catch (e: Exception) {
                Log.e("[Contacts Manager] Couldn't initialize sync account: $e")
            }
        } else {
            for (account in accounts) {
                Log.i("[Contacts Manager] Found account with name [${account.name}] and type [${account.type}]")
            }
        }
    }

    fun getAvailableSyncAccounts(): List<Triple<String, String, Drawable?>> {
        val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        val packageManager = context.packageManager
        val syncAdapters = ContentResolver.getSyncAdapterTypes()
        val authenticators: Array<AuthenticatorDescription> = accountManager.authenticatorTypes
        val available = arrayListOf<Triple<String, String, Drawable?>>()

        for (syncAdapter in syncAdapters) {
            if (syncAdapter.authority == "com.android.contacts" && syncAdapter.isUserVisible) {
                if (syncAdapter.supportsUploading() || syncAdapter.accountType == context.getString(R.string.sync_account_type)) {
                    Log.i("[Contacts Manager] Found sync adapter for com.android.contacts authority: ${syncAdapter.accountType}")
                    val accounts = accountManager.getAccountsByType(syncAdapter.accountType)
                    for (account in accounts) {
                        Log.i("[Contacts Manager] Found account for account type ${syncAdapter.accountType}: ${account.name}")
                        for (authenticator in authenticators) {
                            if (authenticator.type == account.type) {
                                val drawable = packageManager.getDrawable(authenticator.packageName, authenticator.smallIconId, null)
                                val triple = Triple(account.name, account.type, drawable)
                                available.add(triple)
                            }
                        }
                    }
                }
            }
        }

        return available
    }

    @Synchronized
    private fun refreshContactOnPresenceReceived(friend: Friend) {
        Log.d("[Contacts Manager] Received presence information for contact $friend")
        if (corePreferences.storePresenceInNativeContact && PermissionHelper.get().hasWriteContactsPermission()) {
            if (friend.refKey != null) {
                storePresenceInNativeContact(friend)
            }
        }
        notifyListeners(friend)
    }

    @Synchronized
    fun storePresenceInformationForAllContacts() {
        if (corePreferences.storePresenceInNativeContact && PermissionHelper.get().hasWriteContactsPermission()) {
            for (list in coreContext.core.friendsLists) {
                for (friend in list.friends) {
                    val id = friend.refKey
                    if (id != null) {
                        storePresenceInNativeContact(friend)
                    }
                }
            }
        }
    }

    private fun storePresenceInNativeContact(friend: Friend) {
        for (phoneNumber in friend.phoneNumbers) {
            val sipAddress = friend.getContactForPhoneNumberOrAddress(phoneNumber)
            if (sipAddress != null) {
                Log.d("[Contacts Manager] Found presence information to store in native contact $friend under Linphone sync account")
                val contactEditor = NativeContactEditor(friend)
                val coroutineScope = CoroutineScope(Dispatchers.Main)
                coroutineScope.launch {
                    val deferred = async {
                        withContext(Dispatchers.IO) {
                            contactEditor.setPresenceInformation(
                                phoneNumber,
                                sipAddress
                            ).commit()
                        }
                    }
                    deferred.await()
                }
            }
        }
    }

    fun createFriendFromSearchResult(searchResult: SearchResult): Friend {
        val searchResultFriend = searchResult.friend
        if (searchResultFriend != null) return searchResultFriend

        val friend = coreContext.core.createFriend()

        val address = searchResult.address
        if (address != null) {
            friend.address = address
        }

        val number = searchResult.phoneNumber
        if (number != null) {
            friend.addPhoneNumber(number)

            if (address != null && address.username == number) {
                friend.removeAddress(address)
            }
        }

        return friend
    }
}

fun Friend.getContactForPhoneNumberOrAddress(value: String): String? {
    val presenceModel = getPresenceModelForUriOrTel(value)
    if (presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open) return presenceModel.contact
    return null
}

fun Friend.hasPresence(): Boolean {
    for (address in addresses) {
        val presenceModel = getPresenceModelForUriOrTel(address.asStringUriOnly())
        if (presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open) return true
    }
    for (number in phoneNumbers) {
        val presenceModel = getPresenceModelForUriOrTel(number)
        if (presenceModel != null && presenceModel.basicStatus == PresenceBasicStatus.Open) return true
    }
    return false
}

fun Friend.getPictureUri(): Uri? {
    val refKey = refKey
    if (refKey != null) {
        try {
            val nativeId = refKey.toLong()
            return Uri.withAppendedPath(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, nativeId),
                ContactsContract.Contacts.Photo.DISPLAY_PHOTO
            )
        } catch (nfe: NumberFormatException) {}
    }

    val photoUri = photo ?: return null
    return Uri.parse(photoUri)
}

fun Friend.getThumbnailUri(): Uri? {
    val refKey = refKey
    if (refKey != null) {
        try {
            val nativeId = refKey.toLong()
            return Uri.withAppendedPath(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, nativeId),
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
            )
        } catch (nfe: NumberFormatException) {}
    }

    val photoUri = photo ?: return null
    return Uri.parse(photoUri)
}

fun Friend.getPerson(): Person {
    val personBuilder = Person.Builder().setName(name)

    val bm: Bitmap? =
        ImageUtils.getRoundBitmapFromUri(
            coreContext.context,
            getPictureUri()
        )
    val icon =
        if (bm == null) IconCompat.createWithResource(
            coreContext.context,
            R.drawable.avatar
        ) else IconCompat.createWithAdaptiveBitmap(bm)
    if (icon != null) {
        personBuilder.setIcon(icon)
    }

    personBuilder.setImportant(starred)
    return personBuilder.build()
}
