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
package bsolution.phone.activities.main.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import bsolution.phone.R
import bsolution.phone.activities.GenericFragment
import bsolution.phone.activities.main.viewmodels.ListTopBarViewModel
import bsolution.phone.databinding.ListEditTopBarFragmentBinding
import bsolution.phone.utils.Event

class ListTopBarFragment : GenericFragment<ListEditTopBarFragmentBinding>() {
    private lateinit var viewModel: ListTopBarViewModel

    override fun getLayoutId(): Int = R.layout.list_edit_top_bar_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        useMaterialSharedAxisXForwardAnimation = false

        viewModel = ViewModelProvider(parentFragment ?: this)[ListTopBarViewModel::class.java]
        binding.viewModel = viewModel

        binding.setCancelClickListener {
            viewModel.isEditionEnabled.value = false
        }

        binding.setSelectAllClickListener {
            viewModel.selectAllEvent.value = Event(true)
        }

        binding.setUnSelectAllClickListener {
            viewModel.unSelectAllEvent.value = Event(true)
        }

        binding.setDeleteClickListener {
            viewModel.deleteSelectionEvent.value = Event(true)
        }

        onBackPressedCallback.isEnabled = false
    }
}
