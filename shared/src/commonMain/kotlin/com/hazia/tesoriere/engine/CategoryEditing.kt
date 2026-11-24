package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.ProfileData

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/CategoryEditing.kt at
 * 2892110. Changed from Sci-Fin: the draft has no spent figure, since spent is worked out per
 * month and changed by an adjustment (SPEC R25, R59); deleting a category waits for SPEC R42's
 * links and the Trash.
 */
object CategoryEditing {

    /** The category editor: name, icon and planned amount (SPEC R59). */
    data class Draft(val id: String? = null, val name: String = "", val icon: String? = null, val planned: Money = 0) {
        companion object { fun of(c: Category) = Draft(c.id, c.name, c.icon, c.planned) }
    }

    /** Saves a new category or an edit; neither logs a row nor moves money. A blank name saves nothing. */
    fun save(d: ProfileData, draft: Draft, @Suppress("UNUSED_PARAMETER") clock: Clock, ids: () -> String): ProfileData {
        val name = draft.name.trim(); if (name.isEmpty()) return d
        return if (draft.id == null) {
            d.copy(categories = d.categories + Category(ids(), d.profileId, name, draft.icon, planned = draft.planned))
        } else {
            if (d.categories.none { it.id == draft.id }) return d
            d.copy(categories = d.categories.map { if (it.id == draft.id) it.copy(name = name, icon = draft.icon, planned = draft.planned) else it })
        }
    }
}
