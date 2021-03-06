package org.stepic.droid.persistence.downloads.resolvers.structure

import io.reactivex.Observable
import org.stepic.droid.persistence.model.Structure
import org.stepik.android.model.Unit

interface UnitStructureResolver: StructureResolver<Unit> {
    fun resolveStructure(
        courseId: Long,
        sectionId: Long,
        vararg unitIds: Long,
        resolveNestedObjects: Boolean
    ): Observable<Structure>
}