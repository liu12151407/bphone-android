/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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

import androidx.lifecycle.MutableLiveData
import bsolution.phone.LinphoneApplication.Companion.coreContext
import bsolution.phone.activities.main.viewmodels.ErrorReportingViewModel
import bsolution.phone.utils.AppUtils
import bsolution.phone.utils.LinphoneUtils
import org.linphone.core.Address
import org.linphone.core.ChatRoomSecurityLevel
import org.linphone.core.Friend

interface ContactDataInterface {
    val contact: MutableLiveData<Friend>

    val displayName: MutableLiveData<String>

    val securityLevel: MutableLiveData<ChatRoomSecurityLevel>

    val showGroupChatAvatar: Boolean
        get() = false
}

open class GenericContactData(private val sipAddress: Address) : ContactDataInterface {
    final override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    final override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    final override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()

    val initials = MutableLiveData<String>()

    init {
        securityLevel.value = ChatRoomSecurityLevel.ClearText
        contactLookup()
    }

    open fun destroy() {
    }

    private fun contactLookup() {
        displayName.value = LinphoneUtils.getDisplayName(sipAddress)

        val c = coreContext.contactsManager.findContactByAddress(sipAddress)
        contact.value = c

        initials.value = if (c != null) {
            AppUtils.getInitials(c.name ?: "")
        } else {
            AppUtils.getInitials(displayName.value ?: "")
        }
    }
}

abstract class GenericContactViewModel(private val sipAddress: Address) :
    ErrorReportingViewModel(),
    ContactDataInterface {
    final override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    final override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    final override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()

    init {
        securityLevel.value = ChatRoomSecurityLevel.ClearText
        contactLookup()
    }

    private fun contactLookup() {
        displayName.value = LinphoneUtils.getDisplayName(sipAddress)
        contact.value = coreContext.contactsManager.findContactByAddress(sipAddress)
    }
}
