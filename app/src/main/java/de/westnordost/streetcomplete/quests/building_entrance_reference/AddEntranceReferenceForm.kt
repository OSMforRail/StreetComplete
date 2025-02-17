package de.westnordost.streetcomplete.quests.building_entrance_reference

import android.os.Bundle
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.core.view.isInvisible
import androidx.core.widget.doAfterTextChanged
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AbstractOsmQuestForm
import de.westnordost.streetcomplete.quests.AnswerItem
import de.westnordost.streetcomplete.util.ktx.nonBlankTextOrNull
import de.westnordost.streetcomplete.util.ktx.showKeyboard

class AddEntranceReferenceForm : AbstractOsmQuestForm<EntranceAnswer>() {

    override val otherAnswers = listOf(
        AnswerItem(R.string.quest_entrance_reference_nothing_signed) { onNothingSigned() },
    )

    private var referenceCodeInput: EditText? = null
    private var flatRangeStartInput: EditText? = null
    private var flatRangeEndInput: EditText? = null
    private var selectFlatRangeAndCode: Button? = null
    private var selectFlatRange: Button? = null
    private var selectCode: Button? = null
    private var selectNothingSigned: Button? = null
    private var toggleKeyboardButton: Button? = null

    enum class InterfaceMode {
        FLAT_RANGE, ENTRANCE_REFERENCE, FLAT_RANGE_AND_ENTRANCE_REFERENCE, SELECTING
    }
    private var interfaceMode: InterfaceMode = InterfaceMode.SELECTING

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prevMode = savedInstanceState?.getString(INTERFACE_MODE)?.let { InterfaceMode.valueOf(it) }
        setInterfaceMode(prevMode ?: InterfaceMode.SELECTING)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(INTERFACE_MODE, interfaceMode.name)
    }

    /* -------------------------- Set (different) layout  --------------------------- */

    private fun setInterfaceMode(interfaceMode: InterfaceMode) {
        this.interfaceMode = interfaceMode
        val view = setContentView(interfaceMode.layout)
        onContentViewCreated(view)
    }

    private fun onContentViewCreated(view: View) {
        toggleKeyboardButton = view.findViewById(R.id.toggleKeyboardButton)
        referenceCodeInput = view.findViewById(R.id.referenceCodeInput)
        flatRangeStartInput = view.findViewById(R.id.flatRangeStartInput)
        flatRangeEndInput = view.findViewById(R.id.flatRangeEndInput)
        selectFlatRangeAndCode = view.findViewById(R.id.select_flat_range_and_code)
        selectFlatRangeAndCode?.setOnClickListener { setInterfaceMode(InterfaceMode.FLAT_RANGE_AND_ENTRANCE_REFERENCE) }
        selectFlatRange = view.findViewById(R.id.select_flat_range_only)
        selectFlatRange?.setOnClickListener { setInterfaceMode(InterfaceMode.FLAT_RANGE) }
        selectCode = view.findViewById(R.id.select_code_only)
        selectCode?.setOnClickListener { setInterfaceMode(InterfaceMode.ENTRANCE_REFERENCE) }
        selectNothingSigned = view.findViewById(R.id.nothing_signed)
        selectNothingSigned?.setOnClickListener { onNothingSigned() }

        initKeyboardButton()

        if (flatRangeStartInput != null) {
            referenceCodeInput?.imeOptions = EditorInfo.IME_ACTION_NEXT
        }

        listOfNotNull(
            referenceCodeInput, flatRangeStartInput, flatRangeEndInput,
        ).forEach { it.doAfterTextChanged { checkIsFormComplete() } }

        checkIsFormComplete()
    }

    /* ------------------------------------- Other answers -------------------------------------- */

    private fun onNothingSigned() {
        applyAnswer(Unsigned)
    }

    /* ------------------------------------- Change mode ---------------------------------------- */

    private fun initKeyboardButton() {
        toggleKeyboardButton?.text = "abc"
        toggleKeyboardButton?.setOnClickListener {
            val focus = requireActivity().currentFocus
            if (focus != null && focus is EditText) {
                val start = focus.selectionStart
                val end = focus.selectionEnd
                if (focus.inputType and InputType.TYPE_CLASS_NUMBER != 0) {
                    focus.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    toggleKeyboardButton?.text = "123"
                } else {
                    focus.inputType = InputType.TYPE_CLASS_NUMBER
                    focus.keyListener = DigitsKeyListener.getInstance("0123456789.,- /")
                    toggleKeyboardButton?.text = "abc"
                }
                // for some reason, the cursor position gets lost first time the input type is set (#1093)
                focus.setSelection(start, end)
                focus.showKeyboard()
            }
        }
        updateKeyboardButtonVisibility()

        val onFocusChange = View.OnFocusChangeListener { v, hasFocus ->
            updateKeyboardButtonVisibility()
            if (hasFocus) v.showKeyboard()
        }
        referenceCodeInput?.onFocusChangeListener = onFocusChange
        flatRangeStartInput?.onFocusChangeListener = onFocusChange
        flatRangeEndInput?.onFocusChangeListener = onFocusChange
    }

    private fun updateKeyboardButtonVisibility() {
        toggleKeyboardButton?.isInvisible = !(
            referenceCodeInput?.hasFocus() == true
                || flatRangeStartInput?.hasFocus() == true
                || flatRangeEndInput?.hasFocus() == true
            )
    }

    /* ----------------------------------- Commit answer ---------------------------------------- */

    override fun onClickOk() {
        val answer = createAnswer()
        applyAnswer(answer)
    }

    override fun isFormComplete(): Boolean {
        val referenceCode = referenceCodeInput?.nonBlankTextOrNull
        val flatRangeStart = flatRangeStartInput?.nonBlankTextOrNull
        val flatRangeEnd = flatRangeEndInput?.nonBlankTextOrNull

        return when (interfaceMode) {
            InterfaceMode.FLAT_RANGE -> flatRangeStart != null && flatRangeEnd != null
            InterfaceMode.ENTRANCE_REFERENCE -> referenceCode != null
            InterfaceMode.FLAT_RANGE_AND_ENTRANCE_REFERENCE -> flatRangeStart != null && flatRangeEnd != null && referenceCode != null
            InterfaceMode.SELECTING -> false
        }
    }

    override fun isRejectingClose(): Boolean =
        referenceCodeInput?.nonBlankTextOrNull != null
            || flatRangeStartInput?.nonBlankTextOrNull != null
            || flatRangeEndInput?.nonBlankTextOrNull != null

    private fun createAnswer(): EntranceAnswer {
        val referenceCode = referenceCodeInput?.nonBlankTextOrNull
        val flatRangeStart = flatRangeStartInput?.nonBlankTextOrNull
        val flatRangeEnd = flatRangeEndInput?.nonBlankTextOrNull
        val flatRange = when {
            flatRangeStart == null || flatRangeEnd == null -> null
            flatRangeStart == flatRangeEnd -> flatRangeStart
            else -> "$flatRangeStart-$flatRangeEnd"
        }

        return when {
            referenceCode != null && flatRange != null -> ReferenceCodeAndFlatRange(referenceCode, flatRange)
            referenceCode != null                      -> ReferenceCode(referenceCode)
            flatRange != null                          -> FlatRange(flatRange)
            else                                       -> throw UnsupportedOperationException()
        }
    }

    private val InterfaceMode.layout get() = when (this) {
        InterfaceMode.FLAT_RANGE -> R.layout.quest_entrance_flat_range
        InterfaceMode.ENTRANCE_REFERENCE -> R.layout.quest_entrance_reference
        InterfaceMode.FLAT_RANGE_AND_ENTRANCE_REFERENCE -> R.layout.quest_entrance_reference_range_and_reference_input
        InterfaceMode.SELECTING -> R.layout.quest_entrance_reference_mode_selection
    }

    companion object {
        private const val INTERFACE_MODE = "interface_mode"
    }
}
