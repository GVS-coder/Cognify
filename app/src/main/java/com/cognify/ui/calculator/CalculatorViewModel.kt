package com.cognify.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.CalculatorHistoryEntity
import com.cognify.data.repository.CalculatorRepository
import com.cognify.data.repository.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import java.util.Locale
import javax.inject.Inject

enum class CalculatorMode { NORMAL, SCIENTIFIC, CURRENCY }
enum class AngleMode { DEG, RAD }

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val calculatorRepository: CalculatorRepository,
    private val currencyRepository: CurrencyRepository
) : ViewModel() {

    private val _currentMode = MutableStateFlow(CalculatorMode.NORMAL)
    val currentMode = _currentMode.asStateFlow()

    private val _angleMode = MutableStateFlow(AngleMode.DEG)
    val angleMode = _angleMode.asStateFlow()

    private val _expression = MutableStateFlow("")
    val expression = _expression.asStateFlow()

    private val _result = MutableStateFlow("0")
    val result = _result.asStateFlow()

    // Currency states
    val availableCurrencies = currencyRepository.getAvailableCurrencies()
    
    private val _fromCurrency = MutableStateFlow("USD")
    val fromCurrency = _fromCurrency.asStateFlow()

    private val _toCurrency = MutableStateFlow("EUR")
    val toCurrency = _toCurrency.asStateFlow()

    private val _currencyAmount = MutableStateFlow("0")
    val currencyAmount = _currencyAmount.asStateFlow()

    private val _currencyResult = MutableStateFlow("0.00")
    val currencyResult = _currencyResult.asStateFlow()

    private val _isCurrencyLoading = MutableStateFlow(false)
    val isCurrencyLoading = _isCurrencyLoading.asStateFlow()

    private val _currencyRateInfo = MutableStateFlow("")
    val currencyRateInfo = _currencyRateInfo.asStateFlow()

    private val _currencyRateDate = MutableStateFlow("")
    val currencyRateDate = _currencyRateDate.asStateFlow()

    private val _currencyErrorMessage = MutableStateFlow<String?>(null)
    val currencyErrorMessage = _currencyErrorMessage.asStateFlow()

    val history: StateFlow<List<CalculatorHistoryEntity>> = calculatorRepository.getHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Fetch initial rate
        updateCurrencyConversion()
    }

    fun setMode(mode: CalculatorMode) {
        _currentMode.value = mode
        if (mode == CalculatorMode.CURRENCY && _currencyRateInfo.value.isEmpty()) {
            updateCurrencyConversion()
        }
    }

    fun toggleAngleMode() {
        _angleMode.value = if (_angleMode.value == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG
        evaluateExpression()
    }

    fun appendExpression(value: String) {
        _expression.value += value
        evaluateExpression()
    }

    fun deleteLast() {
        if (_expression.value.isNotEmpty()) {
            _expression.value = _expression.value.dropLast(1)
            evaluateExpression()
        }
    }

    fun clear() {
        _expression.value = ""
        _result.value = "0"
    }

    private fun evaluateExpression() {
        if (_expression.value.isBlank()) {
            _result.value = "0"
            return
        }
        
        try {
            var exprStr = _expression.value
                .replace("×", "*")
                .replace("÷", "/")
                .replace("%", "/100")
                .replace("√", "sqrt")
            
            // Handle degrees by converting angles for trig functions
            if (_angleMode.value == AngleMode.DEG) {
                exprStr = exprStr.replace("sin(", "sin((pi/180)*")
                exprStr = exprStr.replace("cos(", "cos((pi/180)*")
                exprStr = exprStr.replace("tan(", "tan((pi/180)*")
            }

            val e = ExpressionBuilder(exprStr).build()
            val res = e.evaluate()
            
            if (res.isNaN() || res.isInfinite()) {
                _result.value = "Error"
            } else {
                _result.value = if (res % 1.0 == 0.0) {
                    res.toLong().toString()
                } else {
                    String.format(Locale.getDefault(), "%.8f", res).trimEnd('0').trimEnd('.')
                }
            }
        } catch (e: Exception) {
            // Do not update result if expression is currently invalid (e.g., "5+")
            // Wait until it's valid
        }
    }

    fun calculateResult() {
        evaluateExpression()
        if (_expression.value.isNotEmpty() && _result.value != "Error" && _result.value != "0") {
            viewModelScope.launch {
                calculatorRepository.addHistory(_expression.value, _result.value)
            }
            _expression.value = _result.value
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            calculatorRepository.clearHistory()
        }
    }

    // Currency operations
    fun setFromCurrency(currency: String) {
        _fromCurrency.value = currency
        updateCurrencyConversion()
    }

    fun setToCurrency(currency: String) {
        _toCurrency.value = currency
        updateCurrencyConversion()
    }

    fun swapCurrencies() {
        val temp = _fromCurrency.value
        _fromCurrency.value = _toCurrency.value
        _toCurrency.value = temp
        updateCurrencyConversion()
    }

    fun appendCurrencyAmount(value: String) {
        if (_currencyAmount.value == "0" && value != ".") {
            _currencyAmount.value = value
        } else {
            _currencyAmount.value += value
        }
        updateCurrencyConversion(onlyRecalculateAmount = true)
    }

    fun deleteCurrencyLast() {
        if (_currencyAmount.value.length > 1) {
            _currencyAmount.value = _currencyAmount.value.dropLast(1)
        } else {
            _currencyAmount.value = "0"
        }
        updateCurrencyConversion(onlyRecalculateAmount = true)
    }

    fun clearCurrency() {
        _currencyAmount.value = "0"
        _currencyResult.value = "0.00"
    }
    
    fun refreshCurrencyRate() {
        updateCurrencyConversion(forceRefresh = true)
    }

    private var currentRate: Double = 0.0

    private fun updateCurrencyConversion(forceRefresh: Boolean = false, onlyRecalculateAmount: Boolean = false) {
        val amount = _currencyAmount.value.toDoubleOrNull() ?: 0.0
        
        if (onlyRecalculateAmount && currentRate > 0) {
            _currencyResult.value = String.format(Locale.getDefault(), "%.2f", amount * currentRate)
            return
        }

        viewModelScope.launch {
            _isCurrencyLoading.value = true
            _currencyErrorMessage.value = null
            
            val from = _fromCurrency.value
            val to = _toCurrency.value
            
            val result = currencyRepository.getLiveRate(from, to, forceRefresh)
            
            result.onSuccess { (rate, date) ->
                currentRate = rate
                _currencyResult.value = String.format(Locale.getDefault(), "%.2f", amount * rate)
                _currencyRateInfo.value = "1 $from = ${String.format(Locale.getDefault(), "%.4f", rate)} $to"
                _currencyRateDate.value = "Rate updated: $date"
            }.onFailure {
                _currencyErrorMessage.value = "Unable to fetch the latest exchange rate."
                if (currentRate > 0) {
                    // Fallback to existing memory rate if available
                    _currencyResult.value = String.format(Locale.getDefault(), "%.2f", amount * currentRate)
                } else {
                    _currencyResult.value = "---"
                    _currencyRateInfo.value = ""
                    _currencyRateDate.value = ""
                }
            }
            
            _isCurrencyLoading.value = false
        }
    }
}
