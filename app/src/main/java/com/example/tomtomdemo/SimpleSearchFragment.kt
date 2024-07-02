package com.example.tomtomdemo

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import com.tomtom.sdk.search.online.OnlineSearch
import com.tomtom.sdk.search.ui.SearchFragment
import com.tomtom.sdk.search.ui.SearchFragmentListener
import com.tomtom.sdk.search.ui.model.PlaceDetails
import com.tomtom.sdk.search.ui.model.SearchApiParameters
import com.tomtom.sdk.search.ui.model.SearchProperties

/**
 * Created by Chen Wei on 2024/7/1.
 */
class SimpleSearchFragment : SearchFragment() {
    companion object {
        private const val LIMIT_DEFAULT = 5

        @JvmStatic
        fun newInstance(limitCount: Int = LIMIT_DEFAULT): SimpleSearchFragment {
            return SimpleSearchFragment().apply {
                arguments = bundleOf(
                    SEARCH_PROPERTIES_BUNDLE_KEY to SearchProperties(
                        searchApiKey = BuildConfig.TOMTOM_API_KEY,
                        searchApiParameters = SearchApiParameters(limit = limitCount),
                    ),
                )
            }
        }
    }

    private val simpleViewModel by activityViewModels<SimpleViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.let {
            setSearchApi(OnlineSearch.create(it, BuildConfig.TOMTOM_API_KEY))
            enableSearchBackButton(false)
            setFragmentListener(object : SearchFragmentListener {
                override fun onSearchBackButtonClick() {
                }

                override fun onSearchResultClick(placeDetails: PlaceDetails) {
                    simpleViewModel.planRouteData.value = placeDetails
                }

                override fun onSearchError(throwable: Throwable) {
                }

                override fun onSearchQueryChanged(input: String) {
                }

                override fun onCommandInsert(command: String) {
                }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setFragmentListener(null)
    }
}