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
package bsolution.phone.activities.assistant.fragments

import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import bsolution.phone.LinphoneApplication.Companion.coreContext
import bsolution.phone.R
import bsolution.phone.activities.GenericFragment
import bsolution.phone.activities.SnackBarActivity
import bsolution.phone.activities.assistant.viewmodels.PhoneAccountValidationViewModel
import bsolution.phone.activities.assistant.viewmodels.PhoneAccountValidationViewModelFactory
import bsolution.phone.activities.assistant.viewmodels.SharedAssistantViewModel
import bsolution.phone.activities.navigateToAccountSettings
import bsolution.phone.activities.navigateToEchoCancellerCalibration
import bsolution.phone.databinding.AssistantPhoneAccountValidationFragmentBinding

class PhoneAccountValidationFragment : GenericFragment<AssistantPhoneAccountValidationFragmentBinding>() {
    private lateinit var sharedViewModel: SharedAssistantViewModel
    private lateinit var viewModel: PhoneAccountValidationViewModel

    override fun getLayoutId(): Int = R.layout.assistant_phone_account_validation_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedAssistantViewModel::class.java]
        }

        viewModel = ViewModelProvider(this, PhoneAccountValidationViewModelFactory(sharedViewModel.getAccountCreator()))[PhoneAccountValidationViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.phoneNumber.value = arguments?.getString("PhoneNumber")
        viewModel.isLogin.value = arguments?.getBoolean("IsLogin", false)
        viewModel.isCreation.value = arguments?.getBoolean("IsCreation", false)
        viewModel.isLinking.value = arguments?.getBoolean("IsLinking", false)

        viewModel.leaveAssistantEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                when {
                    viewModel.isLogin.value == true || viewModel.isCreation.value == true -> {
                        coreContext.newAccountConfigured(true)

                        if (coreContext.core.isEchoCancellerCalibrationRequired) {
                            navigateToEchoCancellerCalibration()
                        } else {
                            requireActivity().finish()
                        }
                    }
                    viewModel.isLinking.value == true -> {
                        if (findNavController().graph.id == R.id.settings_nav_graph_xml) {
                            val args = Bundle()
                            args.putString(
                                "Identity",
                                "sip:${viewModel.accountCreator.username}@${viewModel.accountCreator.domain}"
                            )
                            navigateToAccountSettings(args)
                        } else {
                            requireActivity().finish()
                        }
                    }
                }
            }
        }

        viewModel.onErrorEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { message ->
                (requireActivity() as SnackBarActivity).showSnackBar(message)
            }
        }

        val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener {
            val data = clipboard.primaryClip
            if (data != null && data.itemCount > 0) {
                val clip = data.getItemAt(0).text.toString()
                if (clip.length == 4) {
                    viewModel.code.value = clip
                }
            }
        }
    }
}
