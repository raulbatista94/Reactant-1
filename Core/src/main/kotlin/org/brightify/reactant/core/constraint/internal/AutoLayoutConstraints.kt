package org.brightify.reactant.core.constraint.internal

import org.brightify.reactant.core.constraint.AutoLayout
import org.brightify.reactant.core.constraint.Constraint
import org.brightify.reactant.core.constraint.ConstraintVariable
import org.brightify.reactant.core.util.onChange

/**
 *  @author <a href="mailto:filip.dolnik.96@gmail.com">Filip Dolnik</a>
 */
internal class AutoLayoutConstraints(autoLayout: AutoLayout) {

    var isActive: Boolean by onChange(false) { _, _, _ ->
        widthConstraint.isActive = isActive
        heightConstraint.isActive = isActive
        cornerConstraint.isActive = isActive
    }

    var width: Double by onChange(0.0) { _, _, _ ->
        widthConstraint.offset = width
    }

    var height: Double by onChange(0.0) { _, _, _ ->
        heightConstraint.offset = height
    }

    private val widthConstraint = Constraint(autoLayout,
            listOf(ConstraintItem(ConstraintVariable(autoLayout, ConstraintType.width), ConstraintOperator.equal)))
    private val heightConstraint = Constraint(autoLayout,
            listOf(ConstraintItem(ConstraintVariable(autoLayout, ConstraintType.height), ConstraintOperator.equal)))
    private val cornerConstraint = Constraint(autoLayout,
            listOf(
                    ConstraintItem(ConstraintVariable(autoLayout, ConstraintType.top), ConstraintOperator.equal),
                    ConstraintItem(ConstraintVariable(autoLayout, ConstraintType.left), ConstraintOperator.equal)
            ))

    init {
        widthConstraint.initialized = true
        heightConstraint.initialized = true
        cornerConstraint.initialized = true
    }
}