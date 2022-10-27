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

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import bsolution.phone.LinphoneApplication.Companion.coreContext
import bsolution.phone.LinphoneApplication.Companion.corePreferences
import bsolution.phone.R
import bsolution.phone.activities.GenericFragment
import bsolution.phone.activities.assistant.AssistantActivity
import bsolution.phone.activities.assistant.viewmodels.GenericLoginViewModel
import bsolution.phone.activities.assistant.viewmodels.GenericLoginViewModelFactory
import bsolution.phone.activities.assistant.viewmodels.SharedAssistantViewModel
import bsolution.phone.activities.main.viewmodels.DialogViewModel
import bsolution.phone.activities.navigateToEchoCancellerCalibration
import bsolution.phone.databinding.AssistantGenericAccountLoginFragmentBinding
import bsolution.phone.utils.DialogUtils

class GenericAccountLoginFragment : GenericFragment<AssistantGenericAccountLoginFragmentBinding>() {
    private lateinit var sharedViewModel: SharedAssistantViewModel
    private lateinit var viewModel: GenericLoginViewModel

    override fun getLayoutId(): Int = R.layout.assistant_generic_account_login_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedAssistantViewModel::class.java]
        }

        viewModel = ViewModelProvider(this, GenericLoginViewModelFactory(sharedViewModel.getAccountCreator(true)))[GenericLoginViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.leaveAssistantEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val isLinphoneAccount = viewModel.domain.value.orEmpty() == corePreferences.defaultDomain
                coreContext.newAccountConfigured(isLinphoneAccount)

                if (coreContext.core.isEchoCancellerCalibrationRequired) {
                    navigateToEchoCancellerCalibration()
                } else {
                    requireActivity().finish()
                }
            }
        }

        viewModel.invalidCredentialsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val dialogViewModel =
                    DialogViewModel(getString(R.string.assistant_error_invalid_credentials))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

                dialogViewModel.showCancelButton {
                    viewModel.removeInvalidProxyConfig()
                    dialog.dismiss()
                }

                dialogViewModel.showDeleteButton(
                    {
                        viewModel.continueEvenIfInvalidCredentials()
                        dialog.dismiss()
                    },
                    getString(R.string.assistant_continue_even_if_credentials_invalid)
                )

                dialog.show()
            }
        }

        viewModel.onErrorEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { message ->
                (requireActivity() as AssistantActivity).showSnackBar(message)
            }
        }
    }
}
