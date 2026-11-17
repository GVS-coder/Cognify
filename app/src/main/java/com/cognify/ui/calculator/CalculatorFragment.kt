package com.cognify.ui.calculator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cognify.databinding.DialogCalculatorHistoryBinding
import com.cognify.databinding.FragmentCalculatorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CalculatorFragment : Fragment() {

    private var _binding: FragmentCalculatorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalculatorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalculatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupModeToggle()
        setupNormalKeypad()
        setupScientificKeypad()
        setupCurrencyKeypad()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(com.cognify.R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                com.cognify.R.id.action_history -> {
                    showHistoryDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupModeToggle() {
        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    com.cognify.R.id.btnModeNormal -> viewModel.setMode(CalculatorMode.NORMAL)
                    com.cognify.R.id.btnModeScientific -> viewModel.setMode(CalculatorMode.SCIENTIFIC)
                    com.cognify.R.id.btnModeCurrency -> viewModel.setMode(CalculatorMode.CURRENCY)
                }
            }
        }
        
        binding.tvAngleMode.setOnClickListener {
            viewModel.toggleAngleMode()
        }
    }

    private fun setupNormalKeypad() {
        val numberButtons = mapOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2",
            binding.btn3 to "3", binding.btn4 to "4", binding.btn5 to "5",
            binding.btn6 to "6", binding.btn7 to "7", binding.btn8 to "8",
            binding.btn9 to "9", binding.btnDot to "."
        )

        numberButtons.forEach { (btn, valStr) ->
            btn.setOnClickListener { viewModel.appendExpression(valStr) }
        }

        binding.btnPlus.setOnClickListener { viewModel.appendExpression("+") }
        binding.btnMinus.setOnClickListener { viewModel.appendExpression("-") }
        binding.btnMultiply.setOnClickListener { viewModel.appendExpression("×") }
        binding.btnDivide.setOnClickListener { viewModel.appendExpression("÷") }
        binding.btnPercent.setOnClickListener { viewModel.appendExpression("%") }

        binding.btnAC.setOnClickListener { viewModel.clear() }
        binding.btnDel.setOnClickListener { viewModel.deleteLast() }
        binding.btnEquals.setOnClickListener { viewModel.calculateResult() }
    }

    private fun setupScientificKeypad() {
        val numberButtons = mapOf(
            binding.btnSci0 to "0", binding.btnSci1 to "1", binding.btnSci2 to "2",
            binding.btnSci3 to "3", binding.btnSci4 to "4", binding.btnSci5 to "5",
            binding.btnSci6 to "6", binding.btnSci7 to "7", binding.btnSci8 to "8",
            binding.btnSci9 to "9", binding.btnSciDot to "."
        )

        numberButtons.forEach { (btn, valStr) ->
            btn.setOnClickListener { viewModel.appendExpression(valStr) }
        }

        binding.btnSciPlus.setOnClickListener { viewModel.appendExpression("+") }
        binding.btnSciMinus.setOnClickListener { viewModel.appendExpression("-") }
        binding.btnSciMultiply.setOnClickListener { viewModel.appendExpression("×") }
        binding.btnSciDivide.setOnClickListener { viewModel.appendExpression("÷") }
        binding.btnSciPercent.setOnClickListener { viewModel.appendExpression("%") }

        // Scientific functions
        binding.btnSin.setOnClickListener { viewModel.appendExpression("sin(") }
        binding.btnCos.setOnClickListener { viewModel.appendExpression("cos(") }
        binding.btnTan.setOnClickListener { viewModel.appendExpression("tan(") }
        binding.btnLn.setOnClickListener { viewModel.appendExpression("log(") } // exp4j log is base e
        binding.btnLog.setOnClickListener { viewModel.appendExpression("log10(") }
        binding.btnSqrt.setOnClickListener { viewModel.appendExpression("√(") }
        binding.btnPow.setOnClickListener { viewModel.appendExpression("^") }
        binding.btnPi.setOnClickListener { viewModel.appendExpression("pi") }
        binding.btnE.setOnClickListener { viewModel.appendExpression("e") }
        binding.btnLeftParen.setOnClickListener { viewModel.appendExpression("(") }
        binding.btnRightParen.setOnClickListener { viewModel.appendExpression(")") }

        binding.btnSciAC.setOnClickListener { viewModel.clear() }
        binding.btnSciDel.setOnClickListener { viewModel.deleteLast() }
        binding.btnSciEquals.setOnClickListener { viewModel.calculateResult() }
    }

    private fun setupCurrencyKeypad() {
        val numberButtons = mapOf(
            binding.btnCurr0 to "0", binding.btnCurr1 to "1", binding.btnCurr2 to "2",
            binding.btnCurr3 to "3", binding.btnCurr4 to "4", binding.btnCurr5 to "5",
            binding.btnCurr6 to "6", binding.btnCurr7 to "7", binding.btnCurr8 to "8",
            binding.btnCurr9 to "9", binding.btnCurrDot to "."
        )

        numberButtons.forEach { (btn, valStr) ->
            btn.setOnClickListener { viewModel.appendCurrencyAmount(valStr) }
        }

        binding.btnCurrAC.setOnClickListener { viewModel.clearCurrency() }
        binding.btnCurrDel.setOnClickListener { viewModel.deleteCurrencyLast() }

        binding.btnFromCurrency.setOnClickListener {
            showCurrencySelectorDialog { selected -> viewModel.setFromCurrency(selected) }
        }
        binding.btnToCurrency.setOnClickListener {
            showCurrencySelectorDialog { selected -> viewModel.setToCurrency(selected) }
        }
        binding.btnSwapCurrencies.setOnClickListener {
            viewModel.swapCurrencies()
        }
        binding.btnRefreshCurrency.setOnClickListener {
            viewModel.refreshCurrencyRate()
        }
    }

    private fun showCurrencySelectorDialog(onCurrencySelected: (String) -> Unit) {
        val currencies = viewModel.availableCurrencies.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Currency")
            .setItems(currencies) { _, which ->
                onCurrencySelected(currencies[which])
            }
            .show()
    }

    private fun showHistoryDialog() {
        val dialogBinding = DialogCalculatorHistoryBinding.inflate(layoutInflater)
        
        val adapter = CalculatorHistoryAdapter { item ->
            // Optionally, handle item click (e.g., restore expression)
            // viewModel.appendExpression(item.expression)
        }
        
        dialogBinding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvHistory.adapter = adapter
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Calculation History")
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .show()

        dialogBinding.btnClearHistory.setOnClickListener {
            viewModel.clearHistory()
            dialog.dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.history.collect { historyList ->
                    adapter.submitList(historyList)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentMode.collect { mode ->
                        when (mode) {
                            CalculatorMode.NORMAL -> {
                                binding.viewFlipperMain.displayedChild = 0
                                binding.viewFlipperKeypad.displayedChild = 0
                                binding.layoutAngleMode.visibility = View.GONE
                                binding.toolbar.menu.findItem(com.cognify.R.id.action_history)?.isVisible = true
                            }
                            CalculatorMode.SCIENTIFIC -> {
                                binding.viewFlipperMain.displayedChild = 0
                                binding.viewFlipperKeypad.displayedChild = 1
                                binding.layoutAngleMode.visibility = View.VISIBLE
                                binding.toolbar.menu.findItem(com.cognify.R.id.action_history)?.isVisible = true
                            }
                            CalculatorMode.CURRENCY -> {
                                binding.viewFlipperMain.displayedChild = 1
                                binding.toolbar.menu.findItem(com.cognify.R.id.action_history)?.isVisible = false
                            }
                        }
                    }
                }

                launch {
                    viewModel.angleMode.collect { angleMode ->
                        binding.tvAngleMode.text = angleMode.name
                    }
                }

                launch {
                    viewModel.expression.collect { expr ->
                        binding.tvExpression.text = expr
                    }
                }

                launch {
                    viewModel.result.collect { res ->
                        binding.tvResult.text = res
                    }
                }

                launch {
                    viewModel.fromCurrency.collect { curr ->
                        binding.btnFromCurrency.text = curr
                    }
                }

                launch {
                    viewModel.toCurrency.collect { curr ->
                        binding.btnToCurrency.text = curr
                    }
                }

                launch {
                    viewModel.currencyAmount.collect { amount ->
                        binding.tvCurrencyAmount.text = amount
                    }
                }

                launch {
                    viewModel.currencyResult.collect { result ->
                        binding.tvCurrencyResult.text = result
                    }
                }

                launch {
                    viewModel.isCurrencyLoading.collect { isLoading ->
                        binding.progressCurrency.visibility = if (isLoading) View.VISIBLE else View.GONE
                        binding.btnRefreshCurrency.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
                    }
                }

                launch {
                    viewModel.currencyRateInfo.collect { info ->
                        binding.tvCurrencyRateInfo.text = info
                        binding.tvCurrencyRateInfo.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE
                    }
                }

                launch {
                    viewModel.currencyRateDate.collect { date ->
                        binding.tvCurrencyRateDate.text = date
                        binding.tvCurrencyRateDate.visibility = if (date.isEmpty()) View.GONE else View.VISIBLE
                    }
                }

                launch {
                    viewModel.currencyErrorMessage.collect { error ->
                        if (error != null) {
                            binding.tvCurrencyError.text = error
                            binding.tvCurrencyError.visibility = View.VISIBLE
                        } else {
                            binding.tvCurrencyError.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
