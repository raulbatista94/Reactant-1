package org.brightify.reactant.core.constraint.internal.manager

import android.view.View
import org.brightify.reactant.core.constraint.AutoLayout
import org.brightify.reactant.core.constraint.Constraint
import org.brightify.reactant.core.constraint.ConstraintVariable
import org.brightify.reactant.core.constraint.exception.ViewNotManagedByCommonAutoLayoutException
import org.brightify.reactant.core.constraint.internal.solver.Equation
import org.brightify.reactant.core.constraint.internal.solver.Solver
import org.brightify.reactant.core.constraint.internal.util.DefaultEquationsProvider
import org.brightify.reactant.core.constraint.internal.util.IntrinsicSize
import org.brightify.reactant.core.constraint.util.children

/**
 *  @author <a href="mailto:filip.dolnik.96@gmail.com">Filip Dolnik</a>
 */
internal class MainConstraintManager : ConstraintManager {

    private val solver = Solver()
    private val equations = HashMap<View, List<Equation>>()
    private val constraints = HashMap<View, HashSet<Constraint>>()
    private val valueForVariable = HashMap<ConstraintVariable, Number>()
    private val intrinsicSizes = HashMap<View, IntrinsicSize>()

    private val managedViews: Set<View>
        get() = equations.keys

    override val mainConstraintManager: MainConstraintManager
        get() = this

    override val allConstraints: List<Constraint>
        get() = constraints.flatMap { it.value }

    override fun addConstraint(constraint: Constraint) {
        if (constraints[constraint.view]?.contains(constraint) == true) {
            return
        }

        if (verifyViewsUsedByConstraint(constraint)) {
            solver.addConstraint(constraint)
            constraint.isManaged = true
        } else {
            throw ViewNotManagedByCommonAutoLayoutException(constraint.view,
                    constraint.constraintItems.mapNotNull { it.rightVariable?.view }.first { !managedViews.contains(it) })
        }

        if (constraints[constraint.view] == null) {
            constraints[constraint.view] = HashSet()
        }
        constraints[constraint.view]?.add(constraint)
    }

    override fun removeConstraint(constraint: Constraint) {
        if (constraints[constraint.view]?.remove(constraint) != true) {
            return
        }

        solver.removeConstraint(constraint)
        constraint.isManaged = false
    }

    override fun addManagedView(view: View) {
        if (managedViews.contains(view)) {
            return
        }

        val equations = DefaultEquationsProvider(view).equations
        this.equations[view] = equations
        equations.forEach { solver.addEquation(it) }
    }

    override fun removeManagedView(view: View) {
        if (!managedViews.contains(view)) {
            return
        }

        equations[view]?.forEach { solver.removeEquation(it) }
        equations.remove(view)

        constraints.flatMap { it.value }.filter { !verifyViewsUsedByConstraint(it) }.forEach { removeConstraint(it) }
        constraints.remove(view)

        valueForVariable.filter { it.key.view == view }.forEach { (variable, _) -> resetValueForVariable(variable) }

        intrinsicSizes.remove(view)
    }

    override fun removeViewConstraintsCreatedByUser(view: View) {
        constraints[view]
                ?.minus(intrinsicSizes[view]?.constraints ?: emptyList())
                ?.forEach { removeConstraint(it) }
    }

    override fun getValueForVariable(variable: ConstraintVariable): Double = solver.getValueForVariable(variable)

    override fun setValueForVariable(variable: ConstraintVariable, value: Number) {
        if (!managedViews.contains(variable.view)) {
            return
        }

        solver.setValueForVariable(variable, value)
        valueForVariable[variable] = value
    }

    override fun resetValueForVariable(variable: ConstraintVariable) {
        solver.resetValueForVariable(variable)
        valueForVariable.remove(variable)
    }

    override fun getViewIntrinsicSize(view: View): IntrinsicSize {
        var size = intrinsicSizes[view]
        if (size == null) {
            size = IntrinsicSize(view)
            intrinsicSizes[view] = size
        }
        return size
    }

    override fun addAllToManager(manager: ConstraintManager) {
        val mainManager = manager.mainConstraintManager
        equations.forEach { (view, equations) ->
            mainManager.equations[view] = equations
            equations.forEach { mainManager.solver.addEquation(it) }
        }
        constraints.flatMap { it.value }.forEach { mainManager.addConstraint(it) }
        valueForVariable.forEach { (variable, value) -> mainManager.setValueForVariable(variable, value) }
        mainManager.intrinsicSizes.putAll(intrinsicSizes)
    }

    override fun splitToMainManagerForAutoLayout(layout: AutoLayout): MainConstraintManager {
        val disconnectedViews = HashSet<View>()
        fun registerDisconnectedView(view: View) {
            disconnectedViews.add(view)
            if (view is AutoLayout) {
                view.children.forEach { registerDisconnectedView(it) }
            }
        }
        registerDisconnectedView(layout)

        val mainManager = MainConstraintManager()
        equations.filter { disconnectedViews.contains(it.key) }.forEach { (view, equations) ->
            mainManager.equations[view] = equations
            equations.forEach { mainManager.solver.addEquation(it) }
        }
        valueForVariable.filter { disconnectedViews.contains(it.key.view) }.forEach { (variable, value) ->
            mainManager.setValueForVariable(variable, value)
        }
        mainManager.intrinsicSizes.putAll(intrinsicSizes.filter { disconnectedViews.contains(it.key) })

        val constraintsOfDisconnectedViews = constraints.flatMap { it.value }.filter { verifyViewsUsedByConstraint(it, disconnectedViews) }
        disconnectedViews.forEach { removeManagedView(it) }
        constraintsOfDisconnectedViews.forEach { mainManager.addConstraint(it) }

        return mainManager
    }

    private fun verifyViewsUsedByConstraint(constraint: Constraint, views: Set<View> = managedViews): Boolean {
        return constraint.constraintItems.flatMap { it.equation.terms.map { it.variable.view } }.all { views.contains(it) }
    }
}
