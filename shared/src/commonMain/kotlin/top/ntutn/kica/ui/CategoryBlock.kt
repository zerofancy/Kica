package top.ntutn.kica.ui

import top.ntutn.kica.model.ComicCategory
import top.ntutn.kica.model.ComicSummary

fun List<ComicSummary>.filterBlockedSummaries(blocked: Set<String>): List<ComicSummary> =
    if (blocked.isEmpty()) this
    else filter { it.categories.none { category -> category in blocked } }

fun List<ComicCategory>.filterBlockedCategories(blocked: Set<String>): List<ComicCategory> =
    if (blocked.isEmpty()) this
    else filter { it.title !in blocked }
