package com.hazia.tesoriere.data

import androidx.room.migration.Migration

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/data/Migrations.kt at 2892110.
 *
 * SPEC R142: every schema version the app has written has a path forward, each migration written
 * against the exported schema in `shared/schemas/`; SchemaGuardTest fails the build when a version
 * arrives without one.
 *
 * Changed from Sci-Fin: none of Sci-Fin's ten versions is Tesoriere's. Version 1 is changed in
 * place until the first install on the owner's device (SPEC R142), so there is no migration yet.
 */
val TESORIERE_MIGRATIONS: Array<Migration> = arrayOf()
