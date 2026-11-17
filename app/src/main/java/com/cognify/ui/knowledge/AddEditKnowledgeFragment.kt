package com.cognify.ui.knowledge

import com.cognify.R
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
import com.cognify.data.model.Difficulty
import com.cognify.data.model.KnowledgeType
import com.cognify.data.model.ProgrammingLanguage
import com.cognify.databinding.FragmentAddEditKnowledgeBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddEditKnowledgeFragment : Fragment() {

    private var _binding: FragmentAddEditKnowledgeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditKnowledgeViewModel by viewModels()

    private var selectedType: KnowledgeType = KnowledgeType.NOTE
    private var selectedLanguage: ProgrammingLanguage = ProgrammingLanguage.NONE
    private var selectedDifficulty: Difficulty = Difficulty.NONE

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditKnowledgeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        updateFormVisibility(selectedType)
        observeExistingItem()
        observeEvents()

        binding.toolbar.setNavigationOnClickListener {
            if (!findNavController().navigateUp()) {
                if (!findNavController().popBackStack()) {
                    try { findNavController().navigate(com.cognify.R.id.dashboardFragment) } catch (e: Exception) {}
                }
            }
        }

        binding.fabSave.setOnClickListener { saveKnowledge() }
    }

    private fun getKnowledgeTypeString(type: KnowledgeType): String {
        return when (type) {
            KnowledgeType.NOTE -> getString(com.cognify.R.string.ui_knowledge_type_note)
            KnowledgeType.CODE -> getString(com.cognify.R.string.ui_knowledge_type_code)
            KnowledgeType.SQL -> getString(com.cognify.R.string.ui_knowledge_type_sql)
            KnowledgeType.LINUX -> getString(com.cognify.R.string.ui_knowledge_type_linux)
            KnowledgeType.INTERVIEW -> getString(com.cognify.R.string.ui_knowledge_type_interview)
            KnowledgeType.JAPANESE -> getString(com.cognify.R.string.ui_knowledge_type_japanese)
            KnowledgeType.COLLEGE -> getString(com.cognify.R.string.ui_knowledge_type_college)
            KnowledgeType.DOCUMENTATION -> getString(com.cognify.R.string.ui_knowledge_type_documentation)
        }
    }

    private fun setupDropdowns() {
        // Type dropdown
        val types = KnowledgeType.values().map { getKnowledgeTypeString(it) } + getString(com.cognify.R.string.ui_others)
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        binding.acvType.setAdapter(typeAdapter)
        binding.acvType.setText(getKnowledgeTypeString(KnowledgeType.NOTE), false)
        binding.acvType.setOnItemClickListener { _, _, pos, _ ->
            if (pos == KnowledgeType.values().size) { // "Others" is the last item
                binding.tilCustomType.visibility = View.VISIBLE
                updateFormVisibility(selectedType)
            } else {
                binding.tilCustomType.visibility = View.GONE
                selectedType = KnowledgeType.values()[pos]
                updateFormVisibility(selectedType)
            }
        }

        // Language dropdown
        val languages = ProgrammingLanguage.values().map { it.displayName } + "Others"
        val langAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, languages)
        binding.acvLanguage.setAdapter(langAdapter)
        binding.acvLanguage.setText(ProgrammingLanguage.NONE.displayName, false)
        binding.acvLanguage.setOnItemClickListener { _, _, pos, _ ->
            if (languages[pos] == "Others") {
                // If custom language, keep default NONE or map custom
                selectedLanguage = ProgrammingLanguage.NONE
            } else {
                selectedLanguage = ProgrammingLanguage.values()[pos]
            }
        }

        // Difficulty dropdown
        val difficulties = Difficulty.values().map { it.name }
        val diffAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, difficulties)
        binding.acvDifficulty.setAdapter(diffAdapter)
        binding.acvDifficulty.setText(Difficulty.NONE.name, false)
        binding.acvDifficulty.setOnItemClickListener { _, _, pos, _ ->
            selectedDifficulty = Difficulty.values()[pos]
        }
    }

    private var isAuthenticated = false

    private fun observeExistingItem() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.existingItem.collect { item ->
                    if (item != null) {
                        if (item.isPrivate && !isAuthenticated) {
                            com.cognify.util.BiometricAuthManager.authenticate(
                                fragment = this@AddEditKnowledgeFragment,
                                title = getString(R.string.ui_unlock_private_note),
                                onSuccess = {
                                    isAuthenticated = true
                                    populateKnowledgeDetails(item)
                                },
                                onError = { msg ->
                                    android.widget.Toast.makeText(requireContext(), getString(R.string.auth_failed, msg), android.widget.Toast.LENGTH_SHORT).show()
                                    findNavController().navigateUp()
                                }
                            )
                        } else {
                            populateKnowledgeDetails(item)
                        }
                    }
                }
            }
        }
    }

    private fun populateKnowledgeDetails(item: com.cognify.data.local.KnowledgeEntity) {
        binding.toolbar.title = getString(R.string.ui_edit_entry)
        binding.etTitle.setText(item.title)
        binding.etDescription.setText(item.description)
        binding.etCodeBlock.setText(item.codeBlock ?: "")
        binding.etCategory.setText(item.category)
        binding.etTags.setText(item.tags.joinToString(", "))
        binding.etSource.setText(item.source ?: "")
        binding.switchFavorite.isChecked = item.isFavorite
        binding.switchPinned.isChecked = item.pinned
        binding.switchPrivate.isChecked = item.isPrivate

        selectedType = item.type
        selectedLanguage = item.language
        selectedDifficulty = item.difficulty

        binding.acvType.setText(getKnowledgeTypeString(item.type), false)
        binding.acvLanguage.setText(item.language.displayName, false)
        binding.acvDifficulty.setText(item.difficulty.name, false)
        
        updateFormVisibility(selectedType)
    }

    private fun updateFormVisibility(type: KnowledgeType) {
        val allowPrivacy = true

        if (allowPrivacy) {
            binding.switchPrivate.visibility = View.VISIBLE
        } else {
            binding.switchPrivate.visibility = View.GONE
            binding.switchPrivate.isChecked = false
        }

        when (type) {
            KnowledgeType.CODE, KnowledgeType.SQL, KnowledgeType.LINUX -> {
                binding.tilCodeBlock.visibility = View.VISIBLE
                binding.tilLanguage.visibility = View.VISIBLE
                binding.tilCategory.visibility = View.GONE
                binding.tilDifficulty.visibility = View.GONE
            }
            KnowledgeType.INTERVIEW -> {
                binding.tilCodeBlock.visibility = View.GONE
                binding.tilLanguage.visibility = View.GONE
                binding.tilCategory.visibility = View.VISIBLE
                binding.tilDifficulty.visibility = View.VISIBLE
            }
            else -> { // NOTE, JAPANESE, COLLEGE, DOCUMENTATION, Others
                binding.tilCodeBlock.visibility = View.GONE
                binding.tilLanguage.visibility = View.GONE
                binding.tilCategory.visibility = View.VISIBLE
                binding.tilDifficulty.visibility = View.GONE
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AddEditKnowledgeEvent.SaveSuccess -> findNavController().navigateUp()
                        is AddEditKnowledgeEvent.Error ->
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun saveKnowledge() {
        val tags = binding.etTags.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        viewModel.save(
            title = binding.etTitle.text.toString(),
            description = binding.etDescription.text.toString(),
            type = selectedType,
            tags = tags,
            language = selectedLanguage,
            difficulty = selectedDifficulty,
            category = binding.etCategory.text.toString(),
            codeBlock = binding.etCodeBlock.text.toString(),
            source = binding.etSource.text.toString(),
            isFavorite = binding.switchFavorite.isChecked,
            pinned = binding.switchPinned.isChecked,
            isPrivate = binding.switchPrivate.isChecked
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
